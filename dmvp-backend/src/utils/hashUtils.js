/**
 * src/utils/hashUtils.js
 *
 * Pure utility functions for cryptographic hashing, canonical media normalization,
 * and similarity computations used throughout the DMVP v3 backend.
 *
 * All functions are synchronous and stateless, with comprehensive input validation
 * and error handling.
 */

const crypto = require('crypto');

// Import sharp for image processing only if available (optional dependency)
let sharp = null;
try {
  sharp = require('sharp');
} catch (_) {
  // sharp not installed - fallback to plain hash for images
}

/**
 * Compute SHA-256 hash of input data.
 *
 * @param {Buffer|string|stream.Readable} input - Data to hash. If string, treated as UTF-8.
 * @returns {string} 64-character lowercase hex digest.
 * @throws {TypeError} If input is not a Buffer, string, or Readable stream.
 */
function computeSHA256(input) {
  if (typeof input === 'string') {
    return crypto.createHash('sha256').update(input, 'utf8').digest('hex');
  }
  if (Buffer.isBuffer(input)) {
    return crypto.createHash('sha256').update(input).digest('hex');
  }
  // Check for readable stream (has .pipe and .on)
  if (input && typeof input.pipe === 'function' && typeof input.on === 'function') {
    return new Promise((resolve, reject) => {
      const hash = crypto.createHash('sha256');
      input.on('data', (chunk) => hash.update(chunk));
      input.on('end', () => resolve(hash.digest('hex')));
      input.on('error', reject);
    });
  }
  throw new TypeError('computeSHA256: input must be Buffer, string, or Readable stream');
}

/**
 * Compute a canonical media hash after deterministic normalization.
 *
 * For images: strip EXIF and other metadata, re-encode to baseline JPEG with standard quality.
 * For videos: normalize container-level metadata (removes editing software tags, etc.).
 *
 * Currently, only image normalization is implemented (requires 'sharp' package).
 * Video normalization returns null (fallback to original SHA-256).
 *
 * @param {Buffer} buffer - Raw file bytes.
 * @param {string} mediaType - 'image' or 'video'.
 * @returns {Promise<string|null>} 64-character hex digest or null if normalization not supported.
 */
async function computeCanonicalMediaHash(buffer, mediaType) {
  if (!Buffer.isBuffer(buffer)) {
    throw new TypeError('computeCanonicalMediaHash: buffer must be a Buffer');
  }
  if (typeof mediaType !== 'string') {
    throw new TypeError('computeCanonicalMediaHash: mediaType must be a string');
  }

  const type = mediaType.toLowerCase();
  if (type === 'image') {
    if (!sharp) {
      // sharp not installed; fallback to original hash (not canonical)
      return computeSHA256(buffer);
    }
    try {
      // Strip all metadata and re-encode to baseline JPEG with quality 90
      // This provides a deterministic representation independent of EXIF, comments, etc.
      const normalized = await sharp(buffer)
        .jpeg({
          quality: 90,
          mozjpeg: true,
          chromaSubsampling: '4:2:0',
          trellisQuantisation: true,
          overshootDeringing: true,
          optimiseScans: true,
          progressive: false,
        })
        .toBuffer();
      return computeSHA256(normalized);
    } catch (err) {
      // On processing failure, log and fallback to original hash
      console.warn('computeCanonicalMediaHash: sharp processing failed, falling back to original SHA-256:', err.message);
      return computeSHA256(buffer);
    }
  } else if (type === 'video') {
    // Video normalization is more complex and often requires external tools.
    // For now, return null to indicate unsupported.
    // In production, you could implement using ffmpeg with deterministic encoding settings.
    return null;
  } else {
    throw new Error(`computeCanonicalMediaHash: unsupported mediaType "${mediaType}"`);
  }
}

/**
 * Compute Hamming distance between two binary hash strings.
 *
 * Supports hex-encoded strings (e.g., SHA-256) or binary strings of '0'/'1'.
 * Both inputs must be of equal length.
 *
 * @param {string} hashA - First hash string.
 * @param {string} hashB - Second hash string.
 * @returns {number} Number of differing bits (integer).
 * @throws {TypeError} If inputs are not strings or lengths differ.
 */
function hammingDistance(hashA, hashB) {
  if (typeof hashA !== 'string' || typeof hashB !== 'string') {
    throw new TypeError('hammingDistance: both arguments must be strings');
  }
  if (hashA.length !== hashB.length) {
    throw new Error('hammingDistance: hash strings must be of equal length');
  }

  let distance = 0;
  // Detect if strings are hex (only 0-9a-f)
  const hexRegex = /^[0-9a-fA-F]+$/;
  const isHex = hexRegex.test(hashA) && hexRegex.test(hashB);

  if (isHex && hashA.length % 2 === 0 && hashB.length % 2 === 0) {
    // Hex strings: compare bytes
    const bufA = Buffer.from(hashA, 'hex');
    const bufB = Buffer.from(hashB, 'hex');
    for (let i = 0; i < bufA.length; i++) {
      const xor = bufA[i] ^ bufB[i];
      // Count bits in xor (popcount)
      distance += popCount(xor);
    }
  } else {
    // Assume binary strings of '0' and '1'
    for (let i = 0; i < hashA.length; i++) {
      if (hashA[i] !== hashB[i]) distance++;
    }
  }
  return distance;
}

/**
 * Popcount for 8-bit integer (number of set bits).
 * @param {number} byte - 0-255
 * @returns {number} count of 1 bits.
 */
function popCount(byte) {
  let count = 0;
  while (byte) {
    count += byte & 1;
    byte >>= 1;
  }
  return count;
}

/**
 * Compare two robust fingerprint profiles and compute a similarity score.
 *
 * The profile object is expected to contain fields such as:
 *   - phash: string (binary or hex) - perceptual hash
 *   - dhash: string (binary or hex) - difference hash
 *   - blockHash: string (binary or hex) - block-based hash
 *   - localFeatures: (optional) not used in this simple comparison
 *
 * The function calculates a weighted average of normalized Hamming distances,
 * where smaller distance means higher similarity.
 *
 * @param {object} profileA - First fingerprint profile.
 * @param {object} profileB - Second fingerprint profile.
 * @param {object} weights - Optional weighting object with keys: phash, dhash, blockHash.
 *                           Default: { phash: 0.5, dhash: 0.3, blockHash: 0.2 }.
 * @returns {number} Similarity score between 0.0 (no similarity) and 1.0 (identical).
 * @throws {TypeError} If profile objects are invalid.
 */
function compareFingerprintProfiles(profileA, profileB, weights = null) {
  if (!profileA || typeof profileA !== 'object') {
    throw new TypeError('compareFingerprintProfiles: profileA must be an object');
  }
  if (!profileB || typeof profileB !== 'object') {
    throw new TypeError('compareFingerprintProfiles: profileB must be an object');
  }

  const defaultWeights = { phash: 0.5, dhash: 0.3, blockHash: 0.2 };
  const w = weights || defaultWeights;

  // Ensure total weight sums to 1
  const totalWeight = Object.values(w).reduce((sum, val) => sum + val, 0);
  if (Math.abs(totalWeight - 1.0) > 1e-9) {
    throw new Error('compareFingerprintProfiles: weights must sum to 1');
  }

  const fields = ['phash', 'dhash', 'blockHash'];
  let weightedSimilarity = 0;
  let usedWeight = 0;

  for (const field of fields) {
    if (profileA[field] && profileB[field] && w[field] && w[field] > 0) {
      const hashA = profileA[field];
      const hashB = profileB[field];
      // Only compare if both are strings and same length
      if (typeof hashA === 'string' && typeof hashB === 'string' && hashA.length === hashB.length) {
        let distance;
        try {
          distance = hammingDistance(hashA, hashB);
        } catch (err) {
          // If hammingDistance fails, skip this field
          continue;
        }
        // Normalize distance: similarity = 1 - (distance / bitLength)
        const bitLength = hashA.length * (isHexString(hashA) ? 4 : 1); // hex: each char=4 bits, binary:1
        const similarity = Math.max(0, 1 - (distance / bitLength));
        weightedSimilarity += w[field] * similarity;
        usedWeight += w[field];
      }
    }
  }

  // If no fields were compared, return 0
  if (usedWeight === 0) return 0.0;

  // Normalize by actual used weight (in case some fields missing)
  return Math.min(1, Math.max(0, weightedSimilarity / usedWeight));
}

/**
 * Helper to detect if a string is hex-encoded (only 0-9a-fA-F).
 */
function isHexString(str) {
  return /^[0-9a-fA-F]+$/.test(str);
}

module.exports = {
  computeSHA256,
  computeCanonicalMediaHash,
  hammingDistance,
  compareFingerprintProfiles,
};
