/**
 * @file src/services/hybridSigningService.js
 * @description DMVP v4.0 — Hybrid Post-Quantum Signing Service
 *
 * Implements hybrid signing with:
 *   - Ed25519 (classical, current standard)
 *   - ML-DSA-65 / FIPS 204 (post-quantum, NIST standard)
 *
 * CRITICAL: Never use "CRYSTALS-Dilithium" — always use "ML-DSA-65" or "FIPS 204"
 *
 * @module services/hybridSigningService
 * @version dmvp-v4.0.0
 */

'use strict';

const crypto = require('crypto');

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const ALGORITHM_CLASSICAL = 'Ed25519';
const ALGORITHM_PQ = 'ML-DSA-65'; // FIPS 204 — NEVER use "CRYSTALS-Dilithium"
const ALGORITHM_HYBRID = 'Hybrid-Ed25519-ML-DSA-65';

// ML-DSA-65 parameters (FIPS 204)
const ML_DSA_65_SEED_BYTES = 32;
const ML_DSA_65_PUBLIC_KEY_BYTES = 1952;
const ML_DSA_65_SECRET_KEY_BYTES = 4000;
const ML_DSA_65_SIGNATURE_BYTES = 3309;

// ─────────────────────────────────────────────────────────────────────────────
// Ed25519 Key Generation (Classical)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generate Ed25519 key pair
 * @returns {{ publicKey: string, privateKey: string, algorithm: string }}
 */
function generateEd25519KeyPair() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519', {
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });

  return {
    publicKey,
    privateKey,
    algorithm: ALGORITHM_CLASSICAL,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// ML-DSA-65 Key Generation (Post-Quantum)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generate ML-DSA-65 key pair (simulated — in production, use oqs-node or liboqs)
 *
 * NOTE: This is a simulation layer. In production, integrate with:
 *   - oqs-node (https://github.com/open-quantum-safe/oqs-node)
 *   - liboqs v0.11+ system library
 *   - Bouncy Castle PQC for Java/Android
 *
 * @returns {{ publicKey: Buffer, privateKey: Buffer, algorithm: string }}
 */
function generateMLDSA65KeyPair() {
  // Simulated ML-DSA-65 key generation
  // In production: const oqs = require('oqs-node'); const keys = oqs.generateKeyPair('ML-DSA-65');
  const seed = crypto.randomBytes(ML_DSA_65_SEED_BYTES);
  
  // Simulated public/private key derivation (NOT cryptographically accurate)
  // Real implementation would use liboqs
  const publicKey = crypto.createHash('sha3-512').update(seed).digest();
  const privateKey = Buffer.concat([seed, publicKey]);

  return {
    publicKey: publicKey.slice(0, ML_DSA_65_PUBLIC_KEY_BYTES),
    privateKey: privateKey.slice(0, ML_DSA_65_SECRET_KEY_BYTES),
    algorithm: ALGORITHM_PQ,
    seed: seed.toString('hex'),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Hybrid Key Generation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generate hybrid key pair (Ed25519 + ML-DSA-65)
 * @returns {{ classical: object, pq: object, algorithm: string }}
 */
function generateHybridKeyPair() {
  const classical = generateEd25519KeyPair();
  const pq = generateMLDSA65KeyPair();

  return {
    classical,
    pq,
    algorithm: ALGORITHM_HYBRID,
    createdAt: new Date().toISOString(),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Ed25519 Signing (Classical)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sign data with Ed25519
 * @param {Buffer|string} data - Data to sign
 * @param {string} privateKeyPem - Ed25519 private key (PEM)
 * @returns {string} Base64-encoded signature
 */
function signEd25519(data, privateKeyPem) {
  const dataBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
  const signature = crypto.sign(null, dataBuffer, privateKeyPem);
  return signature.toString('base64');
}

/**
 * Verify Ed25519 signature
 * @param {Buffer|string} data - Original data
 * @param {string} signatureBase64 - Base64-encoded signature
 * @param {string} publicKeyPem - Ed25519 public key (PEM)
 * @returns {boolean}
 */
function verifyEd25519(data, signatureBase64, publicKeyPem) {
  try {
    const dataBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
    const signature = Buffer.from(signatureBase64, 'base64');
    return crypto.verify(null, dataBuffer, publicKeyPem, signature);
  } catch (error) {
    return false;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ML-DSA-65 Signing (Post-Quantum)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sign data with ML-DSA-65 (simulated)
 *
 * NOTE: In production, use oqs-node:
 *   const oqs = require('oqs-node');
 *   const signature = oqs.sign('ML-DSA-65', data, privateKey);
 *
 * @param {Buffer|string} data - Data to sign
 * @param {Buffer} privateKey - ML-DSA-65 private key
 * @returns {string} Base64-encoded signature
 */
function signMLDSA65(data, privateKey) {
  const dataBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
  
  // Simulated ML-DSA-65 signature (NOT cryptographically accurate)
  // Real implementation: const signature = oqs.sign('ML-DSA-65', dataBuffer, privateKey);
  const hash = crypto.createHash('sha3-512').update(dataBuffer).update(privateKey).digest();
  
  return hash.toString('base64');
}

/**
 * Verify ML-DSA-65 signature (simulated)
 *
 * NOTE: In production, use oqs-node:
 *   const valid = oqs.verify('ML-DSA-65', data, signature, publicKey);
 *
 * @param {Buffer|string} data - Original data
 * @param {string} signatureBase64 - Base64-encoded signature
 * @param {Buffer} publicKey - ML-DSA-65 public key
 * @returns {boolean}
 */
function verifyMLDSA65(data, signatureBase64, publicKey) {
  try {
    const dataBuffer = Buffer.isBuffer(data) ? data : Buffer.from(data);
    const signature = Buffer.from(signatureBase64, 'base64');
    
    // Simulated verification (NOT cryptographically accurate)
    // Real implementation: return oqs.verify('ML-DSA-65', dataBuffer, signature, publicKey);
    const expectedHash = crypto.createHash('sha3-512').update(dataBuffer).update(publicKey).digest();
    
    return expectedHash.equals(signature);
  } catch (error) {
    return false;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hybrid Signing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sign data with hybrid scheme (Ed25519 + ML-DSA-65)
 * @param {Buffer|string} data - Data to sign
 * @param {object} keys - { classical: { privateKey }, pq: { privateKey } }
 * @returns {{ classical_sig: string, pq_sig: string, algorithm: string }}
 */
function signHybrid(data, keys) {
  const classical_sig = signEd25519(data, keys.classical.privateKey);
  const pq_sig = signMLDSA65(data, keys.pq.privateKey);

  return {
    classical_sig,
    pq_sig,
    algorithm: ALGORITHM_HYBRID,
    timestamp: new Date().toISOString(),
  };
}

/**
 * Verify hybrid signature
 * @param {Buffer|string} data - Original data
 * @param {object} signatures - { classical_sig, pq_sig }
 * @param {object} keys - { classical: { publicKey }, pq: { publicKey } }
 * @returns {{ classical_valid: boolean, pq_valid: boolean, hybrid_valid: boolean }}
 */
function verifyHybrid(data, signatures, keys) {
  const classical_valid = verifyEd25519(data, signatures.classical_sig, keys.classical.publicKey);
  const pq_valid = verifyMLDSA65(data, signatures.pq_sig, keys.pq.publicKey);

  return {
    classical_valid,
    pq_valid,
    hybrid_valid: classical_valid && pq_valid,
    algorithm: ALGORITHM_HYBRID,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Certificate Generation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generate ownership certificate with hybrid signatures
 * @param {object} evidenceData - Evidence record data
 * @param {object} keys - Hybrid key pair
 * @returns {object} Signed certificate
 */
function generateOwnershipCertificate(evidenceData, keys) {
  const certificateData = {
    certificate_id: crypto.randomUUID(),
    evidence_id: evidenceData.evidenceId,
    uaid: evidenceData.uaid || null,
    sha256: evidenceData.sha256,
    media_type: evidenceData.mediaType,
    device_key_id: evidenceData.deviceKeyId,
    issued_at: new Date().toISOString(),
    valid_until: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(), // 1 year
    version: '4.0.0',
  };

  // Canonical JSON for signing
  const canonicalData = JSON.stringify(certificateData, Object.keys(certificateData).sort());
  
  // Hybrid signature
  const signatures = signHybrid(canonicalData, keys);

  return {
    ...certificateData,
    classical_sig: signatures.classical_sig,
    classical_algorithm: ALGORITHM_CLASSICAL,
    pq_sig: signatures.pq_sig,
    pq_algorithm: ALGORITHM_PQ, // "ML-DSA-65" — NEVER "CRYSTALS-Dilithium"
    hybrid_algorithm: ALGORITHM_HYBRID,
    signatures,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Exports
// ─────────────────────────────────────────────────────────────────────────────

module.exports = {
  // Constants
  ALGORITHM_CLASSICAL,
  ALGORITHM_PQ,
  ALGORITHM_HYBRID,
  
  // Key generation
  generateEd25519KeyPair,
  generateMLDSA65KeyPair,
  generateHybridKeyPair,
  
  // Classical signing
  signEd25519,
  verifyEd25519,
  
  // Post-quantum signing
  signMLDSA65,
  verifyMLDSA65,
  
  // Hybrid signing
  signHybrid,
  verifyHybrid,
  
  // Certificate generation
  generateOwnershipCertificate,
};
