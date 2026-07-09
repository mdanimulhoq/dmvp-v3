/**
 * @file src/routes/evidence.js
 * @description DMVP v3.0 — Evidence Registration Routes
 *
 * Endpoints:
 *   POST /evidence              — Register new Canonical Evidence Envelope
 *   GET  /evidence/:evidenceId  — Fetch evidence record
 *   GET  /evidence/by-hash/:sha256 — Exact hash lookup
 *
 * @module routes/evidence
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const { registerRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');
const { computeSHA256Sync } = require('../utils/hashUtils');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';
const PROTOCOL_VERSION = process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function buildError(status, errorCode, message, detail = null, req = null) {
  return {
    status,
    errorCode,
    message,
    detail,
    policy_version: POLICY_VERSION,
    request_id: req?.requestId || 'unknown',
  };
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidUUID(str) {
  return typeof str === 'string' && UUID_REGEX.test(str);
}

function isValidSHA256(str) {
  return typeof str === 'string' && /^[0-9a-f]{64}$/i.test(str);
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /evidence
// Register a new Canonical Evidence Envelope (CEE)
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/',
  authenticate,
  registerRateLimit,
  async (req, res, next) => {
    try {
      const {
        evidenceId,
        mediaType,
        sha256Original,
        canonicalMediaHash,
        robustFingerprintProfile,
        fingerprintAlgorithmVersions,
        signerDeviceKeyId,
        signerPublicKeyReference,
        signatureAlgorithm,
        deviceAttestationSummary,
        trustedTimestampTokenReference,
        captureTimeClaim,
        geolocationLat,
        geolocationLng,
        privacyFlags,
        clientAppVersion,
        verificationPolicyVersion,
        chainParentEvidenceId,
        auditReference,
        signature,
        perceptualHash,
        fingerprintPrimary,
        fingerprintSearchTokens,
        durationMs,
        width,
        height,
        codec,
        frameRate,
        transformHints,
        idempotencyKey,
      } = req.body;

      // ── Validation ────────────────────────────────────────────────────────
      if (!evidenceId || !isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      if (!mediaType || !['IMAGE', 'VIDEO'].includes(mediaType.toUpperCase())) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'mediaType must be IMAGE or VIDEO', null, req)
        );
      }

      if (!sha256Original || !isValidSHA256(sha256Original)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'sha256Original must be a 64-char hex string', null, req)
        );
      }

      if (!signerDeviceKeyId || typeof signerDeviceKeyId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'signerDeviceKeyId is required', null, req)
        );
      }

      if (!signature || typeof signature !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'signature is required', null, req)
        );
      }

      // ── Idempotency check ─────────────────────────────────────────────────
      if (idempotencyKey) {
        const existing = await prisma.evidence.findFirst({
          where: { idempotencyKey },
          select: { evidenceId: true, createdAt: true },
        });
        if (existing) {
          return res.status(200).json({
            success: true,
            evidenceId: existing.evidenceId,
            idempotencyKey,
            createdAt: existing.createdAt,
            message: 'Evidence already registered (idempotent response)',
            policy_version: POLICY_VERSION,
            request_id: req.requestId,
          });
        }
      }

      // ── Check duplicate by device + hash ──────────────────────────────────
      const duplicate = await prisma.evidence.findFirst({
        where: {
          signerDeviceKeyId,
          sha256Original: sha256Original.toLowerCase(),
        },
        select: { evidenceId: true },
      });

      if (duplicate) {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_EVIDENCE', 'Evidence with this hash already registered from this device', {
            evidenceId: duplicate.evidenceId,
          }, req)
        );
      }

      // ── Verify device exists ──────────────────────────────────────────────
      const device = await prisma.device.findUnique({
        where: { keyId: signerDeviceKeyId },
        select: { deviceId: true, trustTier: true, revokedAt: true },
      });

      if (!device) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Signing device not found in registry', { signerDeviceKeyId }, req)
        );
      }

      if (device.revokedAt) {
        return res.status(403).json(
          buildError(403, 'DEVICE_REVOKED', 'Signing device has been revoked', { signerDeviceKeyId }, req)
        );
      }

      // ── Create evidence record ────────────────────────────────────────────
      const evidence = await prisma.evidence.create({
        data: {
          evidenceId,
          protocolVersion: PROTOCOL_VERSION,
          mediaType: mediaType.toUpperCase(),
          sha256Original: sha256Original.toLowerCase(),
          canonicalMediaHash: canonicalMediaHash ? canonicalMediaHash.toLowerCase() : null,
          robustFingerprintProfile: robustFingerprintProfile || {},
          fingerprintAlgorithmVersions: fingerprintAlgorithmVersions || {},
          signerDeviceKeyId,
          signerPublicKeyReference: signerPublicKeyReference || signerDeviceKeyId,
          signatureAlgorithm: signatureAlgorithm || 'SHA256withECDSA',
          deviceAttestationSummary: deviceAttestationSummary || null,
          trustedTimestampTokenReference: trustedTimestampTokenReference || null,
          captureTimeClaim: captureTimeClaim ? new Date(captureTimeClaim) : null,
          geolocationLat: geolocationLat ? parseFloat(geolocationLat) : null,
          geolocationLng: geolocationLng ? parseFloat(geolocationLng) : null,
          privacyFlags: privacyFlags || { gps: false, exif: false, device_info: false },
          clientAppVersion: clientAppVersion || 'unknown',
          verificationPolicyVersion: verificationPolicyVersion || POLICY_VERSION,
          chainParentEvidenceId: chainParentEvidenceId || null,
          auditReference: auditReference || computeSHA256Sync(`${evidenceId}-${Date.now()}`),
          signature,
          perceptualHash: perceptualHash || null,
          fingerprintPrimary: fingerprintPrimary || null,
          fingerprintSearchTokens: fingerprintSearchTokens || null,
          durationMs: durationMs ? parseInt(durationMs) : null,
          width: width ? parseInt(width) : null,
          height: height ? parseInt(height) : null,
          codec: codec || null,
          frameRate: frameRate ? parseFloat(frameRate) : null,
          transformHints: transformHints || null,
          status: 'ACTIVE',
          requestId: req.requestId,
          idempotencyKey: idempotencyKey || null,
        },
      });

      // ── Audit log ─────────────────────────────────────────────────────────
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'EVIDENCE_REGISTERED',
          entityType: 'Evidence',
          entityId: evidenceId,
          actorDeviceId: device.deviceId,
          actorKeyId: signerDeviceKeyId,
          ipAddress: req.ip,
          userAgent: req.headers['user-agent'] || null,
          details: {
            mediaType: evidence.mediaType,
            trustTier: device.trustTier,
          },
        },
      });

      console.info(`[Evidence] Registered: ${evidenceId} device=${signerDeviceKeyId}`);

      return res.status(201).json({
        success: true,
        evidenceId: evidence.evidenceId,
        mediaType: evidence.mediaType,
        sha256Original: evidence.sha256Original,
        status: evidence.status,
        createdAt: evidence.createdAt,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      if (error.code === 'P2002') {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_EVIDENCE', 'Evidence with this ID or audit reference already exists', null, req)
        );
      }
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /evidence/:evidenceId
// Fetch an evidence record by ID
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidenceId',
  authenticate,
  async (req, res, next) => {
    try {
      const { evidenceId } = req.params;

      if (!isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      const evidence = await prisma.evidence.findUnique({
        where: { evidenceId },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidenceId }, req)
        );
      }

      return res.status(200).json({
        ...evidence,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /evidence/by-hash/:sha256
// Exact hash lookup
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/by-hash/:sha256',
  authenticate,
  async (req, res, next) => {
    try {
      const { sha256 } = req.params;

      if (!isValidSHA256(sha256)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'sha256 must be a 64-character hex string', null, req)
        );
      }

      const evidence = await prisma.evidence.findFirst({
        where: {
          sha256Original: sha256.toLowerCase(),
        },
        orderBy: { createdAt: 'desc' },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'No evidence found with this hash', { sha256 }, req)
        );
      }

      return res.status(200).json({
        ...evidence,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
