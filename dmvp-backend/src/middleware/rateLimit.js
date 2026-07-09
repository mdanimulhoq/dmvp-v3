/**
 * @file src/middleware/rateLimit.js
 * @description Rate limiting middleware for the DMVP v3.0 backend API.
 *
 * Provides multiple named rate limiters tuned to the sensitivity and cost
 * of different endpoint groups. All limiters return a structured error
 * envelope consistent with the rest of the DMVP API:
 *
 *   {
 *     error_code: "RATE_LIMIT_EXCEEDED",
 *     message: string,
 *     detail: { retry_after_seconds: number },
 *     policy_version: string,
 *     request_id: string
 *   }
 *
 * All limiters:
 *  - use `standardHeaders: true` (RateLimit-* headers) and `legacyHeaders: false`
 *  - key by `req.ip` (Express must have `trust proxy` configured appropriately
 *    upstream in app.js when running behind a reverse proxy / load balancer)
 *  - skip counting successful CORS preflight (OPTIONS) requests
 *  - read the active verification policy version from
 *    `process.env.VERIFICATION_POLICY_VERSION`
 */

'use strict';

const rateLimit = require('express-rate-limit');
const crypto = require('crypto');

/** Fifteen minutes, expressed in milliseconds, shared by all limiters below. */
const FIFTEEN_MINUTES_MS = 15 * 60 * 1000;

/**
 * Resolve the currently active verification policy version.
 * Falls back to a safe default if the environment variable is unset,
 * so rate-limit error responses never emit `undefined`.
 *
 * @returns {string} policy version identifier
 */
function getPolicyVersion() {
  return process.env.VERIFICATION_POLICY_VERSION || 'unspecified';
}

/**
 * Generate a request identifier for correlation in logs, audit trails,
 * and support requests. Uses the incoming `x-request-id` header when
 * present (e.g. set by an upstream gateway), otherwise mints a new UUID.
 *
 * @param {import('express').Request} req
 * @returns {string} request identifier
 */
function resolveRequestId(req) {
  const incoming = req.headers['x-request-id'];
  if (typeof incoming === 'string' && incoming.trim().length > 0) {
    return incoming.trim();
  }
  return crypto.randomUUID();
}

/**
 * Key generator shared by all limiters. Uses Express's `req.ip`, which
 * respects the `trust proxy` setting configured on the app instance,
 * so this correctly resolves the client IP when DMVP is deployed behind
 * a reverse proxy or load balancer.
 *
 * @param {import('express').Request} req
 * @returns {string} rate-limit bucket key
 */
function keyGenerator(req) {
  return req.ip;
}

/**
 * Build a `handler` function for express-rate-limit that emits the DMVP
 * structured error envelope on 429 responses.
 *
 * @param {string} message - Human-readable message describing which
 *   endpoint group was rate limited.
 * @param {number} windowMs - The limiter's window size in milliseconds,
 *   used to compute `retry_after_seconds`.
 * @returns {(req: import('express').Request, res: import('express').Response) => void}
 */
function buildHandler(message, windowMs) {
  return function rateLimitHandler(req, res) {
    const requestId = resolveRequestId(req);
    const retryAfterSeconds = Math.ceil(windowMs / 1000);

    res.setHeader('Retry-After', String(retryAfterSeconds));

    res.status(429).json({
      error_code: 'RATE_LIMIT_EXCEEDED',
      message,
      detail: {
        retry_after_seconds: retryAfterSeconds,
      },
      policy_version: getPolicyVersion(),
      request_id: requestId,
    });
  };
}

/**
 * Skip predicate shared by all limiters. Successful CORS preflight
 * (OPTIONS) requests are never counted against a client's quota, since
 * they carry no application-level intent and browsers issue them
 * automatically ahead of the real request.
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @returns {boolean}
 */
function skipSuccessfulOptions(req, res) {
  return req.method === 'OPTIONS' && res.statusCode < 400;
}

/**
 * General-purpose rate limiter for standard API traffic.
 * Limit: 100 requests per 15 minutes per IP.
 *
 * @type {import('express-rate-limit').RateLimitRequestHandler}
 */
const generalRateLimit = rateLimit({
  windowMs: FIFTEEN_MINUTES_MS,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  skip: skipSuccessfulOptions,
  handler: buildHandler(
    'Too many requests to the DMVP API. Please slow down and try again later.',
    FIFTEEN_MINUTES_MS
  ),
});

/**
 * Rate limiter for authentication and device lifecycle endpoints
 * (registration, rotation, revocation, recovery).
 * Limit: 20 requests per 15 minutes per IP.
 *
 * @type {import('express-rate-limit').RateLimitRequestHandler}
 */
const authRateLimit = rateLimit({
  windowMs: FIFTEEN_MINUTES_MS,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  skip: skipSuccessfulOptions,
  handler: buildHandler(
    'Too many device authentication requests. Please slow down and try again later.',
    FIFTEEN_MINUTES_MS
  ),
});

/**
 * Rate limiter for verification endpoints (/verify and related routes).
 * Limit: 50 requests per 15 minutes per IP.
 *
 * @type {import('express-rate-limit').RateLimitRequestHandler}
 */
const verifyRateLimit = rateLimit({
  windowMs: FIFTEEN_MINUTES_MS,
  max: 50,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  skip: skipSuccessfulOptions,
  handler: buildHandler(
    'Too many verification requests. Please slow down and try again later.',
    FIFTEEN_MINUTES_MS
  ),
});

/**
 * Rate limiter for evidence registration endpoints (/evidence POST).
 * Limit: 30 requests per 15 minutes per IP.
 *
 * @type {import('express-rate-limit').RateLimitRequestHandler}
 */
const registerRateLimit = rateLimit({
  windowMs: FIFTEEN_MINUTES_MS,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator,
  skip: skipSuccessfulOptions,
  handler: buildHandler(
    'Too many evidence registration requests. Please slow down and try again later.',
    FIFTEEN_MINUTES_MS
  ),
});

// ═════════════════════════════════════════════════════════════════════════════
// BACKWARD COMPATIBILITY: rateLimiter() factory for route files
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Backward-compatible rateLimiter() factory for route files that call it
 * as a function with custom options. Creates a custom rate-limit middleware
 * on the fly.
 *
 * Usage in route files:
 *   const { rateLimiter } = require('../middleware/rateLimit');
 *   router.post('/', rateLimiter({ windowMs: 900000, max: 30 }), handler);
 *
 * @param {Object} [options={}] - Configuration options.
 * @param {number} [options.windowMs=900000] - Time window in milliseconds.
 * @param {number} [options.max=100] - Max requests per window.
 * @returns {import('express-rate-limit').RateLimitRequestHandler}
 */
function rateLimiter(options = {}) {
  const windowMs = options.windowMs || FIFTEEN_MINUTES_MS;
  const maxRequests = options.max || 100;

  return rateLimit({
    windowMs,
    max: maxRequests,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator,
    skip: skipSuccessfulOptions,
    handler: buildHandler(
      'Too many requests. Please slow down and try again later.',
      windowMs
    ),
  });
}

module.exports = {
  generalRateLimit,
  authRateLimit,
  verifyRateLimit,
  registerRateLimit,
  rateLimiter,        // ← ADDED: backward compatibility for route files
};
