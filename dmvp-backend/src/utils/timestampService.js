'use strict';
const https = require('https');
const crypto = require('crypto');
const TSA_URL = process.env.TSA_URL || 'https://freetsa.org/tsr';
const TSA_TIMEOUT_MS = 10000;

async function requestTimestampToken(sha256Hex) {
  try {
    const tsReq = buildTimestampRequest(sha256Hex);
    const response = await postTSA(tsReq);
    if (!response) return null;
    const tokenReference = `tsa:${crypto.createHash('sha256').update(response).digest('hex').substring(0, 32)}`;
    return { tokenReference, timestamp: new Date().toISOString(), tsaUrl: TSA_URL };
  } catch (err) {
    console.error('[TimestampService] TSA request failed:', err.message);
    return null;
  }
}

function buildTimestampRequest(sha256Hex) {
  const hashBytes = Buffer.from(sha256Hex, 'hex');
  const sha256OID = Buffer.from([0x60,0x86,0x48,0x01,0x65,0x03,0x04,0x02,0x01]);
  const algId = Buffer.concat([Buffer.from([0x30,0x0d]),Buffer.from([0x06,0x09]),sha256OID,Buffer.from([0x05,0x00])]);
  const imprintContent = Buffer.concat([algId,Buffer.from([0x04,hashBytes.length]),hashBytes]);
  const messageImprint = Buffer.concat([Buffer.from([0x30,imprintContent.length]),imprintContent]);
  const version = Buffer.from([0x02,0x01,0x01]);
  const reqContent = Buffer.concat([version,messageImprint]);
  return Buffer.concat([Buffer.from([0x30,reqContent.length]),reqContent]);
}

function postTSA(tsReq) {
  return new Promise((resolve, reject) => {
    const url = new URL(TSA_URL);
    const options = { hostname: url.hostname, port: url.port || 443, path: url.pathname, method: 'POST',
      headers: { 'Content-Type': 'application/timestamp-query', 'Content-Length': tsReq.length }, timeout: TSA_TIMEOUT_MS };
    const req = https.request(options, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => { res.statusCode === 200 ? resolve(Buffer.concat(chunks)) : reject(new Error(`TSA returned ${res.statusCode}`)); });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('TSA timeout')); });
    req.write(tsReq);
    req.end();
  });
}

async function getTimestamp(sha256Hex, mode = 'standard') {
  const timestamp = new Date().toISOString();
  if (mode === 'enhanced' || mode === 'high_assurance') {
    const token = await requestTimestampToken(sha256Hex);
    if (token) return { ...token, mode };
    console.warn('[TimestampService] TSA unavailable, falling back to standard');
  }
  return { tokenReference: null, timestamp, mode: 'standard' };
}

module.exports = { getTimestamp, requestTimestampToken };
