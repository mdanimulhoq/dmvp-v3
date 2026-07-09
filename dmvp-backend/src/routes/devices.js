/**
 * src/routes/devices.js
 *
 * Express routes for device lifecycle management.
 * Endpoints:
 *   POST /devices/register - Register a new device key
 *   POST /devices/:device_key_id/rotate - Rotate to a new key
 *   POST /devices/:device_key_id/revoke - Revoke a device key
 *   POST /devices/recover - Recover lineage after device loss
 *   GET /devices/:device_key_id - Get device key info
 *   GET /devices - List device keys (with filters)
 *
 * All routes enforce authentication, rate limiting, and input validation.
 */

const express = require('express');
const router = express.Router();

// Middleware
const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');

// Service
const {
  registerDevice,
  rotateDeviceKey,
  revokeDeviceKey,
  recoverDeviceLineage,
  getDeviceKey,
  listDeviceKeys,
} = require('../services/deviceService');

// Logger
const logger = console;

/**
 * POST /devices/register
 *
 * Register a new device key.
 *
 * Request body:
 *   {
 *     device_key_id: string - required,
 *     public_key: string - required (PEM or base64),
 *     attestation_summary: object - optional,
 *     platform: "android" | "ios" | "desktop" | "unknown" - optional
 *   }
 *
 * Response: 201 Created with device key record.
 *           409 Conflict if device_key_id already exists.
 *           400 Bad Request if validation fails.
 */
router.post(
  '/register',
  authenticate, // Require authentication (could be a user or system)
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }), // 20 per 15 min
  async (req, res, next) => {
    try {
      const { device_key_id, public_key, attestation_summary, platform } = req.body;

      // Validate required fields
      if (!device_key_id || typeof device_key_id !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'device_key_id is required and must be a string',
          detail: null,
        });
      }
      if (!public_key || typeof public_key !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'public_key is required and must be a string',
          detail: null,
        });
      }

      const params = {
        device_key_id,
        public_key,
        attestation_summary: attestation_summary || null,
        platform: platform || 'unknown',
        actorId: req.user ? req.user.id : null,
      };

      const result = await registerDevice(params);
      res.status(201).json(result);
    } catch (error) {
      if (error.message && error.message.includes('already registered')) {
        return res.status(409).json({
          error_code: 'DUPLICATE_DEVICE_KEY',
          message: error.message,
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * POST /devices/:device_key_id/rotate
 *
 * Rotate device key to a new key.
 *
 * Request body:
 *   {
 *     new_device_key_id: string - required,
 *     new_public_key: string - required,
 *     attestation_summary: object - optional,
 *     platform: string - optional
 *   }
 *
 * Response: 201 Created with new device key record.
 *           404 Not Found if old key not found.
 *           409 Conflict if new key already exists.
 */
router.post(
  '/:device_key_id/rotate',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { device_key_id } = req.params;
      const { new_device_key_id, new_public_key, attestation_summary, platform } = req.body;

      // Validate
      if (!new_device_key_id || typeof new_device_key_id !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'new_device_key_id is required and must be a string',
          detail: null,
        });
      }
      if (!new_public_key || typeof new_public_key !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'new_public_key is required and must be a string',
          detail: null,
        });
      }

      const params = {
        old_device_key_id: device_key_id,
        new_device_key_id,
        new_public_key,
        attestation_summary: attestation_summary || null,
        platform: platform || 'unknown',
        actorId: req.user ? req.user.id : null,
      };

      const result = await rotateDeviceKey(params);
      res.status(201).json(result);
    } catch (error) {
      if (error.message && error.message.includes('not found')) {
        return res.status(404).json({
          error_code: 'DEVICE_KEY_NOT_FOUND',
          message: error.message,
          detail: null,
        });
      }
      if (error.message && error.message.includes('already exists')) {
        return res.status(409).json({
          error_code: 'DUPLICATE_DEVICE_KEY',
          message: error.message,
          detail: null,
        });
      }
      if (error.message && error.message.includes('revoked')) {
        return res.status(400).json({
          error_code: 'INVALID_STATE',
          message: error.message,
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * POST /devices/:device_key_id/revoke
 *
 * Revoke a device key.
 *
 * Request body: none
 *
 * Response: 200 OK with updated device key record.
 *           404 Not Found if key not found.
 *           400 Bad Request if already revoked.
 */
router.post(
  '/:device_key_id/revoke',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { device_key_id } = req.params;
      const actorId = req.user ? req.user.id : null;

      const result = await revokeDeviceKey(device_key_id, actorId);
      res.json(result);
    } catch (error) {
      if (error.message && error.message.includes('not found')) {
        return res.status(404).json({
          error_code: 'DEVICE_KEY_NOT_FOUND',
          message: error.message,
          detail: null,
        });
      }
      if (error.message && error.message.includes('already revoked')) {
        return res.status(400).json({
          error_code: 'ALREADY_REVOKED',
          message: error.message,
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * POST /devices/recover
 *
 * Recover device lineage after device loss.
 *
 * Request body:
 *   {
 *     old_device_key_id: string - required,
 *     new_device_key_id: string - required,
 *     new_public_key: string - required,
 *     attestation_summary: object - optional,
 *     platform: string - optional,
 *     recovery_quorum: string - optional (extra proof)
 *   }
 *
 * Response: 201 Created with new device key record.
 *           404 Not Found if old key not found.
 *           409 Conflict if new key already exists.
 */
router.post(
  '/recover',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 10 }),
  async (req, res, next) => {
    try {
      const { old_device_key_id, new_device_key_id, new_public_key, attestation_summary, platform, recovery_quorum } = req.body;

      // Validate
      if (!old_device_key_id || typeof old_device_key_id !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'old_device_key_id is required and must be a string',
          detail: null,
        });
      }
      if (!new_device_key_id || typeof new_device_key_id !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'new_device_key_id is required and must be a string',
          detail: null,
        });
      }
      if (!new_public_key || typeof new_public_key !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'new_public_key is required and must be a string',
          detail: null,
        });
      }

      const params = {
        old_device_key_id,
        new_device_key_id,
        new_public_key,
        attestation_summary: attestation_summary || null,
        platform: platform || 'unknown',
        recovery_quorum: recovery_quorum || null,
        actorId: req.user ? req.user.id : null,
      };

      const result = await recoverDeviceLineage(params);
      res.status(201).json(result);
    } catch (error) {
      if (error.message && error.message.includes('not found')) {
        return res.status(404).json({
          error_code: 'DEVICE_KEY_NOT_FOUND',
          message: error.message,
          detail: null,
        });
      }
      if (error.message && error.message.includes('already exists')) {
        return res.status(409).json({
          error_code: 'DUPLICATE_DEVICE_KEY',
          message: error.message,
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * GET /devices/:device_key_id
 *
 * Get device key information.
 *
 * Response: 200 OK with device key record.
 *           404 Not Found if key not found.
 */
router.get(
  '/:device_key_id',
  authenticate,
  async (req, res, next) => {
    try {
      const { device_key_id } = req.params;
      const result = await getDeviceKey(device_key_id);
      if (!result) {
        return res.status(404).json({
          error_code: 'DEVICE_KEY_NOT_FOUND',
          message: 'Device key not found',
          detail: null,
        });
      }
      res.json(result);
    } catch (error) {
      next(error);
    }
  }
);

/**
 * GET /devices
 *
 * List device keys with optional filters (query params).
 *
 * Query parameters:
 *   trust_tier: string (e.g., TIER_A)
 *   lifecycle_state: string (e.g., ACTIVE, REVOKED)
 *   page: number (default 1)
 *   limit: number (default 20)
 *
 * Response: 200 OK with paginated list.
 */
router.get(
  '/',
  authenticate,
  async (req, res, next) => {
    try {
      const { trust_tier, lifecycle_state, page = 1, limit = 20 } = req.query;

      const filters = {};
      if (trust_tier) filters.trust_tier = trust_tier;
      if (lifecycle_state) filters.lifecycle_state = lifecycle_state;

      const result = await listDeviceKeys(
        filters,
        parseInt(page, 10),
        parseInt(limit, 10)
      );
      res.json(result);
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
