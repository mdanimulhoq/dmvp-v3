'use strict';

const {
  generateUAID,
  parseUAID,
  isValidUAID,
  extractTimestamp,
} = require('../src/utils/uaidGenerator');

describe('UAID Generator', () => {
  describe('generateUAID', () => {
    test('generates valid UAID with correct format', () => {
      const tenant = 't1';
      const uaid = generateUAID(tenant);

      expect(uaid).toMatch(/^uaid_5_t1_[0-9A-HJKMNP-TV-Z]{26}$/i);
    });

    test('generates unique UAIDs on consecutive calls', () => {
      const tenant = 'tenant1';
      const uaid1 = generateUAID(tenant);
      const uaid2 = generateUAID(tenant);

      expect(uaid1).not.toBe(uaid2);
    });

    test('handles tenant with underscores and spaces', () => {
      const uaid = generateUAID('tenant_abc 123');
      
      expect(uaid).toMatch(/^uaid_5_tenantabc123_[0-9A-HJKMNP-TV-Z]{26}$/i);
    });

    test('converts tenant to lowercase', () => {
      const uaid = generateUAID('TENANT_ABC');
      
      expect(uaid).toMatch(/^uaid_5_tenantabc_[0-9A-HJKMNP-TV-Z]{26}$/i);
    });

    test('throws error for empty tenantId', () => {
      expect(() => generateUAID('')).toThrow('tenantId must be a non-empty string');
    });

    test('throws error for null tenantId', () => {
      expect(() => generateUAID(null)).toThrow('tenantId must be a non-empty string');
    });

    test('throws error for undefined tenantId', () => {
      expect(() => generateUAID(undefined)).toThrow('tenantId must be a non-empty string');
    });

    test('throws error for non-string tenantId', () => {
      expect(() => generateUAID(123)).toThrow('tenantId must be a non-empty string');
    });

    test('throws error for tenant with only separators', () => {
      expect(() => generateUAID('___')).toThrow('tenantId cannot be empty or contain only separators');
    });
  });

  describe('parseUAID', () => {
    test('parses valid UAID correctly', () => {
      const uaid = 'uaid_5_t1_01ARZ3NDEKTSV4RRFFQ69G5FAV';
      const parsed = parseUAID(uaid);

      expect(parsed).toEqual({
        prefix: 'uaid',
        version: '5',
        tenant: 't1',
        ulid: '01ARZ3NDEKTSV4RRFFQ69G5FAV',
      });
    });

    test('returns null for invalid UAID format', () => {
      expect(parseUAID('invalid')).toBeNull();
      expect(parseUAID('uaid_5_t1')).toBeNull();
      expect(parseUAID('uaid_6_t1_01ARZ3NDEKTSV4RRFFQ69G5FAV')).toBeNull();
      expect(parseUAID('other_5_t1_01ARZ3NDEKTSV4RRFFQ69G5FAV')).toBeNull();
    });

    test('returns null for null or undefined', () => {
      expect(parseUAID(null)).toBeNull();
      expect(parseUAID(undefined)).toBeNull();
    });

    test('returns null for empty string', () => {
      expect(parseUAID('')).toBeNull();
    });

    test('returns null for UAID with invalid ULID characters', () => {
      // ULID uses Crockford's base32, excludes I, L, O, U
      const invalidUlid = '01ARZ3NDEKTSV4RRFFQ69G5FAI'; // Contains 'I'
      const uaid = `uaid_5_t1_${invalidUlid}`;
      
      expect(parseUAID(uaid)).toBeNull();
    });
  });

  describe('isValidUAID', () => {
    test('returns true for valid UAID', () => {
      const uaid = generateUAID('t1');
      expect(isValidUAID(uaid)).toBe(true);
    });

    test('returns false for invalid UAID', () => {
      expect(isValidUAID('invalid')).toBe(false);
      expect(isValidUAID('')).toBe(false);
      expect(isValidUAID(null)).toBe(false);
    });
  });

  describe('extractTimestamp', () => {
    test('extracts timestamp from valid UAID', () => {
      const before = Date.now();
      const uaid = generateUAID('t1');
      const after = Date.now();

      const timestamp = extractTimestamp(uaid);
      
      expect(timestamp).toBeInstanceOf(Date);
      expect(timestamp.getTime()).toBeGreaterThanOrEqual(before - 1000);
      expect(timestamp.getTime()).toBeLessThanOrEqual(after + 1000);
    });

    test('returns null for invalid UAID', () => {
      expect(extractTimestamp('invalid')).toBeNull();
      expect(extractTimestamp(null)).toBeNull();
    });

    test('extracted timestamp is recent', () => {
      const uaid = generateUAID('t1');
      const timestamp = extractTimestamp(uaid);
      const now = Date.now();

      // Should be within last 5 seconds
      expect(now - timestamp.getTime()).toBeLessThan(5000);
    });
  });

  describe('UAID format compliance', () => {
    test('UAID follows TDD v5 specification', () => {
      const uaid = generateUAID('t1');
      const parts = uaid.split('_');

      expect(parts[0]).toBe('uaid');
      expect(parts[1]).toBe('5');
      expect(parts[2]).toBe('t1');
      expect(parts[3]).toHaveLength(26); // ULID is 26 characters
    });

    test('multiple UAIDs are time-sortable', () => {
      const uaids = [];
      for (let i = 0; i < 10; i++) {
        uaids.push(generateUAID('t1'));
        // Small delay to ensure different timestamps
        const start = Date.now();
        while (Date.now() - start < 2) {
          // Wait 2ms
        }
      }

      // UAIDs should be in ascending order (time-sortable)
      const sorted = [...uaids].sort();
      expect(sorted).toEqual(uaids);
    });
  });
});
