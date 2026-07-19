'use strict';

const {
  computeBLAKE3,
  computeDualHash,
  computeSHA256Sync,
} = require('../src/utils/hashUtils');

describe('Dual Hash (TDD v5 Phase 1 Step 1.3)', () => {
  describe('computeBLAKE3', () => {
    test('computes BLAKE3 hash of string', async () => {
      const hash = await computeBLAKE3('hello world');
      expect(hash).toMatch(/^[0-9a-f]{64}$/);
    });

    test('computes BLAKE3 hash of Buffer', async () => {
      const hash = await computeBLAKE3(Buffer.from('hello world'));
      expect(hash).toMatch(/^[0-9a-f]{64}$/);
    });

    test('same input produces same hash', async () => {
      const hash1 = await computeBLAKE3('test data');
      const hash2 = await computeBLAKE3('test data');
      expect(hash1).toBe(hash2);
    });

    test('different input produces different hash', async () => {
      const hash1 = await computeBLAKE3('data1');
      const hash2 = await computeBLAKE3('data2');
      expect(hash1).not.toBe(hash2);
    });

    test('throws for invalid input', async () => {
      await expect(computeBLAKE3(123)).rejects.toThrow(TypeError);
      await expect(computeBLAKE3(null)).rejects.toThrow(TypeError);
    });
  });

  describe('computeDualHash', () => {
    test('returns both sha256 and blake3', async () => {
      const result = await computeDualHash('test data');
      expect(result).toHaveProperty('sha256');
      expect(result).toHaveProperty('blake3');
      expect(result.sha256).toMatch(/^[0-9a-f]{64}$/);
      expect(result.blake3).toMatch(/^[0-9a-f]{64}$/);
    });

    test('sha256 matches standalone SHA-256', async () => {
      const data = 'verify consistency';
      const dual = await computeDualHash(data);
      const standalone = computeSHA256Sync(data);
      expect(dual.sha256).toBe(standalone);
    });

    test('blake3 matches standalone BLAKE3', async () => {
      const data = 'verify consistency';
      const dual = await computeDualHash(data);
      const standalone = await computeBLAKE3(data);
      expect(dual.blake3).toBe(standalone);
    });

    test('works with Buffer input', async () => {
      const result = await computeDualHash(Buffer.from('buffer test'));
      expect(result.sha256).toMatch(/^[0-9a-f]{64}$/);
      expect(result.blake3).toMatch(/^[0-9a-f]{64}$/);
    });
  });
});
