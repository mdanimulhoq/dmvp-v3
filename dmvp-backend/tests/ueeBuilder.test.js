'use strict';

const {
  buildUEEFromCEE,
  validateUEE,
  extractCEEFromUEE,
} = require('../src/utils/ueeBuilder');

describe('UEE v5.0 Builder (TDD v5 Phase 1 Step 1.8)', () => {
  const mockCEE = {
    protocol_version: 'dmvp-v3.0.0',
    media_type: 'image',
    sha256_original: 'a'.repeat(64),
    canonical_media_hash: 'b'.repeat(64),
    robust_fingerprint_profile: {
      phash: 'c'.repeat(16),
      dhash: 'd'.repeat(16),
    },
    signer_device_key_id: 'device-123',
    signer_public_key_reference: 'key-ref-456',
    signature_algorithm: 'SHA256withECDSA',
    registration_server_time: '2026-07-18T00:00:00Z',
    privacy_flags: { gps: false, exif: false, device_info: true },
    verification_policy_version: 'policy-v3.0.0',
    client_app_version: '3.0.0',
    audit_reference: 'audit-789',
  };

  describe('buildUEEFromCEE', () => {
    test('creates UEE v5.0 with correct version', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.version).toBe('5.0');
    });

    test('includes dual hashes', () => {
      const uee = buildUEEFromCEE(mockCEE, {
        blake3Hash: 'e'.repeat(64),
      });
      expect(uee.content_hashes.sha256).toBe(mockCEE.sha256_original);
      expect(uee.content_hashes.blake3).toBe('e'.repeat(64));
      expect(uee.content_hashes.canonical).toBe(mockCEE.canonical_media_hash);
    });

    test('includes C2PA signals', () => {
      const c2paResult = {
        c2pa_status: 'verified',
        c2pa_manifest_hash: 'f'.repeat(64),
        c2pa_manifest: { claim_generator: 'Samsung' },
      };
      const uee = buildUEEFromCEE(mockCEE, { c2paResult });
      expect(uee.provenance_signals.c2pa_status).toBe('verified');
      expect(uee.provenance_signals.c2pa_manifest_hash).toBe('f'.repeat(64));
      expect(uee.provenance_signals.c2pa_claim_generator).toBe('Samsung');
    });

    test('defaults C2PA to absent when not provided', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.provenance_signals.c2pa_status).toBe('absent');
    });

    test('includes fingerprint layers', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.fingerprint_layers).toBeDefined();
      expect(uee.fingerprint_layers.L1_exact).toBeDefined();
      expect(uee.fingerprint_layers.L3_perceptual).toBeDefined();
      expect(uee.fingerprint_layers.L8_ai_derivative).toBeDefined();
    });

    test('preserves CEE in cee_compat for backward compatibility', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.cee_compat).toEqual(mockCEE);
    });

    test('includes signer information', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.signer.device_key_id).toBe('device-123');
      expect(uee.signer.public_key_reference).toBe('key-ref-456');
      expect(uee.signer.signature_algorithm).toBe('SHA256withECDSA');
    });

    test('includes metadata', () => {
      const uee = buildUEEFromCEE(mockCEE);
      expect(uee.metadata.protocol_version).toBe('dmvp-v3.0.0');
      expect(uee.metadata.policy_version).toBe('policy-v3.0.0');
      expect(uee.metadata.client_app_version).toBe('3.0.0');
      expect(uee.metadata.audit_reference).toBe('audit-789');
    });

    test('includes UAID when provided', () => {
      const uee = buildUEEFromCEE(mockCEE, {
        uaid: 'uaid_5_t1_01ARZ3NDEKTSV4RRFFQ69G5FAV',
        tenantId: 't1',
      });
      expect(uee.uaid).toBe('uaid_5_t1_01ARZ3NDEKTSV4RRFFQ69G5FAV');
      expect(uee.tenant_id).toBe('t1');
    });
  });

  describe('validateUEE', () => {
    test('validates correct UEE', () => {
      const uee = buildUEEFromCEE(mockCEE);
      const result = validateUEE(uee);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    test('fails validation for missing version', () => {
      const uee = { ...buildUEEFromCEE(mockCEE), version: '4.0' };
      const result = validateUEE(uee);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Invalid or missing UEE version');
    });

    test('fails validation for missing sha256', () => {
      const uee = buildUEEFromCEE(mockCEE);
      uee.content_hashes.sha256 = null;
      const result = validateUEE(uee);
      expect(result.valid).toBe(false);
      expect(result.errors.some(e => e.includes('sha256'))).toBe(true);
    });

    test('fails validation for L8 without FPR/FNR', () => {
      const uee = buildUEEFromCEE(mockCEE);
      uee.fingerprint_layers.L8_ai_derivative.status = 'possible_ai_derivative';
      uee.fingerprint_layers.L8_ai_derivative.fpr = null;
      const result = validateUEE(uee);
      expect(result.valid).toBe(false);
      expect(result.errors.some(e => e.includes('false_positive_rate'))).toBe(true);
    });
  });

  describe('extractCEEFromUEE', () => {
    test('extracts original CEE from UEE', () => {
      const uee = buildUEEFromCEE(mockCEE);
      const extracted = extractCEEFromUEE(uee);
      expect(extracted).toEqual(mockCEE);
    });

    test('returns null for UEE without cee_compat', () => {
      const uee = { version: '5.0' };
      const extracted = extractCEEFromUEE(uee);
      expect(extracted).toBeNull();
    });

    test('returns null for null input', () => {
      expect(extractCEEFromUEE(null)).toBeNull();
    });
  });
});
