-- TDD v5 Phase 1 Step 1.2: Database Schema Update
-- Add dual hash (BLAKE3), C2PA manifest hash, and post-quantum signature columns
-- Migration: 20260718_phase1_dual_hash_c2pa_pq

-- Evidence table: Add BLAKE3 hash and C2PA manifest hash
ALTER TABLE "evidences" ADD COLUMN IF NOT EXISTS "blake3_hash" CHAR(64);
ALTER TABLE "evidences" ADD COLUMN IF NOT EXISTS "c2pa_manifest_hash" VARCHAR(255);

-- OwnershipClaim table: Add post-quantum signature columns (Phase 4 preparation)
ALTER TABLE "ownership_claims" ADD COLUMN IF NOT EXISTS "pq_sig" TEXT;
ALTER TABLE "ownership_claims" ADD COLUMN IF NOT EXISTS "pq_algo" VARCHAR(64);
ALTER TABLE "ownership_claims" ADD COLUMN IF NOT EXISTS "pq_pubkey" TEXT;

-- Indexes for new columns
CREATE INDEX IF NOT EXISTS "idx_evidence_blake3_hash" ON "evidences"("blake3_hash");
CREATE INDEX IF NOT EXISTS "idx_evidence_c2pa_manifest_hash" ON "evidences"("c2pa_manifest_hash");

-- Comments for documentation
COMMENT ON COLUMN "evidences"."blake3_hash" IS 'TDD v5: BLAKE3 hash - 3-5x faster than SHA-256, used as internal primary key for fast re-verification';
COMMENT ON COLUMN "evidences"."c2pa_manifest_hash" IS 'TDD v5: C2PA Content Credentials manifest hash - EU AI Act Article 50 compliance (August 2026)';
COMMENT ON COLUMN "ownership_claims"."pq_sig" IS 'TDD v5 Phase 4: Post-quantum signature (ML-DSA-65 / FIPS 204)';
COMMENT ON COLUMN "ownership_claims"."pq_algo" IS 'TDD v5 Phase 4: Post-quantum algorithm identifier (e.g., ML-DSA-65)';
COMMENT ON COLUMN "ownership_claims"."pq_pubkey" IS 'TDD v5 Phase 4: Post-quantum public key';
