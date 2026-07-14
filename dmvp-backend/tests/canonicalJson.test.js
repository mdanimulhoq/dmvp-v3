'use strict';

const {
  canonicalize,
  canonicalizeToUtf8,
} = require('../src/utils/canonicalJson');

describe('canonicalJson', () => {
  test('canonicalize sorts keys recursively and removes whitespace', () => {
    const payload = {
      z: 1,
      a: {
        b: true,
        a: 'first',
      },
      m: [
        {
          d: 4,
          c: 3,
        },
      ],
    };

    expect(canonicalize(payload)).toBe(
      '{"a":{"a":"first","b":true},"m":[{"c":3,"d":4}],"z":1}'
    );
  });

  test('canonicalize excludes signature when requested', () => {
    const payload = {
      z: 'last',
      signature: 'base64-signature',
      a: 'first',
    };

    expect(canonicalize(payload, { excludeSignature: true })).toBe(
      '{"a":"first","z":"last"}'
    );
  });

  test('canonicalizeToUtf8 returns UTF-8 bytes of canonical JSON', () => {
    const payload = {
      b: 'বাংলা',
      a: 'dmvp',
    };

    const canonical = canonicalize(payload);
    const bytes = canonicalizeToUtf8(payload);

    expect(bytes.equals(Buffer.from(canonical, 'utf8'))).toBe(true);
    expect(canonical).toBe('{"a":"dmvp","b":"বাংলা"}');
  });
});
