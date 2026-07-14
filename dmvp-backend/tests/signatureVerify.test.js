'use strict';

const {
  checkAndConsumeNonce,
  validateTimestamp,
  REPLAY_WINDOW_MS,
} = require('../src/middleware/signatureVerify');

describe('Step 3.6 — Replay protection (NFR-SC-02)', () => {
  describe('REPLAY_WINDOW_MS', () => {
    test('is set to 10 minutes', () => {
      expect(REPLAY_WINDOW_MS).toBe(10 * 60 * 1000);
    });
  });

  describe('validateTimestamp', () => {
    test('accepts current server time (within window)', () => {
      const nowIso = new Date().toISOString();
      const result = validateTimestamp(nowIso);
      expect(result.valid).toBe(true);
    });

    test('rejects stale timestamp older than the replay window', () => {
      const stale = new Date(Date.now() - (REPLAY_WINDOW_MS + 60000))
        .toISOString();
      const result = validateTimestamp(stale);
      expect(result.valid).toBe(false);
      expect(result.reason).toMatch(/replay window/i);
    });

    test('rejects future timestamp beyond the replay window', () => {
      const future = new Date(Date.now() + (REPLAY_WINDOW_MS + 60000))
        .toISOString();
      const result = validateTimestamp(future);
      expect(result.valid).toBe(false);
    });

    test('rejects malformed timestamp strings', () => {
      const result = validateTimestamp('not-a-timestamp');
      expect(result.valid).toBe(false);
      expect(result.reason).toMatch(/iso/i);
    });
  });

  describe('checkAndConsumeNonce', () => {
    test('fresh nonce is accepted on first use', () => {
      const deviceKeyId = 'device-test-fresh-' + Date.now();
      const nonce = 'nonce-fresh-' + Math.random().toString(36).slice(2);
      expect(checkAndConsumeNonce(deviceKeyId, nonce)).toBe(true);
    });

    test('same nonce for same device is rejected on replay', () => {
      const deviceKeyId = 'device-test-replay-' + Date.now();
      const nonce = 'nonce-replay-' + Math.random().toString(36).slice(2);
      expect(checkAndConsumeNonce(deviceKeyId, nonce)).toBe(true);
      expect(checkAndConsumeNonce(deviceKeyId, nonce)).toBe(false);
    });

    test('same nonce with a different device is still accepted', () => {
      const nonce = 'nonce-shared-' + Math.random().toString(36).slice(2);
      const deviceA = 'device-A-' + Date.now();
      const deviceB = 'device-B-' + Date.now();
      expect(checkAndConsumeNonce(deviceA, nonce)).toBe(true);
      expect(checkAndConsumeNonce(deviceB, nonce)).toBe(true);
    });

    test('different nonces on same device are both accepted', () => {
      const deviceKeyId = 'device-test-multi-' + Date.now();
      const nonce1 = 'n1-' + Math.random().toString(36).slice(2);
      const nonce2 = 'n2-' + Math.random().toString(36).slice(2);
      expect(checkAndConsumeNonce(deviceKeyId, nonce1)).toBe(true);
      expect(checkAndConsumeNonce(deviceKeyId, nonce2)).toBe(true);
    });
  });
});
