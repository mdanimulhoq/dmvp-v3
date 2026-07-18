'use strict';

const { ulid } = require('ulid');

/**
 * UAID (Universal Asset Identifier) Generator
 * TDD v5 Phase 1 Step 1.1
 * 
 * Format: uaid_5_<tenant>_<ULID>
 * Example: uaid_5_t1_01J3ZQX...
 * 
 * ULID: Time-sortable, monotonic, globally unique
 */

const UAID_PREFIX = 'uaid';
const UAID_VERSION = '5';
const UAID_SEPARATOR = '_';

/**
 * Generate a new UAID for an asset
 * 
 * @param {string} tenantId - Tenant identifier (e.g., 't1', 'tenant_abc')
 * @returns {string} UAID in format: uaid_5_<tenant>_<ULID>
 * @throws {Error} If tenantId is invalid
 */
function generateUAID(tenantId) {
  if (!tenantId || typeof tenantId !== 'string') {
    throw new Error('tenantId must be a non-empty string');
  }

  // Sanitize tenant ID (remove separator characters to avoid format issues)
  const sanitizedTenant = tenantId.replace(/[_\s]/g, '').toLowerCase();
  
  if (sanitizedTenant.length === 0) {
    throw new Error('tenantId cannot be empty or contain only separators');
  }

  const ulidValue = ulid();
  
  return `${UAID_PREFIX}${UAID_SEPARATOR}${UAID_VERSION}${UAID_SEPARATOR}${sanitizedTenant}${UAID_SEPARATOR}${ulidValue}`;
}

/**
 * Parse a UAID into its components
 * 
 * @param {string} uaid - UAID string
 * @returns {Object|null} { prefix, version, tenant, ulid } or null if invalid
 */
function parseUAID(uaid) {
  if (!uaid || typeof uaid !== 'string') {
    return null;
  }

  const parts = uaid.split(UAID_SEPARATOR);
  
  if (parts.length !== 4) {
    return null;
  }

  const [prefix, version, tenant, ulidPart] = parts;

  if (prefix !== UAID_PREFIX || version !== UAID_VERSION) {
    return null;
  }

  if (!tenant || !ulidPart) {
    return null;
  }

  // Validate ULID format (26 characters, Crockford's base32)
  if (!/^[0-9A-HJKMNP-TV-Z]{26}$/i.test(ulidPart)) {
    return null;
  }

  return {
    prefix,
    version,
    tenant,
    ulid: ulidPart,
  };
}

/**
 * Validate if a string is a valid UAID
 * 
 * @param {string} uaid - UAID string to validate
 * @returns {boolean} True if valid UAID
 */
function isValidUAID(uaid) {
  return parseUAID(uaid) !== null;
}

/**
 * Extract timestamp from UAID's ULID component
 * 
 * @param {string} uaid - UAID string
 * @returns {Date|null} Timestamp or null if invalid
 */
function extractTimestamp(uaid) {
  const parsed = parseUAID(uaid);
  if (!parsed) {
    return null;
  }

  try {
    // ULID timestamp is encoded in first 10 characters (48 bits, milliseconds)
    const ulidPart = parsed.ulid;
    const timeChars = ulidPart.substring(0, 10);
    
    // Decode Crockford's base32
    const ENCODING = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    let timestamp = 0;
    
    for (let i = 0; i < timeChars.length; i++) {
      const char = timeChars[i].toUpperCase();
      const value = ENCODING.indexOf(char);
      if (value === -1) {
        return null;
      }
      timestamp = timestamp * 32 + value;
    }

    return new Date(timestamp);
  } catch (error) {
    return null;
  }
}

module.exports = {
  generateUAID,
  parseUAID,
  isValidUAID,
  extractTimestamp,
  UAID_PREFIX,
  UAID_VERSION,
};
