'use strict';

const crypto = require('crypto');
const {
  normalizeSpkiPublicKey,
  verifyEcdsaSha256Signature,
} = require('../src/utils/cryptoUtils');

function createP256KeyPair() {
  return crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    publicKeyEncoding: {
      type: 'spki',
      format: 'der',
    },
    privateKeyEncoding: {
      type: 'pkcs8',
      format: 'pem',
    },
  });
}

function signPayload(payloadBytes, privateKeyPem) {
  const signer = crypto.createSign('SHA256');
  signer.update(payloadBytes);
  signer.end();
  return signer.sign(privateKeyPem).toString('base64');
}

describe('cryptoUtils', () => {
  test('normalizeSpkiPublicKey accepts Android SPKI base64 public key', () => {
    const { publicKey } = createP256KeyPair();
    const publicKeyBase64 = publicKey.toString('base64');

    const normalized = normalizeSpkiPublicKey(publicKeyBase64);

    expect(normalized).toContain('-----BEGIN PUBLIC KEY-----');
    expect(normalized).toContain('-----END PUBLIC KEY-----');
  });

  test('verifyEcdsaSha256Signature returns true for valid P-256 signature', () => {
    const { publicKey, privateKey } = createP256KeyPair();
    const payload = Buffer.from('dmvp backend crypto verification', 'utf8');
    const signatureBase64 = signPayload(payload, privateKey);
    const publicKeyBase64 = publicKey.toString('base64');

    const verified = verifyEcdsaSha256Signature(
      payload,
      signatureBase64,
      publicKeyBase64
    );

    expect(verified).toBe(true);
  });

  test('verifyEcdsaSha256Signature returns false for modified payload', () => {
    const { publicKey, privateKey } = createP256KeyPair();
    const originalPayload = Buffer.from('original payload', 'utf8');
    const modifiedPayload = Buffer.from('modified payload', 'utf8');
    const signatureBase64 = signPayload(originalPayload, privateKey);
    const publicKeyBase64 = publicKey.toString('base64');

    const verified = verifyEcdsaSha256Signature(
      modifiedPayload,
      signatureBase64,
      publicKeyBase64
    );

    expect(verified).toBe(false);
  });

  test('verifyEcdsaSha256Signature returns false for malformed public key', () => {
    const payload = Buffer.from('payload', 'utf8');

    const verified = verifyEcdsaSha256Signature(
      payload,
      'not-base64',
      'not-a-public-key'
    );

    expect(verified).toBe(false);
  });
});
