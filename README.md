# DMVP v3 — Decentralized Media Verification Protocol

DMVP is a zero-storage, proof-based media verification system for photos and videos. It stores cryptographic proof, fingerprints, signatures, timestamps, device trust metadata, and verification records, but does not require storing original user media.

## Project structure

- `dmvp-android/` — Android app for capture, device key generation, media fingerprinting, evidence registration, verification, and search.
- `dmvp-backend/` — Node.js/Express API, Prisma/PostgreSQL registry, device lifecycle, evidence registration, verification, search, ownership, and audit logic.
- `.github/workflows/build-apk.yml` — CI build/test workflow for Android APK.

## Core goals

- Verify authenticity without mandatory cloud custody of originals.
- Separate integrity, provenance, similarity, and evidence quality.
- Preserve privacy with minimal/default metadata collection.
- Support signed, replay-resistant, idempotent evidence registration.
- Produce explainable, auditable verification results.

## Main concepts

- **CEE**: Canonical Evidence Envelope registered for each media item.
- **Integrity**: exact SHA-256 file equivalence.
- **Provenance**: device-signed origin and device trust lineage.
- **Similarity**: robust matching for transformed/derivative media.
- **Trust Tier**: confidence level of device key and attestation.
- **Lineage Transition**: signed key rotation/recovery/revocation trail.

## Android requirements

- Current `minSdk = 26`, so Android 8+ is technically supported; target compatibility should include Android 11+ through latest Android versions.
- Media permissions must cover:
  - Android 11/12: `READ_EXTERNAL_STORAGE`
  - Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `POST_NOTIFICATIONS`
  - Android 14+: `READ_MEDIA_VISUAL_USER_SELECTED`
- Device keys use Android Keystore with ECDSA P-256 / SHA256withECDSA.

## Evidence registration flow

1. Select/capture photo or video.
2. Compute original SHA-256.
3. Generate robust fingerprint profile.
4. Generate/get Android device signing key.
5. Register/sync device key with backend.
6. Build CEE.
7. Canonically serialize unsigned payload.
8. Sign request with device private key.
9. Send `POST /api/v1/evidence` with:
   - `X-DMVP-Signature`
   - `X-DMVP-Nonce`
   - `X-DMVP-Timestamp`
   - `X-DMVP-Device-Key-Id`
   - `Idempotency-Key`

## Backend API groups

- `GET /health` — service/database health.
- `GET /ready` — readiness.
- `GET /api/v1` — API metadata.
- `POST /api/v1/evidence` — register CEE.
- `GET /api/v1/evidence/{id}` — fetch evidence.
- `GET /api/v1/evidence/by-hash/{sha256}` — exact hash lookup.
- `POST /api/v1/verify` — multi-axis verification.
- `GET /api/v1/verify/policy` — active verification policy.
- `POST /api/v1/search` — evidence search.
- `POST /api/v1/devices/register` — device key registration.
- Device rotation/revoke/recovery and ownership endpoints are also included.

## Security and privacy requirements

- TLS for transport.
- Signed, nonce-bound, timestamp-bound registration writes.
- Idempotent registration handling.
- Optional metadata controlled by privacy flags.
- Device revocation must affect future trust decisions without deleting historical records.
- Audit logs should be tamper-evident or append-only.

## Verification result model

Verification should return separate, explainable outputs:

- Integrity verdict
- Provenance verdict
- Similarity verdict
- Evidence quality verdict
- Warnings
- Transform indicators
- Matched evidence references
- Algorithm/policy versions

## Deployment notes

- Backend is designed for Render deployment with PostgreSQL.
- Android app currently points to the Render API base URL in `RetrofitClient`.
- After backend fixes merge, redeploy Render before testing the installed APK.
- After Android fixes merge, build and install a fresh APK; old installed apps do not update automatically.

## Current known focus areas

- Photo registration errors should be debugged from Android logcat plus backend error body.
- Common failure points:
  - missing/invalid `Idempotency-Key`
  - device key missing on backend
  - signature header mismatch
  - Android media permission behavior on newer Android versions
  - Render backend not redeployed after code merge
