const { PrismaClient } = require('@prisma/client');

const globalForPrisma = global;

const isProduction = process.env.NODE_ENV === 'production';
const logLevels = isProduction
  ? ['error', 'warn']
  : ['query', 'info', 'warn', 'error'];

const prisma =
  globalForPrisma.__dmvpPrisma ||
  new PrismaClient({
    log: logLevels.map((level) => ({
      emit: 'event',
      level,
    })),
    errorFormat: isProduction ? 'minimal' : 'pretty',
  });

if (!isProduction) {
  globalForPrisma.__dmvpPrisma = prisma;
}

prisma.$on('error', (event) => {
  console.error('[Prisma Error]', {
    message: event.message,
    target: event.target,
    timestamp: new Date().toISOString(),
  });
});

prisma.$on('warn', (event) => {
  console.warn('[Prisma Warning]', {
    message: event.message,
    target: event.target,
    timestamp: new Date().toISOString(),
  });
});

if (!isProduction) {
  prisma.$on('info', (event) => {
    console.info('[Prisma Info]', {
      message: event.message,
      target: event.target,
      timestamp: new Date().toISOString(),
    });
  });

  prisma.$on('query', (event) => {
    if (process.env.ENABLE_REQUEST_LOGGING === 'true') {
      console.debug('[Prisma Query]', {
        query: event.query,
        params: event.params,
        durationMs: event.duration,
        timestamp: new Date().toISOString(),
      });
    }
  });
}

let hasConnected = false;

async function connectDatabase() {
  if (hasConnected) {
    return prisma;
  }

  try {
    await prisma.$connect();

    await prisma.$queryRaw`SELECT 1`;

    hasConnected = true;

    console.info(
      `[Database] Connected successfully at ${new Date().toISOString()}`
    );

    return prisma;
  } catch (error) {
    console.error('[Database] Connection failed', {
      message: error.message,
      code: error.code || 'UNKNOWN_DATABASE_ERROR',
      timestamp: new Date().toISOString(),
    });

    throw error;
  }
}

async function disconnectDatabase() {
  if (!hasConnected) {
    return;
  }

  try {
    await prisma.$disconnect();
    hasConnected = false;

    console.info(
      `[Database] Disconnected successfully at ${new Date().toISOString()}`
    );
  } catch (error) {
    console.error('[Database] Disconnect failed', {
      message: error.message,
      code: error.code || 'UNKNOWN_DATABASE_ERROR',
      timestamp: new Date().toISOString(),
    });

    throw error;
  }
}

async function checkDatabaseHealth() {
  const startedAt = Date.now();

  try {
    await prisma.$queryRaw`SELECT 1`;

    return {
      ok: true,
      status: 'healthy',
      provider: 'postgresql',
      latency_ms: Date.now() - startedAt,
      checked_at: new Date().toISOString(),
    };
  } catch (error) {
    return {
      ok: false,
      status: 'unhealthy',
      provider: 'postgresql',
      latency_ms: Date.now() - startedAt,
      checked_at: new Date().toISOString(),
      error: {
        code: error.code || 'DATABASE_HEALTHCHECK_FAILED',
        message: error.message,
      },
    };
  }
}

async function cleanupExpiredSecurityArtifacts() {
  const now = new Date();

  const [expiredNonces, expiredIdempotencyKeys] = await Promise.all([
    prisma.nonce.deleteMany({
      where: {
        OR: [
          { expiresAt: { lt: now } },
          {
            consumedAt: {
              not: null,
            },
            expiresAt: {
              lt: new Date(now.getTime() - 60 * 60 * 1000),
            },
          },
        ],
      },
    }),
    prisma.idempotencyKey.deleteMany({
      where: {
        expiresAt: { lt: now },
      },
    }),
  ]);

  return {
    deleted_nonces: expiredNonces.count,
    deleted_idempotency_keys: expiredIdempotencyKeys.count,
    cleaned_at: now.toISOString(),
  };
}

module.exports = {
  prisma,
  connectDatabase,
  disconnectDatabase,
  checkDatabaseHealth,
  cleanupExpiredSecurityArtifacts,
};
