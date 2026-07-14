'use strict';

function canonicalize(value, options = {}) {
  const excludeSignature = options.excludeSignature === true;
  const sorted = sortValue(value, excludeSignature);
  return JSON.stringify(sorted);
}

function canonicalizeToUtf8(value, options = {}) {
  return Buffer.from(canonicalize(value, options), 'utf8');
}

function sortValue(value, excludeSignature) {
  if (Array.isArray(value)) {
    return value.map((item) => sortValue(item, excludeSignature));
  }

  if (value !== null && typeof value === 'object') {
    const sorted = {};

    Object.keys(value)
      .filter((key) => !(excludeSignature && key === 'signature'))
      .sort()
      .forEach((key) => {
        sorted[key] = sortValue(value[key], excludeSignature);
      });

    return sorted;
  }

  return value;
}

module.exports = {
  canonicalize,
  canonicalizeToUtf8,
  sortValue,
};
