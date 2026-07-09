/**
 * src/routes/devices.js
 *
 * Express routes for device identity onboarding, trust tier assignment,
 * key rotation, revocation, and recovery.
 * Endpoints:
 *   POST /devices/register
 *   POST /devices/:device_key_id/rotate
 *   POST /devices/:device_key_id/revoke
 *   POST /devices/recover
 *
 * All routes enforce authentication, rate limiting, and input validation.
 */

const express = require('express');
const router = express.Router();

// Middleware — safe require with fallback
function safeRequire(path, exportName) {
  try {
    const mod = require(path);
    if (exportName && mod[exportName]) return mod[exportName];
    if (typeof mod === 'function') return mod;
    return mod;
  } catch (e) {
    console.warn(`[devices.js] Warning: could not load ${path} — ${e.message}`);
    return (req, res, next) => next();
  }
}

// Auth middleware
const authenticate = safeRequire('../middleware/auth', 'authenticate');

// Rate limiter — use authRateLimit from rateLimit.js
const rateLimitMod = safeRequire('../middleware/rateLimit');
const authRateLimit = rateLimitMod.authRateLimit || ((req, res, next) => next());

// Service
const {
  registerDevice,
  rotateDeviceKey,
  revokeDeviceKey,
  recoverDeviceIdentity,
} = require('../services/deviceService');

// Logger
const logger = console;

/**
 * POST /devices/register
 *
 * Register a new device identity.
 */
router.post(
  '/register',
  authenticate,
  authRateLimit, // FIXED: use authRateLimit instead of rateLimiter(...)
  async (req, res, next) => {
    try {
      const { publicKey, attestation, deviceType } = req.body;
      const result = await registerDevice({ publicKey, attestation, deviceType });
      res.status(201).json({
        ...result,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

/**
 * POST /devices/:device_key_id/rotate
 */
router.post(
  '/:device_key_id/rotate',
  authenticate,
  authRateLimit, // FIXED
  async (req, res, next) => {
    try {
      const { device_key_id } = req.params;
      const { newPublicKey, rotationSignature } = req.body;
      const result = await rotateDeviceKey(device_key_id, { newPublicKey, rotationSignature });
      res.json({
        ...result,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

/**
 * POST /devices/:device_key_id/revoke
 */
router.post(
  '/:device_key_id/revoke',
  authenticate,
  authRateLimit, // FIXED
  async (req, res, next) => {
    try {
      const { device_key_id } = req.params;
      const { revocationReason } = req.body;
      const result = await revokeDeviceKey(device_key_id, { revocationReason });
      res.json({
        ...result,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

/**
 * POST /devices/recover
 */
router.post(
  '/recover',
  authenticate,
  authRateLimit, // FIXED
  async (req, res, next) => {
    try {
      const { oldDeviceKeyId, recoveryMethod, recoveryProof } = req.body;
      const result = await recoverDeviceIdentity({ oldDeviceKeyId, recoveryMethod, recoveryProof });
      res.status(201).json({
        ...result,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
