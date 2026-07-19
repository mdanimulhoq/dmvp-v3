'use strict';

const { computePerceptualHashes, computePDQ, computePHash, computeDHash } = require('../src/utils/perceptualHasher');
const sharp = require('sharp');

describe('Perceptual Hasher (TDD v5 Phase 2 Step 2.2)', () => {
  let testImageBuffer;
  
  beforeAll(async () => {
    // Create a simple test image
    testImageBuffer = await sharp({
      create: {
        width: 100,
        height: 100,
        channels: 3,
        background: { r: 255, g: 0, b: 0 }
      }
    })
    .png()
    .toBuffer();
  });
  
  describe('computePerceptualHashes', () => {
    test('computes all three hashes', async () => {
      const hashes = await computePerceptualHashes(testImageBuffer);
      expect(hashes).toHaveProperty('pdq');
      expect(hashes).toHaveProperty('phash');
      expect(hashes).toHaveProperty('dhash');
      expect(typeof hashes.pdq).toBe('string');
      expect(typeof hashes.phash).toBe('string');
      expect(typeof hashes.dhash).toBe('string');
    });
    
    test('returns hex strings', async () => {
      const hashes = await computePerceptualHashes(testImageBuffer);
      expect(hashes.pdq).toMatch(/^[0-9a-f]+$/);
      expect(hashes.phash).toMatch(/^[0-9a-f]+$/);
      expect(hashes.dhash).toMatch(/^[0-9a-f]+$/);
    });
  });
  
  describe('computePDQ', () => {
    test('returns consistent hash for same image', async () => {
      const hash1 = await computePDQ(testImageBuffer);
      const hash2 = await computePDQ(testImageBuffer);
      expect(hash1).toBe(hash2);
    });
    
    test('returns hex string', async () => {
      const hash = await computePDQ(testImageBuffer);
      expect(hash).toMatch(/^[0-9a-f]+$/);
    });
  });
  
  describe('computePHash', () => {
    test('returns consistent hash for same image', async () => {
      const hash1 = await computePHash(testImageBuffer);
      const hash2 = await computePHash(testImageBuffer);
      expect(hash1).toBe(hash2);
    });
    
    test('returns hex string', async () => {
      const hash = await computePHash(testImageBuffer);
      expect(hash).toMatch(/^[0-9a-f]+$/);
    });
  });
  
  describe('computeDHash', () => {
    test('returns consistent hash for same image', async () => {
      const hash1 = await computeDHash(testImageBuffer);
      const hash2 = await computeDHash(testImageBuffer);
      expect(hash1).toBe(hash2);
    });
    
    test('returns hex string', async () => {
      const hash = await computeDHash(testImageBuffer);
      expect(hash).toMatch(/^[0-9a-f]+$/);
    });
  });
});
