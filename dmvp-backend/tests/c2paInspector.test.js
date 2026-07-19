'use strict';

const {
  inspectC2paManifest,
  computeC2paManifestHash,
} = require('../src/utils/c2paInspector');

describe('C2PA Inspector (TDD v5 Phase 1 Step 1.5)', () => {
  describe('inspectC2paManifest', () => {
    test('returns absent for empty buffer', async () => {
      const result = await inspectC2paManifest(Buffer.alloc(0), 'image/jpeg');
      expect(result.c2pa_status).toBe('absent');
      expect(result.c2pa_manifest).toBeNull();
      expect(result.c2pa_verified).toBe(false);
    });

    test('returns absent for non-C2PA image', async () => {
      // Create a minimal JPEG without C2PA
      const jpegHeader = Buffer.from([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46,
        0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
      ]);
      const result = await inspectC2paManifest(jpegHeader, 'image/jpeg');
      expect(result.c2pa_status).toBe('absent');
    });

    test('returns valid structure with three possible states', async () => {
      const result = await inspectC2paManifest(Buffer.alloc(100), 'image/jpeg');
      expect(result).toHaveProperty('c2pa_status');
      expect(['verified', 'present_unverified', 'absent']).toContain(result.c2pa_status);
    });
  });

  describe('computeC2paManifestHash', () => {
    test('returns null for null manifest', () => {
      expect(computeC2paManifestHash(null)).toBeNull();
    });

    test('computes SHA-256 hash of manifest', () => {
      const manifest = { claim_generator: 'test', format: 'image/jpeg' };
      const hash = computeC2paManifestHash(manifest);
      expect(hash).toMatch(/^[0-9a-f]{64}$/);
    });

    test('same manifest produces same hash', () => {
      const manifest = { claim_generator: 'test' };
      const hash1 = computeC2paManifestHash(manifest);
      const hash2 = computeC2paManifestHash(manifest);
      expect(hash1).toBe(hash2);
    });
  });
});
