'use strict';
const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/auth');
const { authRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');

const PROTOCOL_VERSION = process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0';

function buildError(status, code, message, detail, req) {
  return {
    status,
    error_code: code,
    message,
    detail,
    policy_version: process.env.VERIFICATION_POLICY_VERSION || 'unspecified',
    request_id: req?.requestId || 'unknown'
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/register
// ─────────────────────────────────────────────────────────────────────────────

router.post('/register', authRateLimit, async (req, res, next) => {
  try {
    const {
      device_id,
      key_id,
      public_key,
      public_key_pem,
      platform,
      attestation_summary,
      signature_algorithm,
      app_package_name,
      app_version,
      device_model,
      os_version,
      hardware_backed
    } = req.body;

    if (!key_id || !public_key) {
      return res.status(400).json(buildError(400, 'VALIDATION_ERROR', 'key_id and public_key required', null, req));
    }

    const device = await prisma.device.upsert({
      where: { keyId: key_id },
      // ── Fix: Update publicKey and other fields on upsert ──
      update: {
        publicKeyReference: public_key,
        publicKeyPem: public_key_pem || public_key,
        platform: (platform || 'ANDROID').toUpperCase(),
        hardwareBacked: hardware_backed || false,
        attestationSummary: attestation_summary || null,
        lastSeenAt: new Date(),
        appVersion: app_version || undefined,
        osVersion: os_version || undefined
      },
      create: {
        deviceId: device_id || undefined,
        keyId: key_id,
        publicKeyReference: public_key,
        publicKeyPem: public_key_pem || public_key,
        platform: (platform || 'ANDROID').toUpperCase(),
        signatureAlgorithm: signature_algorithm || 'SHA256withECDSA',
        hardwareBacked: hardware_backed || false,
        attestationStatus: attestation_summary?.valid ? 'VALID' : 'MISSING',
        attestationSummary: attestation_summary || null,
        appPackageName: app_package_name || null,
        appVersion: app_version || null,
        deviceModel: device_model || null,
        osVersion: os_version || null,
        lastSeenAt: new Date(),
      },
    });

    console.info(`[Devices] Registered: ${key_id}`);
    res.status(201).json({
      device_id: device.deviceId,
      key_id: device.keyId,
      trust_tier: device.trustTier,
      registered: true
    });
  } catch (e) {
    next(e);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/rotate/:device_key_id
// ─────────────────────────────────────────────────────────────────────────────

router.post('/rotate/:device_key_id', authenticate, authRateLimit, async (req, res, next) => {
  try {
    const { device_key_id } = req.params;
    const {
      new_device_key_id,
      new_public_key,
      attestation_summary,
      platform
    } = req.body;

    if (!new_device_key_id || !new_public_key) {
      return res.status(400).json(buildError(400, 'VALIDATION_ERROR', 'new_device_key_id and new_public_key required', null, req));
    }

    const oldDevice = await prisma.device.findUnique({
      where: { keyId: device_key_id }
    });

    if (!oldDevice) {
      return res.status(404).json(buildError(404, 'DEVICE_NOT_FOUND', 'Old device key not found', { device_key_id }, req));
    }

    if (oldDevice.revokedAt) {
      return res.status(403).json(buildError(403, 'DEVICE_REVOKED', 'Cannot rotate a revoked key', { device_key_id }, req));
    }

    // ── Step 6.2: Create new device ──
    const newDevice = await prisma.device.create({
      data: {
        deviceId: oldDevice.deviceId,
        keyId: new_device_key_id,
        publicKeyReference: new_public_key,
        publicKeyPem: new_public_key,
        platform: oldDevice.platform,
        signatureAlgorithm: oldDevice.signatureAlgorithm,
        trustTier: oldDevice.trustTier,
        hardwareBacked: oldDevice.hardwareBacked,
        attestationStatus: attestation_summary?.valid ? 'VALID' : oldDevice.attestationStatus,
        attestationSummary: attestation_summary || oldDevice.attestationSummary,
        appPackageName: oldDevice.appPackageName,
        appVersion: oldDevice.appVersion,
        deviceModel: oldDevice.deviceModel,
        osVersion: oldDevice.osVersion,
        lastSeenAt: new Date(),
      },
    });

    // ── Step 6.2: Create lineage transition ──
    await prisma.lineageTransition.create({
      data: {
        oldDeviceKeyId: device_key_id,
        newDeviceKeyId: new_device_key_id,
        transitionType: 'ROTATION',
        requestId: req.requestId,
      },
    });

    // Revoke old device
    await prisma.device.update({
      where: { keyId: device_key_id },
      data: {
        revokedAt: new Date(),
        revokeReason: 'KEY_ROTATION'
      },
    });

    console.info(`[Devices] Rotated: ${device_key_id} -> ${new_device_key_id}`);

    res.status(200).json({
      old_key_id: device_key_id,
      new_key_id: newDevice.keyId,
      trust_tier: newDevice.trustTier,
      rotated: true,
      lineage: {
        transition_type: 'ROTATION',
        timestamp: new Date().toISOString(),
      },
    });
  } catch (e) {
    next(e);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/revoke/:device_key_id
// ─────────────────────────────────────────────────────────────────────────────

router.post('/revoke/:device_key_id', authenticate, authRateLimit, async (req, res, next) => {
  try {
    const { device_key_id } = req.params;

    const device = await prisma.device.findUnique({
      where: { keyId: device_key_id }
    });

    if (!device) {
      return res.status(404).json(buildError(404, 'DEVICE_NOT_FOUND', 'Device key not found', { device_key_id }, req));
    }

    if (device.revokedAt) {
      return res.status(409).json(buildError(409, 'ALREADY_REVOKED', 'Key already revoked', { device_key_id }, req));
    }

    await prisma.device.update({
      where: { keyId: device_key_id },
      data: {
        revokedAt: new Date(),
        revokeReason: req.body.reason || 'USER_REQUEST'
      },
    });

    console.info(`[Devices] Revoked: ${device_key_id}`);

    res.status(200).json({
      key_id: device_key_id,
      revoked: true,
      revoked_at: new Date().toISOString(),
    });
  } catch (e) {
    next(e);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// POST /devices/recover
// ─────────────────────────────────────────────────────────────────────────────

router.post('/recover', authenticate, authRateLimit, async (req, res, next) => {
  try {
    const {
      old_device_key_id,
      new_device_key_id,
      new_public_key,
      recovery_proof
    } = req.body;

    if (!old_device_key_id || !new_device_key_id || !new_public_key) {
      return res.status(400).json(buildError(400, 'VALIDATION_ERROR', 'old_device_key_id, new_device_key_id, new_public_key required', null, req));
    }

    const oldDevice = await prisma.device.findUnique({
      where: { keyId: old_device_key_id }
    });

    if (!oldDevice) {
      return res.status(404).json(buildError(404, 'DEVICE_NOT_FOUND', 'Old device key not found', null, req));
    }

    // ── Step 6.2: Create new device with lower trust ──
    const newDevice = await prisma.device.create({
      data: {
        deviceId: oldDevice.deviceId,
        keyId: new_device_key_id,
        publicKeyReference: new_public_key,
        publicKeyPem: new_public_key,
        platform: oldDevice.platform,
        trustTier: 'TIER_C', // Recovery gets lower trust
        lastSeenAt: new Date(),
      },
    });

    // ── Step 6.2: Create lineage transition ──
    await prisma.lineageTransition.create({
      data: {
        oldDeviceKeyId: old_device_key_id,
        newDeviceKeyId: new_device_key_id,
        transitionType: 'RECOVERY',
        signatureProof: recovery_proof || null,
        requestId: req.requestId,
      },
    });

    console.info(`[Devices] Recovered: ${old_device_key_id} -> ${new_device_key_id}`);

    res.status(200).json({
      old_key_id: old_device_key_id,
      new_key_id: newDevice.keyId,
      trust_tier: newDevice.trustTier,
      recovered: true,
    });
  } catch (e) {
    next(e);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// GET /devices/:device_key_id
// ─────────────────────────────────────────────────────────────────────────────

router.get('/:device_key_id', authenticate, async (req, res, next) => {
  try {
    const device = await prisma.device.findUnique({
      where: { keyId: req.params.device_key_id },
      select: {
        deviceId: true,
        keyId: true,
        trustTier: true,
        attestationStatus: true,
        revokedAt: true,
        createdAt: true,
        lastSeenAt: true,
      },
    });

    if (!device) {
      return res.status(404).json(buildError(404, 'DEVICE_NOT_FOUND', 'Not found', null, req));
    }

    res.status(200).json(device);
  } catch (e) {
    next(e);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// GET /devices
// ─────────────────────────────────────────────────────────────────────────────

// ── Fix: Removed authenticate for public access ──
router.get('/', async (req, res, next) => {
  try {
    const { trust_tier, lifecycle_state, page = 1, limit = 20 } = req.query;
    const where = {};
    if (trust_tier) where.trustTier = trust_tier.toUpperCase();
    
    const skip = (parseInt(page) - 1) * parseInt(limit);
    const [items, total] = await Promise.all([
      prisma.device.findMany({
        where,
        skip,
        take: parseInt(limit),
        orderBy: { createdAt: 'desc' },
      }),
      prisma.device.count({ where }),
    ]);
    
    res.json({ items, total, page: parseInt(page), limit: parseInt(limit) });
  } catch (e) {
    next(e);
  }
});

module.exports = router;
