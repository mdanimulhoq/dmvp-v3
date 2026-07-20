require('dotenv').config();

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');

const {
  connectDatabase,
  disconnectDatabase,
  checkDatabaseHealth,
  cleanupExpiredSecurityArtifacts,
} = require('./config/database');

const {
  generalRateLimit,
  authRateLimit,
  verifyRateLimit,
  registerRateLimit,
} = require('./middleware/rateLimit');

// ── Safe route loading with error handling ──────────────────────────────────
function safeLoadRoute(name, path) {
  try {
    const route = require(path);
    if (!route || typeof route !== 'function') {
      console.error(`[FATAL] Route "${name}" at ${path} does not export a valid router`);
      return null;
    }
    console.info(`[Routes] Loaded: ${name} from ${path}`);
    return route;
  } catch (error) {
    console.error(`[FATAL] Failed to load route "${name}" from ${path}: ${error.message}`);
    return null;
  }
}

const evidenceRoutes = safeLoadRoute('evidence', './routes/evidence');
const verifyRoutes = safeLoadRoute('verify', './routes/verify');
const searchRoutes = safeLoadRoute('search', './routes/search');
const devicesRoutes = safeLoadRoute('devices', './routes/devices');
const ownershipRoutes = safeLoadRoute('ownership', './routes/ownership');
const assetsRoutes = safeLoadRoute('assets', './routes/assets');
const searchV5Routes = safeLoadRoute('searchV5', './routes/searchV5');
const claimsV4Routes = safeLoadRoute('claimsV4', './routes/claimsV4');
const authRoutes = safeLoadRoute('auth', './routes/auth');

const app = express();

const PORT = Number(process.env.PORT || 3000);
const NODE_ENV = process.env.NODE_ENV || 'development';
const API_BASE_PATH = process.env.API_BASE_PATH || '/api/v1';
const APP_NAME = process.env.APP_NAME || 'dmvp-backend';
const APP_VERSION = process.env.APP_VERSION || '3.0.0';
const POLICY_VERSION =
  process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';
const MAX_JSON_BODY_MB = Number(process.env.MAX_JSON_BODY_MB || 2);
const ENABLE_REQUEST_LOGGING = process.env.ENABLE_REQUEST_LOGGING === 'true';
const ENABLE_STACK_TRACES = process.env.ENABLE_STACK_TRACES === 'true';
const TRUST_PROXY = process.env.TRUST_PROXY === 'true';

if (TRUST_PROXY) {
  app.set('trust proxy', 1);
}

function parseCorsOrigins(value) {
  if (!value || value.trim() === '' || value.trim() === '*') {
    return '*';
  }

  return value
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
}

const corsOrigins = parseCorsOrigins(process.env.CORS_ORIGINS);

app.disable('x-powered-by');

app.use(
  helmet({
    crossOriginResourcePolicy: { policy: 'cross-origin' },
    contentSecurityPolicy: false,
    hsts:
      NODE_ENV === 'production'
        ? {
            maxAge: 31536000,
            includeSubDomains: true,
            preload: true,
          }
        : false,
  })
);

app.use(
  cors({
    origin(origin, callback) {
      if (corsOrigins === '*') {
        return callback(null, true);
      }

      if (!origin) {
        return callback(null, true);
      }

      if (corsOrigins.includes(origin)) {
        return callback(null, true);
      }

      return callback(
        createAppError({
          status: 403,
          errorCode: 'CORS_ORIGIN_DENIED',
          message: 'Origin not allowed by CORS policy',
          detail: { origin },
        })
      );
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: [
      'Content-Type',
      'Authorization',
      'X-Request-Id',
      'X-Idempotency-Key',
      'X-DMVP-Nonce',
      'X-DMVP-Timestamp',
      'X-DMVP-Signature',
      'X-DMVP-Key-Id',
    ],
    exposedHeaders: ['X-Request-Id'],
  })
);

if (ENABLE_REQUEST_LOGGING) {
  morgan.token('request-id', (req) => req.requestId || '-');

  app.use(
    morgan(
      ':method :url :status :response-time ms - :res[content-length] bytes req_id=:request-id'
    )
  );
}

app.use((req, res, next) => {
  req.requestId =
    req.headers['x-request-id']?.toString().trim() || crypto.randomUUID();

  res.setHeader('X-Request-Id', req.requestId);
  next();
});

app.use((req, res, next) => {
  req.startedAt = Date.now();
  next();
});

app.use(express.json({ limit: `${MAX_JSON_BODY_MB}mb` }));
app.use(express.urlencoded({ extended: true, limit: `${MAX_JSON_BODY_MB}mb` }));

app.use(generalRateLimit);

// ── Backward compatibility: rateLimiter() factory ───────────────────────────
function rateLimiter(options = {}) {
  const windowMs = options.windowMs || 15 * 60 * 1000;
  const max = options.max || 100;

  return rateLimit({
    windowMs,
    max,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => req.ip,
    skip: (req, res) => req.method === 'OPTIONS' && res.statusCode < 400,
    handler: (req, res) => {
      const requestId = req.requestId || 'unknown';
      const retryAfterSeconds = Math.ceil(windowMs / 1000);

      res.setHeader('Retry-After', String(retryAfterSeconds));

      res.status(429).json({
        error_code: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests. Please slow down and try again later.',
        detail: {
          retry_after_seconds: retryAfterSeconds,
        },
        policy_version: POLICY_VERSION,
        request_id: requestId,
      });
    },
  });
}

app.get('/health', async (req, res) => {
  const dbHealth = await checkDatabaseHealth();
  const uptimeSeconds = Math.floor(process.uptime());

  const payload = {
    service: APP_NAME,
    version: APP_VERSION,
    environment: NODE_ENV,
    status: dbHealth.ok ? 'ok' : 'degraded',
    policy_version: POLICY_VERSION,
    request_id: req.requestId,
    timestamp: new Date().toISOString(),
    uptime_seconds: uptimeSeconds,
    database: dbHealth,
  };

  const statusCode = dbHealth.ok ? 200 : 503;
  return res.status(statusCode).json(payload);
});

app.get('/ready', async (req, res) => {
  const dbHealth = await checkDatabaseHealth();

  if (!dbHealth.ok) {
    return res.status(503).json({
      error_code: 'SERVICE_NOT_READY',
      message: 'Service is not ready',
      detail: {
        database: dbHealth,
      },
      policy_version: POLICY_VERSION,
      request_id: req.requestId,
    });
  }

  return res.status(200).json({
    ready: true,
    service: APP_NAME,
    version: APP_VERSION,
    policy_version: POLICY_VERSION,
    request_id: req.requestId,
    timestamp: new Date().toISOString(),
  });
});

app.get(`${API_BASE_PATH}`, (req, res) => {
  return res.status(200).json({
    service: APP_NAME,
    version: APP_VERSION,
    protocol_version: process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0',
    policy_version: POLICY_VERSION,
    request_id: req.requestId,
    timestamp: new Date().toISOString(),
    endpoints: {
      health: '/health',
      readiness: '/ready',
      auth: `${API_BASE_PATH}/auth`,
      evidence: `${API_BASE_PATH}/evidence`,
      verify: `${API_BASE_PATH}/verify`,
      search: `${API_BASE_PATH}/search`,
      devices: `${API_BASE_PATH}/devices`,
      ownership: `${API_BASE_PATH}/ownership`,
      assets: `${API_BASE_PATH}/assets`,
    },
  });
});

// ── Mount routes only if successfully loaded ────────────────────────────────
if (evidenceRoutes) {
  app.use(`${API_BASE_PATH}/evidence`, registerRateLimit, evidenceRoutes);
}
if (verifyRoutes) {
  app.use(`${API_BASE_PATH}/verify`, verifyRateLimit, verifyRoutes);
}
if (searchRoutes) {
  app.use(`${API_BASE_PATH}/search`, searchRoutes);
}
if (devicesRoutes) {
  app.use(`${API_BASE_PATH}/devices`, authRateLimit, devicesRoutes);
}
if (ownershipRoutes) {
  app.use(`${API_BASE_PATH}/ownership`, authRateLimit, ownershipRoutes);
}
if (assetsRoutes) {
  app.use(`${API_BASE_PATH}/assets`, assetsRoutes);
}
if (searchV5Routes) {
  app.use(`${API_BASE_PATH}/v5/search`, searchV5Routes);
}
if (claimsV4Routes) {
  app.use(`${API_BASE_PATH}/v4/claims`, authRateLimit, claimsV4Routes);
}
if (authRoutes) {
  app.use(`${API_BASE_PATH}/auth`, authRateLimit, authRoutes);
}

app.use((req, res, next) => {
  next(
    createAppError({
      status: 404,
      errorCode: 'ROUTE_NOT_FOUND',
      message: 'Requested endpoint was not found',
      detail: {
        method: req.method,
        path: req.originalUrl,
      },
    })
  );
});

app.use((error, req, res, next) => {
  const status = normalizeHttpStatus(error.status || error.statusCode || 500);
  const errorCode =
    error.errorCode || error.code || inferErrorCode(status, error);
  const detail = sanitizeErrorDetail(error.detail, error, status);

  if (status >= 500) {
    console.error('[Unhandled Error]', {
      request_id: req.requestId,
      error_code: errorCode,
      message: error.message,
      stack: error.stack,
      path: req.originalUrl,
      method: req.method,
      timestamp: new Date().toISOString(),
    });
  }

  return res.status(status).json({
    error_code: errorCode,
    message: error.message || 'Internal server error',
    detail,
    policy_version: POLICY_VERSION,
    request_id: req.requestId,
  });
});

function createAppError({ status = 500, errorCode, message, detail = null }) {
  const error = new Error(message);
  error.status = status;
  error.errorCode = errorCode;
  error.detail = detail;
  return error;
}

function normalizeHttpStatus(status) {
  if (!Number.isInteger(status) || status < 100 || status > 599) {
    return 500;
  }
  return status;
}

function inferErrorCode(status, error) {
  if (status === 400) return 'BAD_REQUEST';
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 409) return 'CONFLICT';
  if (status === 413) return 'PAYLOAD_TOO_LARGE';
  if (status === 422) return 'VALIDATION_ERROR';
  if (status === 429) return 'RATE_LIMIT_EXCEEDED';
  if (status >= 500 && error?.name === 'PrismaClientKnownRequestError') {
    return 'DATABASE_OPERATION_FAILED';
  }
  return 'INTERNAL_SERVER_ERROR';
}

function sanitizeErrorDetail(explicitDetail, error, status) {
  if (explicitDetail !== undefined && explicitDetail !== null) {
    return explicitDetail;
  }

  if (error.type === 'entity.too.large') {
    return {
      limit_mb: MAX_JSON_BODY_MB,
    };
  }

  if (status >= 500 && !ENABLE_STACK_TRACES) {
    return null;
  }

  if (status >= 500 && ENABLE_STACK_TRACES) {
    return {
      stack: error.stack,
    };
  }

  return null;
}

let serverInstance = null;
let cleanupTimer = null;

async function startServer() {
  await connectDatabase();

  const cleanupIntervalMs = 10 * 60 * 1000;
  cleanupTimer = setInterval(async () => {
    try {
      const result = await cleanupExpiredSecurityArtifacts();
      if (ENABLE_REQUEST_LOGGING) {
        console.info('[Security Cleanup]', result);
      }
    } catch (error) {
      console.error('[Security Cleanup Failed]', {
        message: error.message,
        timestamp: new Date().toISOString(),
      });
    }
  }, cleanupIntervalMs);

  cleanupTimer.unref();

  serverInstance = app.listen(PORT, () => {
    console.info(
      `[${APP_NAME}] listening on port ${PORT} in ${NODE_ENV} mode at ${new Date().toISOString()}`
    );
  });

  return serverInstance;
}

async function shutdown(signal) {
  console.info(`[Shutdown] Received ${signal}. Closing resources...`);

  if (cleanupTimer) {
    clearInterval(cleanupTimer);
    cleanupTimer = null;
  }

  if (serverInstance) {
    await new Promise((resolve, reject) => {
      serverInstance.close((error) => {
        if (error) {
          return reject(error);
        }
        return resolve();
      });
    });
  }

  await disconnectDatabase();
  console.info('[Shutdown] Complete');
}

if (require.main === module) {
  startServer().catch((error) => {
    console.error('[Startup Failure]', {
      message: error.message,
      stack: error.stack,
      timestamp: new Date().toISOString(),
    });
    process.exit(1);
  });

  ['SIGINT', 'SIGTERM'].forEach((signal) => {
    process.on(signal, async () => {
      try {
        await shutdown(signal);
        process.exit(0);
      } catch (error) {
        console.error('[Forced Shutdown]', {
          signal,
          message: error.message,
          stack: error.stack,
          timestamp: new Date().toISOString(),
        });
        process.exit(1);
      }
    });
  });

  process.on('unhandledRejection', (reason) => {
    console.error('[Unhandled Rejection]', {
      reason:
        reason instanceof Error
          ? { message: reason.message, stack: reason.stack }
          : reason,
      timestamp: new Date().toISOString(),
    });
  });

  process.on('uncaughtException', async (error) => {
    console.error('[Uncaught Exception]', {
      message: error.message,
      stack: error.stack,
      timestamp: new Date().toISOString(),
    });

    try {
      await shutdown('uncaughtException');
    } catch (shutdownError) {
      console.error('[Shutdown After Exception Failed]', {
        message: shutdownError.message,
      });
    }

    process.exit(1);
  });
}

module.exports = {
  app,
  startServer,
  shutdown,
  createAppError,
  rateLimiter,
};
