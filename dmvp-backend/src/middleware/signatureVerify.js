/**
 * @file src/middleware/auth.js
 * @description User/account authentication and authorization middleware
 * for the DMVP v3.0 backend API.
 *
 * This module handles bearer-token (JWT) based user authorization, as
 * distinct from the per-request cryptographic signature verification
 * performed by `src/middleware/signatureVerify.js` for device-signed
 * write operations. Authentication here answers "which account is
 * making this call", while signature verification answers "did the
 * registered device actually sign this evidence payload".
 *
 * Exposes:
 *  - `authenticate`         - requires a valid bearer token, 401 otherwise
 *  - `optionalAuthenticate` - attaches req.user if a valid token is
 *                             present, but never rejects the request
 *  - `requireRole(...roles)` - role-based access control, 403 if the
 *                             authenticated account lacks an allowed role
 *
 * All failures use the DMVP structured error envelope:
 *
 *   {
 *     error_code: string,
 *     message: string,
 *     detail: object,
 *     policy_version: string,
 *     request_id: string
 *   }
 */

'use strict';

const jwt = require('jsonwebtoken');
const crypto = require('crypto');

/** Expected JWT issuer, configurable per deployment environment. */
const TOKEN_ISSUER = process.env.JWT_ISSUER || 'dmvp-registry';

/** Expected JWT audience, configurable per deployment environment. */
const TOKEN_AUDIENCE = process.env.JWT_AUDIENCE || 'dmvp-api';

/** Allowed signing algorithms. Restricted to prevent alg-confusion attacks. */
const ALLOWED_ALGORITHMS = ['HS256'];

/**
 * Roles recognized by the DMVP authorization model. Individual tokens
 * may carry a subset of these in their `roles` claim.
 *
 * @readonly
 * @enum {string}
 */
const ROLES = Object.freeze({
  USER: 'user',
  ENTERPRISE: 'enterprise',
  ADMIN: 'admin',
  AUDITOR: 'auditor',
});

/* ------------------------------------------------------------------ *
 * Shared helpers (mirrors conventions in rateLimit.js / validation.js)
 * ------------------------------------------------------------------ */

/**
 * Resolve the currently active verification policy version.
 *
 * @returns {string} policy version identifier
 */
function getPolicyVersion() {
  return process.env.VERIFICATION_POLICY_VERSION || 'unspecified';
}

/**
 * Generate or forward a request identifier for correlation in logs,
 * audit trails, and support requests.
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
 * Send a structured authentication/authorization error response.
 *
 * @param {import('express').Response} res
 * @param {import('express').Request} req
 * @param {number} statusCode - HTTP status code (401 or 403)
 * @param {string} errorCode - Machine-readable error code
 * @param {string} message - Human-readable message
 * @param {object} [detail] - Optional additional detail object
 */
function sendAuthError(res, req, statusCode, errorCode, message, detail = {}) {
  res.status(statusCode).json({
    error_code: errorCode,
    message,
    detail,
    policy_version: getPolicyVersion(),
    request_id: resolveRequestId(req),
  });
}

/**
 * Extract a bearer token from the request's `Authorization` header.
 *
 * @param {import('express').Request} req
 * @returns {string | null} the raw token, or null if not present/malformed
 */
function extractBearerToken(req) {
  const header = req.headers['authorization'];
  if (typeof header !== 'string') {
    return null;
  }

  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return null;
  }

  const token = match[1].trim();
  return token.length > 0 ? token : null;
}

/**
 * Verify a JWT and normalize its payload into the shape attached to
 * `req.user`.
 *
 * @param {string} token - Raw JWT string.
 * @returns {{ accountId: string, roles: string[], claims: object }}
 * @throws {jwt.JsonWebTokenError | jwt.TokenExpiredError | jwt.NotBeforeError}
 */
function verifyToken(token) {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error(
      'JWT_SECRET is not configured; cannot verify authentication tokens'
    );
  }

  const payload = jwt.verify(token, secret, {
    algorithms: ALLOWED_ALGORITHMS,
    issuer: TOKEN_ISSUER,
    audience: TOKEN_AUDIENCE,
  });

  const accountId = payload.sub;
  const roles = Array.isArray(payload.roles) ? payload.roles : [ROLES.USER];

  return { accountId, roles, claims: payload };
}

/**
 * Translate a JWT verification failure into the appropriate DMVP
 * structured error response.
 *
 * @param {import('express').Response} res
 * @param {import('express').Request} req
 * @param {Error} err
 */
function respondWithTokenError(res, req, err) {
  if (err instanceof jwt.TokenExpiredError) {
    sendAuthError(
      res,
      req,
      401,
      'AUTH_TOKEN_EXPIRED',
      'The provided authentication token has expired.',
      { expired_at: err.expiredAt }
    );
    return;
  }

  if (err instanceof jwt.NotBeforeError) {
    sendAuthError(
      res,
      req,
      401,
      'AUTH_TOKEN_NOT_ACTIVE',
      'The provided authentication token is not yet active.'
    );
    return;
  }

  if (err instanceof jwt.JsonWebTokenError) {
    sendAuthError(
      res,
      req,
      401,
      'AUTH_INVALID_TOKEN',
      'The provided authentication token is invalid.'
    );
    return;
  }

  // Configuration or unexpected errors should not leak internals to
  // the client, but must still fail closed.
  sendAuthError(
    res,
    req,
    401,
    'AUTH_VERIFICATION_FAILED',
    'Authentication could not be completed.'
  );
}

/* ------------------------------------------------------------------ *
 * Middleware
 * ------------------------------------------------------------------ */

/**
 * Require a valid bearer token on the request. On success, attaches
 * `req.user = { accountId, roles, claims }` and calls `next()`.
 * On failure, responds with 401 and a structured error envelope.
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
function authenticate(req, res, next) {
  const token = extractBearerToken(req);

  if (!token) {
    sendAuthError(
      res,
      req,
      401,
      'AUTH_MISSING_TOKEN',
      'This endpoint requires a valid bearer token in the Authorization header.'
    );
    return;
  }

  try {
    req.user = verifyToken(token);
    next();
  } catch (err) {
    respondWithTokenError(res, req, err);
  }
}

/**
 * Attach `req.user` when a valid bearer token is present, but never
 * rejects the request. Useful for endpoints (e.g. public evidence
 * lookup) whose response may vary by permission level without being
 * strictly gated behind authentication.
 *
 * A malformed or expired token is treated the same as no token: the
 * request proceeds with `req.user` left undefined.
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
function optionalAuthenticate(req, res, next) {
  const token = extractBearerToken(req);

  if (!token) {
    next();
    return;
  }

  try {
    req.user = verifyToken(token);
  } catch (err) {
    // Silently ignore verification failures for optional auth; the
    // request is treated as unauthenticated rather than rejected.
    req.user = undefined;
  }

  next();
}

/**
 * Build a role-based access control middleware. Must run after
 * `authenticate` (or `optionalAuthenticate` followed by a manual check)
 * so that `req.user` is populated.
 *
 * @param {...string} allowedRoles - One or more roles from `ROLES` that
 *   are permitted to access the guarded route. The authenticated
 *   account must carry at least one matching role.
 * @returns {(req: import('express').Request, res: import('express').Response, next: import('express').NextFunction) => void}
 */
function requireRole(...allowedRoles) {
  if (allowedRoles.length === 0) {
    throw new Error('requireRole() must be called with at least one role');
  }

  return function roleCheckMiddleware(req, res, next) {
    if (!req.user) {
      sendAuthError(
        res,
        req,
        401,
        'AUTH_MISSING_TOKEN',
        'This endpoint requires authentication before role checks can be performed.'
      );
      return;
    }

    const accountRoles = Array.isArray(req.user.roles) ? req.user.roles : [];
    const isAuthorized = accountRoles.some((role) =>
      allowedRoles.includes(role)
    );

    if (!isAuthorized) {
      sendAuthError(
        res,
        req,
        403,
        'AUTH_INSUFFICIENT_ROLE',
        'This account does not have permission to access this resource.',
        { required_roles: allowedRoles, account_roles: accountRoles }
      );
      return;
    }

    next();
  };
}

module.exports = {
  ROLES,
  authenticate,
  optionalAuthenticate,
  requireRole,
};
