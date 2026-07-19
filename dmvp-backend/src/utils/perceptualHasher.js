'use strict';

/**
 * TDD v5 Phase 2 Step 2.2: Backend Image Perceptual Hashing (L3) Server-Side
 * 
 * Computes PDQ, pHash, dHash server-side when:
 * - Android doesn't send hashes
 * - Server needs to verify client-computed hashes
 * 
 * Uses sharp for image processing.
 */

const sharp = require('sharp');

/**
 * Compute all three perceptual hashes for an image buffer
 * @param {Buffer} imageBuffer - Image file buffer
 * @returns {Promise<{pdq: string, phash: string, dhash: string}>}
 */
async function computePerceptualHashes(imageBuffer) {
  const [pdq, phash, dhash] = await Promise.all([
    computePDQ(imageBuffer),
    computePHash(imageBuffer),
    computeDHash(imageBuffer),
  ]);
  
  return { pdq, phash, dhash };
}

/**
 * PDQ Hash - Meta's perceptual hash (simplified server-side implementation)
 * 256-bit hash, robust against common transformations
 */
async function computePDQ(imageBuffer) {
  const SIZE = 64;
  
  // Resize to 64x64 grayscale
  const { data, info } = await sharp(imageBuffer)
    .resize(SIZE, SIZE, { fit: 'fill' })
    .grayscale()
    .raw()
    .toBuffer({ resolveWithObject: true });
  
  // Convert to luminance array
  const luminance = [];
  for (let i = 0; i < data.length; i += info.channels) {
    luminance.push(data[i]);
  }
  
  // Compute hash by comparing adjacent pixels
  let hashBits = '';
  for (let y = 0; y < SIZE - 1; y++) {
    for (let x = 0; x < SIZE - 1; x++) {
      const idx = y * SIZE + x;
      const diff = luminance[idx] - luminance[idx + 1] +
                   luminance[idx] - luminance[idx + SIZE];
      hashBits += diff > 0 ? '1' : '0';
    }
  }
  
  return bitsToHex(hashBits);
}

/**
 * pHash - Perceptual Hash using DCT
 * 256-bit hash, good for detecting similar images
 */
async function computePHash(imageBuffer) {
  const SIZE = 32;
  
  // Resize to 32x32 grayscale
  const { data, info } = await sharp(imageBuffer)
    .resize(SIZE, SIZE, { fit: 'fill' })
    .grayscale()
    .raw()
    .toBuffer({ resolveWithObject: true });
  
  // Convert to 2D array
  const pixels = [];
  for (let y = 0; y < SIZE; y++) {
    const row = [];
    for (let x = 0; x < SIZE; x++) {
      row.push(data[y * SIZE * info.channels + x * info.channels]);
    }
    pixels.push(row);
  }
  
  // Apply DCT (simplified)
  const dct = applyDCT(pixels, SIZE);
  
  // Extract low-frequency components (top-left 8x8)
  let sum = 0;
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      sum += dct[y][x];
    }
  }
  const median = sum / 64;
  
  let hashBits = '';
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      hashBits += dct[y][x] > median ? '1' : '0';
    }
  }
  
  return bitsToHex(hashBits);
}

/**
 * dHash - Difference Hash
 * 64-bit hash, very fast, good for detecting minor changes
 */
async function computeDHash(imageBuffer) {
  const SIZE = 9;
  
  // Resize to 9x8 grayscale
  const { data, info } = await sharp(imageBuffer)
    .resize(SIZE, 8, { fit: 'fill' })
    .grayscale()
    .raw()
    .toBuffer({ resolveWithObject: true });
  
  // Compare adjacent pixels
  let hashBits = '';
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < SIZE - 1; x++) {
      const left = data[y * SIZE * info.channels + x * info.channels];
      const right = data[y * SIZE * info.channels + (x + 1) * info.channels];
      hashBits += left < right ? '1' : '0';
    }
  }
  
  return bitsToHex(hashBits);
}

/**
 * Simplified 2D DCT implementation
 */
function applyDCT(pixels, size) {
  const result = Array(size).fill(null).map(() => Array(size).fill(0));
  
  for (let u = 0; u < size; u++) {
    for (let v = 0; v < size; v++) {
      let sum = 0;
      for (let i = 0; i < size; i++) {
        for (let j = 0; j < size; j++) {
          sum += pixels[i][j] *
                 Math.cos((2 * i + 1) * u * Math.PI / (2 * size)) *
                 Math.cos((2 * j + 1) * v * Math.PI / (2 * size));
        }
      }
      result[u][v] = sum;
    }
  }
  
  return result;
}

/**
 * Convert binary string to hex
 */
function bitsToHex(bits) {
  let hex = '';
  for (let i = 0; i < bits.length; i += 4) {
    const chunk = bits.substr(i, 4).padEnd(4, '0');
    hex += parseInt(chunk, 2).toString(16);
  }
  return hex;
}

module.exports = {
  computePerceptualHashes,
  computePDQ,
  computePHash,
  computeDHash,
};
