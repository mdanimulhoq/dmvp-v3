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

const { authenticate } = require('../middleware/auth');
const { authRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';

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

// ─────────────────────────────────────────────────────────────────────────────
// GET /devices
// List devices or device service info
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/',
  authenticate,
  async (req, res, next) => {
    try {
      const devices = await prisma.device.findMany({
        take: 100,
        select: {
          deviceId: true,
          keyId: true,
          publicKeyReference: true,
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
        count: devices.length,
        devices: devices.map(d => ({
          ...d,
          status: d.revokedAt ? 'REVOKED' : 'ACTIVE',
        })),
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
// Register a new device identity
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/register',
  authenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const {
        deviceId,
        keyId,
        publicKeyReference,
        publicKeyPem,
        platform,
        signatureAlgorithm,
        trustTier,
        hardwareBacked,
        attestationStatus,
        attestationSummary,
        appPackageName,
        appVersion,
        deviceModel,
        osVersion,
      } = req.body;

      // Validation
      if (!keyId || typeof keyId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'keyId is required', null, req)
        );
      }

      if (!publicKeyPem || typeof publicKeyPem !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'publicKeyPem is required', null, req)
        );
      }

      // Check duplicate
      const existing = await prisma.device.findUnique({
        where: { keyId },
      });

      if (existing) {
        return res.status(409).json(
          buildError(409, 'DEVICE_EXISTS', 'Device with this keyId already registered', { keyId }, req)
        );
      }

      const device = await prisma.device.create({
        data: {
          deviceId: deviceId || undefined,
          keyId,
          publicKeyReference: publicKeyReference || keyId,
          publicKeyPem,
          platform: platform || 'ANDROID',
          signatureAlgorithm: signatureAlgorithm || 'SHA256withECDSA',
          trustTier: trustTier || 'TIER_C',
          hardwareBacked: hardwareBacked !== undefined ? hardwareBacked : false,
          attestationStatus: attestationStatus || 'MISSING',
          attestationSummary: attestationSummary || null,
          appPackageName: appPackageName || null,
          appVersion: appVersion || null,
          deviceModel: deviceModel || null,
          osVersion: osVersion || null,
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'DEVICE_REGISTERED',
          entityType: 'Device',
          entityId: device.deviceId,
          actorKeyId: req.user?.keyId || 'anonymous',
          ipAddress: req.ip,
          details: {
            keyId,
            trustTier: device.trustTier,
            platform: device.platform,
          },
        },
      });

      console.info(`[Device] Registered: ${keyId} tier=${device.trustTier}`);

      return res.status(201).json({
        success: true,
        deviceId: device.deviceId,
        keyId: device.keyId,
        trustTier: device.trustTier,
        platform: device.platform,
        createdAt: device.createdAt,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      if (error.code === 'P2002') {
        return res.status(409).json(
          buildError(409, 'DEVICE_EXISTS', 'Device with this keyId or public key already exists', null, req)
        );
      }
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
  authenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const { deviceKeyId } = req.params;
      const { newPublicKeyPem, rotationSignature } = req.body;

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
          keyId: `${deviceKeyId}-rotated-${Date.now()}`,
          publicKeyReference: device.publicKeyReference,
          publicKeyPem: newPublicKeyPem || device.publicKeyPem,
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
        success: true,
        oldKeyId: deviceKeyId,
        newKeyId: newDevice.keyId,
        newDeviceId: newDevice.deviceId,
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
  authenticate,
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
        success: true,
        deviceId: updated.deviceId,
        keyId: deviceKeyId,
        revokedAt: updated.revokedAt,
        reason: updated.revokeReason,
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
  authenticate,
  authRateLimit,
  async (req, res, next) => {
    try {
      const { oldDeviceKeyId, recoveryMethod, newPublicKeyPem } = req.body;

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
          keyId: `${oldDeviceKeyId}-recovered-${Date.now()}`,
          publicKeyReference: oldDevice.publicKeyReference,
          publicKeyPem: newPublicKeyPem || oldDevice.publicKeyPem,
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
        success: true,
        oldKeyId: oldDeviceKeyId,
        newKeyId: recoveryDevice.keyId,
        newDeviceId: recoveryDevice.deviceId,
        trustTier: recoveryDevice.trustTier,
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
