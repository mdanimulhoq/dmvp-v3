/**
 * @file src/routes/devices.js
 * @description DMVP v3.0 — Device Lifecycle Routes
 *
 * Endpoints:
 *   GET  /devices         — List devices (info endpoint)
 *   POST /devices/register — Register new device
 *   POST /devices/:deviceKeyId/rotate — Rotate device key
 *   POST /devices/:deviceKeyId/revoke — Revoke device
 *   POST /devices/recover — Recover device identity
 *
 * @module routes/devices
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate, optionalAuthenticate } = require('../middleware/auth');
const { authRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');
const { normalizeSpkiPublicKey } = require('../utils/cryptoUtils');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';

function buildDeviceResponse(device, publicKey = device.publicKeyPem) {
  return {
    id: device.deviceId,
    device_key_id: device.keyId,
    public_key: publicKey,
    trust_tier: device.trustTier,
    lifecycle_state: device.revokedAt ? 'REVOKED' : 'ACTIVE',
    revoked_at: device.revokedAt,
    created_at: device.createdAt,
    updated_at: device.updatedAt,
  };
}

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

function normalizePlatform(value) {
  const platform = String(value || 'android').toUpperCase();
  return ['ANDROID', 'DESKTOP', 'SERVER', 'OTHER'].includes(platform)
    ? platform
    : 'OTHER';
}

function normalizeTrustTier(requestedTrustTier, hardwareBacked, attestationSummary) {
  const allowed = new Set(['TIER_A', 'TIER_B', 'TIER_C', 'TIER_D']);

  if (typeof requestedTrustTier === 'string') {
    const normalized = requestedTrustTier.trim().toUpperCase();
    if (allowed.has(normalized)) {
      return normalized;
    }
  }

  if (attestationSummary && attestationSummary.valid === false) {
    return 'TIER_D';
  }

  return hardwareBacked ? 'TIER_A' : 'TIER_C';
}

function resolveAttestationStatus(attestationSummary) {
  if (!attestationSummary) {
    return 'MISSING';
  }

  if (attestationSummary.valid === true) {
    return 'VALID';
  }

  if (attestationSummary.valid === false) {
    return 'INVALID';
  }

  return 'DEGRADED';
}

// ─────────────────────────────────────────────────────────────────────────────
// GET /devices
// List devices or device service info
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/',
  optionalAuthenticate,
  async (req, res, next) => {
    try {
      const devices = await prisma.device.findMany({
        take: 100,
        select: {
          deviceId: true,
          keyId: true,
          publicKeyReference: true,
          publicKeyPem: true,
          platform: true,
          trustTier: true,
          attestationStatus: true,
          hardwareBacked: true,
          revokedAt: true,
          firstSeenAt: true,
          lastSeenAt: true,
          createdAt: true,
        },
        orderBy: { createdAt: 'desc' },
      });

      return res.status(200).json({
        items: devices.map(d => buildDeviceResponse(d, d.publicKeyPem)),
        total: devices.length,
        page: Number(req.query.page || 1),
        limit: Number(req.query.limit || 100),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/register
// Register a new device identity with crypto verification
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/register',
  authRateLimit,
  async (req, res, next) => {
    try {
      const body = req.body || {};

      const deviceKeyId = body.device_key_id || body.deviceKeyId || body.keyId;
      const rawPublicKey = body.public_key || body.publicKey || body.publicKeyPem;
      const attestationSummary =
        body.attestation_summary || body.attestationSummary || null;
      const requestedTrustTier = body.trust_tier || body.trustTier;

      const hardwareBacked =
        body.hardware_backed !== undefined
          ? body.hardware_backed === true
          : attestationSummary?.hardware_backed === true;

      const platform = normalizePlatform(body.platform);
      const trustTier = normalizeTrustTier(
        requestedTrustTier,
        hardwareBacked,
        attestationSummary
      );

      if (!deviceKeyId || typeof deviceKeyId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'device_key_id is required', null, req)
        );
      }

      if (!rawPublicKey || typeof rawPublicKey !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'public_key is required', null, req)
        );
      }

      let publicKeyPem;
      try {
        publicKeyPem = normalizeSpkiPublicKey(rawPublicKey);
      } catch (error) {
        return res.status(400).json(
          buildError(
            400,
            'INVALID_PUBLIC_KEY',
            'public_key must be a valid ECDSA P-256 SPKI public key in base64 or PEM format',
            null,
            req
          )
        );
      }

      const existing = await prisma.device.findUnique({
        where: { keyId: deviceKeyId },
      });

      if (existing) {
        return res.status(200).json({
          ...buildDeviceResponse(existing, rawPublicKey),
          protocol_version: process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0',
          policy_version: POLICY_VERSION,
          algorithm_version: 'ecdsa-p256-sha256-spki-v1',
          request_id: req.requestId,
        });
      }

      const device = await prisma.device.create({
        data: {
          deviceId: body.deviceId || undefined,
          keyId: deviceKeyId,
          publicKeyReference: body.publicKeyReference || deviceKeyId,
          publicKeyPem,
          platform,
          signatureAlgorithm: body.signatureAlgorithm || 'SHA256withECDSA',
          trustTier,
          hardwareBacked,
          attestationStatus: resolveAttestationStatus(attestationSummary),
          attestationSummary: attestationSummary || null,
          appPackageName: body.appPackageName || null,
          appVersion: body.appVersion || null,
          deviceModel: body.deviceModel || null,
          osVersion: body.osVersion || null,
        },
      });

      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'DEVICE_REGISTERED',
          entityType: 'Device',
          entityId: device.deviceId,
          actorKeyId: req.user?.keyId || 'anonymous',
          ipAddress: req.ip,
          details: {
            keyId: deviceKeyId,
            trustTier: device.trustTier,
            platform: device.platform,
          },
        },
      });

      console.info(`[Device] Registered: ${deviceKeyId} tier=${device.trustTier}`);

      return res.status(201).json({
        ...buildDeviceResponse(device, rawPublicKey),
        protocol_version: process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0',
        policy_version: POLICY_VERSION,
        algorithm_version: 'ecdsa-p256-sha256-spki-v1',
        request_id: req.requestId,
      });
    } catch (error) {
      if (error.code === 'P2002') {
        return res.status(409).json(
          buildError(
            409,
            'DEVICE_EXISTS',
            'Device with this keyId or public key already exists',
            null,
            req
          )
        );
      }

      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /devices/:deviceKeyId
// Fetch a single device key by key id
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:deviceKeyId',
  optionalAuthenticate,
  async (req, res, next) => {
    try {
      const { deviceKeyId } = req.params;
      const device = await prisma.device.findUnique({
        where: { keyId: deviceKeyId },
      });

      if (!device) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Device not found', { deviceKeyId }, req)
        );
      }

      return res.status(200).json({
        ...buildDeviceResponse(device, device.publicKeyReference),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/:deviceKeyId/rotate
// Rotate device key
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/:deviceKeyId/rotate',
  optionalAuthenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const { deviceKeyId } = req.params;
      const body = req.body || {};
      const newDeviceKeyId = body.new_device_key_id || body.newDeviceKeyId || `${deviceKeyId}-rotated-${Date.now()}`;
      const rawNewPublicKey = body.new_public_key || body.newPublicKey || body.newPublicKeyPem;

      const device = await prisma.device.findUnique({
        where: { keyId: deviceKeyId },
      });

      if (!device) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Device not found', { deviceKeyId }, req)
        );
      }

      if (device.revokedAt) {
        return res.status(403).json(
          buildError(403, 'DEVICE_REVOKED', 'Device has been revoked', { deviceKeyId }, req)
        );
      }

      // Create new device with lineage
      const newDevice = await prisma.device.create({
        data: {
          keyId: newDeviceKeyId,
          publicKeyReference: newDeviceKeyId,
          publicKeyPem: rawNewPublicKey || device.publicKeyPem,
          platform: device.platform,
          trustTier: device.trustTier,
          hardwareBacked: device.hardwareBacked,
          attestationStatus: device.attestationStatus,
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'DEVICE_KEY_ROTATED',
          entityType: 'Device',
          entityId: newDevice.deviceId,
          actorKeyId: req.user?.keyId || 'anonymous',
          details: {
            oldKeyId: deviceKeyId,
            newKeyId: newDevice.keyId,
          },
        },
      });

      return res.status(200).json({
        ...buildDeviceResponse(newDevice, rawNewPublicKey || newDevice.publicKeyReference),
        lineage_parent_id: device.deviceId,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/:deviceKeyId/revoke
// Revoke device
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/:deviceKeyId/revoke',
  optionalAuthenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const { deviceKeyId } = req.params;
      const { revocationReason } = req.body;

      const device = await prisma.device.findUnique({
        where: { keyId: deviceKeyId },
      });

      if (!device) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Device not found', { deviceKeyId }, req)
        );
      }

      if (device.revokedAt) {
        return res.status(409).json(
          buildError(409, 'DEVICE_ALREADY_REVOKED', 'Device already revoked', { deviceKeyId }, req)
        );
      }

      const updated = await prisma.device.update({
        where: { keyId: deviceKeyId },
        data: {
          revokedAt: new Date(),
          revokeReason: revocationReason || 'User requested',
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'DEVICE_REVOKED',
          entityType: 'Device',
          entityId: device.deviceId,
          actorKeyId: req.user?.keyId || 'anonymous',
          details: {
            keyId: deviceKeyId,
            reason: revocationReason,
          },
        },
      });

      console.info(`[Device] Revoked: ${deviceKeyId}`);

      return res.status(200).json({
        ...buildDeviceResponse(updated, updated.publicKeyReference),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/recover
// Recover device identity
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/recover',
  optionalAuthenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const body = req.body || {};
      const oldDeviceKeyId = body.old_device_key_id || body.oldDeviceKeyId;
      const newDeviceKeyId = body.new_device_key_id || body.newDeviceKeyId || `${oldDeviceKeyId}-recovered-${Date.now()}`;
      const rawNewPublicKey = body.new_public_key || body.newPublicKey || body.newPublicKeyPem;
      const recoveryMethod = body.recovery_method || body.recoveryMethod || body.recovery_quorum || body.recoveryQuorum || 'mvp-local-recovery';

      if (!oldDeviceKeyId || !recoveryMethod) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'oldDeviceKeyId and recoveryMethod are required', null, req)
        );
      }

      const oldDevice = await prisma.device.findUnique({
        where: { keyId: oldDeviceKeyId },
      });

      if (!oldDevice) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Original device not found', { oldDeviceKeyId }, req)
        );
      }

      // Create recovery device
      const recoveryDevice = await prisma.device.create({
        data: {
          keyId: newDeviceKeyId,
          publicKeyReference: newDeviceKeyId,
          publicKeyPem: rawNewPublicKey || oldDevice.publicKeyPem,
          platform: oldDevice.platform,
          trustTier: 'TIER_C', // Recovery gets lower trust
          hardwareBacked: false,
          attestationStatus: 'MISSING',
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'DEVICE_RECOVERED',
          entityType: 'Device',
          entityId: recoveryDevice.deviceId,
          actorKeyId: req.user?.keyId || 'anonymous',
          details: {
            oldKeyId: oldDeviceKeyId,
            newKeyId: recoveryDevice.keyId,
            recoveryMethod,
          },
        },
      });

      return res.status(201).json({
        ...buildDeviceResponse(recoveryDevice, rawNewPublicKey || recoveryDevice.publicKeyReference),
        lineage_parent_id: oldDevice.deviceId,
        note: 'Recovery device has lower trust tier. Historical evidence remains linked to original device.',
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
