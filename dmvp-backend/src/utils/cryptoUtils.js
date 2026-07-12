'use strict';

const crypto = require('crypto');

const PEM_PUBLIC_KEY_HEADER = '-----BEGIN PUBLIC KEY-----';

function normalizeSpkiPublicKey(publicKey) {
  if (typeof publicKey !== 'string' || publicKey.trim().length === 0) {
    throw new Error('public_key must be a non-empty string');
  }

  const trimmed = publicKey.trim();

  if (trimmed.includes(PEM_PUBLIC_KEY_HEADER)) {
    const keyObject = crypto.createPublicKey(trimmed);
    assertP256PublicKey(keyObject);
    return keyObject.export({ type: 'spki', format: 'pem' });
  }

  const der = Buffer.from(trimmed, 'base64');
  if (der.length === 0) {
    throw new Error('public_key must be valid SPKI base64');
  }

  const keyObject = crypto.createPublicKey({
    key: der,
    format: 'der',
    type: 'spki',
  });

  assertP256PublicKey(keyObject);
  return keyObject.export({ type: 'spki', format: 'pem' });
}

function assertP256PublicKey(keyObject) {
  const namedCurve = keyObject.asymmetricKeyDetails?.namedCurve;
  const isP256 =
    keyObject.asymmetricKeyType === 'ec' &&
    ['prime256v1', 'secp256r1', 'P-256'].includes(namedCurve);

  if (!isP256) {
    throw new Error('public_key must be an ECDSA P-256 SPKI public key');
  }
}

function verifyEcdsaSha256Signature(payloadBytes, signatureBase64, publicKey) {
  try {
    const canonicalBytes = Buffer.isBuffer(payloadBytes)
      ? payloadBytes
      : Buffer.from(payloadBytes);

    const signatureBytes = Buffer.from(signatureBase64, 'base64');
    const publicKeyPem = normalizeSpkiPublicKey(publicKey);

    const verifier = crypto.createVerify('SHA256');
    verifier.update(canonicalBytes);
    verifier.end();

    return verifier.verify(publicKeyPem, signatureBytes);
  } catch (error) {
    return false;
  }
}

module.exports = {
  normalizeSpkiPublicKey,
  verifyEcdsaSha256Signature,
};
