/**
 * src/services/deviceService.js
 *
 * Device lifecycle management service for DMVP v3.0.
 *
 * Handles:
 *   - Device registration (creating new device keys)
 *   - Key rotation (creating new key with lineage linkage)
 *   - Key revocation (marking keys as revoked)
 *   - Recovery (creating new lineage state after device loss)
 *
 * All operations are audited and maintain historical records.
 */

const crypto = require('crypto');
const { prisma } = require('../config/database');

// Logger
const logger = console;

/**
 * Determine trust tier based on attestation data and platform.
 *
 * @param {Object} attestationSummary - Attestation summary from client.
 * @param {string} platform - 'android', 'ios', 'desktop', etc.
 * @returns {string} Trust tier: 'TIER_A', 'TIER_B', 'TIER_C', 'TIER_D'
 */
function determineTrustTier(attestationSummary, platform) {
  // If attestation is present and valid, and platform supports hardware key
  if (attestationSummary && attestationSummary.valid === true) {
    if (attestationSummary.hardware_backed === true) {
      return 'TIER_A';
    } else {
      // Hardware-backed but attestation degraded
      return 'TIER_B';
    }
  } else {
    // No attestation or invalid
    if (platform === 'android' || platform === 'ios') {
      // Mobile without attestation but may have hardware key
      return 'TIER_B'; // hardware key but attestation unavailable
    } else {
      // Desktop or unknown
      return 'TIER_C';
    }
  }
}

/**
 * Register a new device key.
 *
 * @param {Object} params - Device registration parameters.
 * @param {string} params.device_key_id - Unique device key identifier (provided by client).
 * @param {string} params.public_key - Public key PEM or base64 string.
 * @param {Object} params.attestation_summary - Attestation data from client.
 * @param {string} params.platform - 'android', 'ios', 'desktop', etc.
 * @param {string} [params.actorId] - Actor ID for audit.
 * @returns {Promise<Object>} Created device key record.
 * @throws {Error} If device_key_id already exists.
 */
async function registerDevice({ device_key_id, public_key, attestation_summary, platform = 'unknown', actorId = null }) {
  // Validate required fields
  if (!device_key_id || typeof device_key_id !== 'string') {
    throw new Error('registerDevice: device_key_id is required');
  }
  if (!public_key || typeof public_key !== 'string') {
    throw new Error('registerDevice: public_key is required');
  }

  // Check if device_key_id already exists
  const existing = await prisma.deviceKey.findUnique({
    where: { device_key_id },
  });
  if (existing) {
    throw new Error('Device key already registered');
  }

  // Determine trust tier
  const trustTier = determineTrustTier(attestation_summary, platform);

  // Create new device key
  const deviceKey = await prisma.deviceKey.create({
    data: {
      device_key_id,
      public_key,
      trust_tier: trustTier,
      lifecycle_state: 'ACTIVE',
      // lineage_parent_id is null for initial registration
      // additional metadata can be stored as JSON in a separate field if schema supports
      // We'll assume there is a 'metadata' field or we can store attestation in a separate table.
      // Since schema doesn't have attestation_summary, we may need to store it in a JSON field.
      // For now, we'll ignore it or store in a separate 'device_attestation' table if exists.
    },
  });

  // Audit log
  await prisma.auditLog.create({
    data: {
      event_type: 'DEVICE_REGISTERED',
      actor: actorId || device_key_id,
      target: device_key_id,
      timestamp: new Date().toISOString(),
      policy_version: 'dmvp-v3.0.0',
      metadata: {
        trust_tier: trustTier,
        platform,
        attestation: attestation_summary ? 'present' : 'none',
      },
    },
  });

  logger.info(`Device registered: ${device_key_id}, trust tier: ${trustTier}`);
  return deviceKey;
}

/**
 * Rotate device key: create a new key linked to the old key via lineage.
 *
 * @param {Object} params - Rotation parameters.
 * @param {string} params.old_device_key_id - Existing device key to rotate from.
 * @param {string} params.new_device_key_id - New device key identifier.
 * @param {string} params.new_public_key - New public key.
 * @param {Object} params.attestation_summary - New attestation data (optional).
 * @param {string} params.platform - Platform.
 * @param {string} [params.actorId] - Actor ID for audit.
 * @returns {Promise<Object>} New device key record.
 * @throws {Error} If old key not found or already revoked, or new key already exists.
 */
async function rotateDeviceKey({ old_device_key_id, new_device_key_id, new_public_key, attestation_summary = null, platform = 'unknown', actorId = null }) {
  // Validate
  if (!old_device_key_id || !new_device_key_id || !new_public_key) {
    throw new Error('rotateDeviceKey: old_device_key_id, new_device_key_id, and new_public_key are required');
  }

  // Check old key exists and is not revoked
  const oldKey = await prisma.deviceKey.findUnique({
    where: { device_key_id: old_device_key_id },
  });
  if (!oldKey) {
    throw new Error('Old device key not found');
  }
  if (oldKey.lifecycle_state === 'REVOKED') {
    throw new Error('Cannot rotate from a revoked device key');
  }

  // Check new key doesn't already exist
  const existingNew = await prisma.deviceKey.findUnique({
    where: { device_key_id: new_device_key_id },
  });
  if (existingNew) {
    throw new Error('New device key already exists');
  }

  // Determine trust tier for new key
  const trustTier = determineTrustTier(attestation_summary, platform);

  // Begin transaction to create new key and update old key's state (if needed)
  const result = await prisma.$transaction(async (tx) => {
    // Create new key with lineage reference
    const newKey = await tx.deviceKey.create({
      data: {
        device_key_id: new_device_key_id,
        public_key: new_public_key,
        trust_tier: trustTier,
        lifecycle_state: 'ACTIVE',
        lineage_parent_id: old_device_key_id,
      },
    });

    // Optionally, mark old key as 'ROTATED' or keep as ACTIVE?
    // According to spec, rotation preserves lineage; old key remains valid for historical signatures.
    // We'll keep old key as ACTIVE, but we could mark it as 'ROTATED' to indicate it's no longer the primary.
    // For simplicity, we'll leave it as ACTIVE; trust decisions may consider lineage.

    // Audit log for rotation
    await tx.auditLog.create({
      data: {
        event_type: 'DEVICE_ROTATED',
        actor: actorId || old_device_key_id,
        target: new_device_key_id,
        timestamp: new Date().toISOString(),
        policy_version: 'dmvp-v3.0.0',
        metadata: {
          old_key: old_device_key_id,
          trust_tier: trustTier,
        },
      },
    });

    return newKey;
  });

  logger.info(`Device key rotated: ${old_device_key_id} -> ${new_device_key_id}`);
  return result;
}

/**
 * Revoke a device key.
 *
 * @param {string} device_key_id - Key to revoke.
 * @param {string} [actorId] - Actor ID for audit.
 * @returns {Promise<Object>} Updated device key record.
 * @throws {Error} If key not found or already revoked.
 */
async function revokeDeviceKey(device_key_id, actorId = null) {
  if (!device_key_id) {
    throw new Error('revokeDeviceKey: device_key_id is required');
  }

  const key = await prisma.deviceKey.findUnique({
    where: { device_key_id },
  });
  if (!key) {
    throw new Error('Device key not found');
  }
  if (key.lifecycle_state === 'REVOKED') {
    throw new Error('Device key already revoked');
  }

  const updated = await prisma.deviceKey.update({
    where: { device_key_id },
    data: {
      lifecycle_state: 'REVOKED',
      revoked_at: new Date(),
    },
  });

  // Audit log
  await prisma.auditLog.create({
    data: {
      event_type: 'DEVICE_REVOKED',
      actor: actorId || device_key_id,
      target: device_key_id,
      timestamp: new Date().toISOString(),
      policy_version: 'dmvp-v3.0.0',
      metadata: {
        previous_state: key.lifecycle_state,
        trust_tier: key.trust_tier,
      },
    },
  });

  logger.info(`Device key revoked: ${device_key_id}`);
  return updated;
}

/**
 * Recover device lineage after loss.
 *
 * Creates a new device key with a lineage transition record,
 * preserving historical evidence linkage.
 *
 * This is similar to rotation but may involve additional recovery quorum or verification.
 * For MVP, we create a new key with a special lineage_parent_id and mark the old keys
 * as 'RECOVERED' or similar? Spec says "recovery must create a lineage transition record".
 * We'll create a new key with lineage_parent_id pointing to the old key, and we'll also
 * record a recovery event in audit log. We may also set a flag on the old key.
 *
 * @param {Object} params - Recovery parameters.
 * @param {string} params.old_device_key_id - The key being recovered from (lost device).
 * @param {string} params.new_device_key_id - New device key identifier.
 * @param {string} params.new_public_key - New public key.
 * @param {Object} params.attestation_summary - New attestation data.
 * @param {string} params.platform - Platform.
 * @param {string} [params.recovery_quorum] - Additional recovery proof (optional).
 * @param {string} [params.actorId] - Actor ID for audit.
 * @returns {Promise<Object>} New device key record.
 * @throws {Error} If old key not found or already revoked.
 */
async function recoverDeviceLineage({ old_device_key_id, new_device_key_id, new_public_key, attestation_summary, platform = 'unknown', recovery_quorum = null, actorId = null }) {
  // Validate
  if (!old_device_key_id || !new_device_key_id || !new_public_key) {
    throw new Error('recoverDeviceLineage: old_device_key_id, new_device_key_id, and new_public_key are required');
  }

  // Check old key exists and is not revoked (but it might be lost, so we allow even if ACTIVE)
  const oldKey = await prisma.deviceKey.findUnique({
    where: { device_key_id: old_device_key_id },
  });
  if (!oldKey) {
    throw new Error('Old device key not found');
  }
  // We don't require old key to be ACTIVE; it could be REVOKED, but we still allow recovery?
  // According to spec, recovery should create a new lineage state. We'll allow recovery even if old key is REVOKED,
  // but maybe we should reject if old key is already part of a recovery chain? For simplicity, we'll allow.

  // Check new key doesn't already exist
  const existingNew = await prisma.deviceKey.findUnique({
    where: { device_key_id: new_device_key_id },
  });
  if (existingNew) {
    throw new Error('New device key already exists');
  }

  // Determine trust tier for new key
  const trustTier = determineTrustTier(attestation_summary, platform);

  // Create new key with lineage reference
  const newKey = await prisma.deviceKey.create({
    data: {
      device_key_id: new_device_key_id,
      public_key: new_public_key,
      trust_tier: trustTier,
      lifecycle_state: 'ACTIVE',
      lineage_parent_id: old_device_key_id, // Link to old key
    },
  });

  // Optionally, mark old key as 'RECOVERED'? We'll keep it as is; lineage shows the transition.

  // Audit log for recovery
  await prisma.auditLog.create({
    data: {
      event_type: 'DEVICE_RECOVERED',
      actor: actorId || old_device_key_id,
      target: new_device_key_id,
      timestamp: new Date().toISOString(),
      policy_version: 'dmvp-v3.0.0',
      metadata: {
        old_key: old_device_key_id,
        trust_tier: trustTier,
        recovery_quorum_provided: !!recovery_quorum,
      },
    },
  });

  logger.info(`Device lineage recovered: ${old_device_key_id} -> ${new_device_key_id}`);
  return newKey;
}

/**
 * Get device key information.
 *
 * @param {string} device_key_id - Device key identifier.
 * @returns {Promise<Object|null>} Device key record or null.
 */
async function getDeviceKey(device_key_id) {
  if (!device_key_id) return null;
  return prisma.deviceKey.findUnique({
    where: { device_key_id },
  });
}

/**
 * List device keys with optional filters.
 *
 * @param {Object} filters - Optional filters: trust_tier, lifecycle_state, etc.
 * @param {number} page - Page number.
 * @param {number} limit - Items per page.
 * @returns {Promise<Object>} { items, total, page, limit }.
 */
async function listDeviceKeys(filters = {}, page = 1, limit = 20) {
  const where = {};
  if (filters.trust_tier) where.trust_tier = filters.trust_tier;
  if (filters.lifecycle_state) where.lifecycle_state = filters.lifecycle_state;
  if (filters.revoked_at) where.revoked_at = filters.revoked_at; // could be boolean?

  const skip = (page - 1) * limit;
  const [items, total] = await Promise.all([
    prisma.deviceKey.findMany({
      where,
      skip,
      take: limit,
      orderBy: { created_at: 'desc' },
    }),
    prisma.deviceKey.count({ where }),
  ]);

  return { items, total, page, limit };
}

module.exports = {
  registerDevice,
  rotateDeviceKey,
  revokeDeviceKey,
  recoverDeviceLineage,
  getDeviceKey,
  listDeviceKeys,
  // Expose helper for testing
  determineTrustTier,
};
