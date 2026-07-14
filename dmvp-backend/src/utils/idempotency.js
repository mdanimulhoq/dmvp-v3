'use strict';

const crypto = require('crypto');
const { canonicalize } = require('./canonicalJson');

const IDEMPOTENCY_KEY_HEADER = 'idempotency-key';
const IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000;
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{12}$/i;

function getIdempotencyKey(req) {
  const value = req.headers[IDEMPOTENCY_KEY_HEADER];
  return typeof value === 'string' ? value.trim() : null;
}

function isValidIdempotencyKey(value) {
  return typeof value === 'string' && UUID_REGEX.test(value);
}

function computeRequestHash(body) {
  const canonicalBody = canonicalize(body || {});
  return crypto.createHash('sha256').update(canonicalBody, 'utf8').digest('hex');
}

function buildExpiresAt(now = new Date(), ttlMs = IDEMPOTENCY_TTL_MS) {
  return new Date(now.getTime() + ttlMs);
}

function evaluateExistingRecord(existing, scope, requestHash) {
  if (!existing) {
    return { action: 'claim' };
  }

  if (existing.scope !== scope || existing.requestHash !== requestHash) {
    return { action: 'conflict', existing };
  }

  if (existing.responseCode && existing.responseBody) {
    return { action: 'replay', existing };
  }

  return { action: 'in_progress', existing };
}

async function claimIdempotencyKey({
  prisma,
  key,
  scope,
  requestHash,
  requestId,
  ttlMs = IDEMPOTENCY_TTL_MS,
}) {
  const existing = await prisma.idempotencyKey.findUnique({
    where: { key },
  });

  const existingDecision = evaluateExistingRecord(existing, scope, requestHash);
  if (existingDecision.action !== 'claim') {
    return existingDecision;
  }

  try {
    const record = await prisma.idempotencyKey.create({
      data: {
        key,
        scope,
        requestHash,
        requestId: requestId || null,
        expiresAt: buildExpiresAt(new Date(), ttlMs),
      },
    });

    return { action: 'created', record };
  } catch (error) {
    if (error.code !== 'P2002') {
      throw error;
    }

    const concurrent = await prisma.idempotencyKey.findUnique({
      where: { key },
    });

    return evaluateExistingRecord(concurrent, scope, requestHash);
  }
}

async function completeIdempotencyKey({
  prisma,
  key,
  responseCode,
  responseBody,
}) {
  return prisma.idempotencyKey.update({
    where: { key },
    data: {
      responseCode,
      responseBody,
      lastSeenAt: new Date(),
    },
  });
}

module.exports = {
  IDEMPOTENCY_KEY_HEADER,
  IDEMPOTENCY_TTL_MS,
  getIdempotencyKey,
  isValidIdempotencyKey,
  computeRequestHash,
  buildExpiresAt,
  evaluateExistingRecord,
  claimIdempotencyKey,
  completeIdempotencyKey,
};
