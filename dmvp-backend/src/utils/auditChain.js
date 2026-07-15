'use strict';
const crypto = require('crypto');
const { prisma } = require('../config/database');

async function getLatestHash() {
  const last = await prisma.auditLog.findFirst({
    orderBy: { auditLogId: 'desc' },
    select: { eventHash: true },
  });
  return last?.eventHash || null;
}

function computeEventHash(prevHash, data) {
  const payload = JSON.stringify({ prev: prevHash, ...data });
  return crypto.createHash('sha256').update(payload).digest('hex');
}

async function createChainedAuditLog(entry) {
  const prevHash = await getLatestHash();
  const eventHash = computeEventHash(prevHash, {
    action: entry.action,
    entityType: entry.entityType,
    entityId: entry.entityId,
    actorKeyId: entry.actorKeyId,
    requestId: entry.requestId,
    timestamp: new Date().toISOString(),
  });

  return prisma.auditLog.create({
    data: { ...entry, prevHash, eventHash },
  });
}

module.exports = { createChainedAuditLog, computeEventHash };
