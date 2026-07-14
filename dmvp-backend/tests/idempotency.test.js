'use strict';

const {
  computeRequestHash,
  evaluateExistingRecord,
  getIdempotencyKey,
  isValidIdempotencyKey,
} = require('../src/utils/idempotency');

describe('idempotency utils', () => {
  test('getIdempotencyKey reads Idempotency-Key header case-normalized by Express', () => {
    const req = {
      headers: {
        'idempotency-key': ' 11111111-1111-4111-8111-111111111111 ',
      },
    };

    expect(getIdempotencyKey(req)).toBe('11111111-1111-4111-8111-111111111111');
  });

  test('isValidIdempotencyKey accepts UUID and rejects non-UUID', () => {
    expect(isValidIdempotencyKey('11111111-1111-4111-8111-111111111111')).toBe(true);
    expect(isValidIdempotencyKey('same-file-sha256')).toBe(false);
  });

  test('computeRequestHash is stable for same body with different key order', () => {
    const first = {
      z: 1,
      a: {
        b: true,
        a: 'first',
      },
    };

    const second = {
      a: {
        a: 'first',
        b: true,
      },
      z: 1,
    };

    expect(computeRequestHash(first)).toBe(computeRequestHash(second));
  });

  test('computeRequestHash changes when body changes', () => {
    expect(computeRequestHash({ a: 1 })).not.toBe(computeRequestHash({ a: 2 }));
  });

  test('evaluateExistingRecord returns replay only for same scope and same body hash with stored response', () => {
    const existing = {
      scope: 'POST /api/v1/evidence',
      requestHash: 'a'.repeat(64),
      responseCode: 201,
      responseBody: { evidenceId: 'evidence-test' },
    };

    expect(
      evaluateExistingRecord(existing, 'POST /api/v1/evidence', 'a'.repeat(64)).action
    ).toBe('replay');

    expect(
      evaluateExistingRecord(existing, 'POST /api/v1/evidence', 'b'.repeat(64)).action
    ).toBe('conflict');
  });
});
