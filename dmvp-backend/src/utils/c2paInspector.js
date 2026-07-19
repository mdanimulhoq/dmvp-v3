'use strict';

/**
 * C2PA (Coalition for Content Provenance and Authenticity) Manifest Inspector
 * TDD v5 Phase 1 Step 1.5
 * 
 * Detects and validates C2PA Content Credentials in assets.
 * Returns three states: verified, present_unverified, or absent
 * 
 * Important: C2PA absence does NOT mean rejection - it's just a signal.
 */

const { createC2pa } = require('c2pa-node');

const c2pa = createC2pa();

/**
 * Inspect C2PA manifest in an asset
 * 
 * @param {Buffer} assetBuffer - Asset file buffer
 * @param {string} mimeType - MIME type of the asset
 * @returns {Promise<Object>} C2PA inspection result
 */
async function inspectC2paManifest(assetBuffer, mimeType) {
  try {
    // Try to read C2PA manifest
    const result = await c2pa.read({ buffer: assetBuffer, mimeType });
    const manifest = result?.activeManifest || result;
    
    if (!manifest) {
      // No C2PA manifest found
      return {
        c2pa_status: 'absent',
        c2pa_manifest: null,
        c2pa_verified: false,
      };
    }

    // Check if manifest is properly signed/verified
    const isVerified = manifest.signature && manifest.signature.verified === true;
    
    return {
      c2pa_status: isVerified ? 'verified' : 'present_unverified',
      c2pa_manifest: {
        claim_generator: manifest.claim_generator || null,
        claim_generator_info: manifest.claim_generator_info || null,
        format: manifest.format || null,
        instance_id: manifest.instance_id || null,
        assertions: manifest.assertions || [],
        signature_algo: manifest.signature?.algorithm || null,
        signature_time: manifest.signature?.time || null,
      },
      c2pa_verified: isVerified,
    };
  } catch (error) {
    // If c2pa-node throws an error, treat as absent
    // (could be unsupported format, corrupted manifest, etc.)
    return {
      c2pa_status: 'absent',
      c2pa_manifest: null,
      c2pa_verified: false,
      c2pa_error: error.message,
    };
  }
}

/**
 * Compute hash of C2PA manifest for storage
 * 
 * @param {Object} c2paManifest - C2PA manifest object
 * @returns {string|null} SHA-256 hash of manifest or null
 */
function computeC2paManifestHash(c2paManifest) {
  if (!c2paManifest) return null;
  
  const crypto = require('crypto');
  const manifestString = JSON.stringify(c2paManifest, Object.keys(c2paManifest).sort());
  return crypto.createHash('sha256').update(manifestString).digest('hex');
}

module.exports = {
  inspectC2paManifest,
  computeC2paManifestHash,
};
