-- Phase 3 Step 3.1: pgvector Setup
-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- Create model_registry table
CREATE TABLE IF NOT EXISTS "model_registry" (
    "model_id" TEXT NOT NULL,
    "model_name" TEXT NOT NULL,
    "vector_type" TEXT NOT NULL,
    "dimensions" INTEGER NOT NULL,
    "version" TEXT NOT NULL,
    "description" TEXT,
    "is_active" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "model_registry_pkey" PRIMARY KEY ("model_id")
);

-- Create unique index on model_name
CREATE UNIQUE INDEX IF NOT EXISTS "model_registry_model_name_key" ON "model_registry"("model_name");

-- Create fingerprint_vectors table with vector column
CREATE TABLE IF NOT EXISTS "fingerprint_vectors" (
    "vector_id" TEXT NOT NULL,
    "evidence_id" TEXT NOT NULL,
    "model_id" TEXT NOT NULL,
    "vector_type" TEXT NOT NULL,
    "vector" vector(2048), -- Default to 2048 dimensions (SigLIP 2)
    "dimensions" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "fingerprint_vectors_pkey" PRIMARY KEY ("vector_id")
);

-- Create unique constraint on evidence_id + model_id
CREATE UNIQUE INDEX IF NOT EXISTS "uq_fingerprint_vector_evidence_model" ON "fingerprint_vectors"("evidence_id", "model_id");

-- Create indexes
CREATE INDEX IF NOT EXISTS "idx_fingerprint_vector_type" ON "fingerprint_vectors"("vector_type");
CREATE INDEX IF NOT EXISTS "idx_fingerprint_vector_evidence" ON "fingerprint_vectors"("evidence_id");

-- Create HNSW index for vector similarity search
-- Using cosine distance operator <=> for similarity search
CREATE INDEX IF NOT EXISTS "idx_fingerprint_vector_hnsw" 
ON "fingerprint_vectors" 
USING hnsw ("vector" vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Seed model_registry with default models
INSERT INTO "model_registry" ("model_id", "model_name", "vector_type", "dimensions", "version", "description", "is_active")
VALUES 
    (gen_random_uuid(), 'google/siglip-2-large-patch16-512', 'siglip', 2048, '1.0.0', 'SigLIP 2 Large - 1.1B params, 2048d output, multilingual', true),
    (gen_random_uuid(), 'facebookresearch/sscd-copy-detection', 'sscd', 512, '1.0.0', 'SSCD Copy Detection - exact/near copy detection', true),
    (gen_random_uuid(), 'laion/clap-htsat-fused', 'clap', 512, '1.0.0', 'LAION-CLAP - audio embedding model', true),
    (gen_random_uuid(), 'OpenAI/codexembed-v1', 'codexembed', 1536, '1.0.0', 'CodeXEmbed - code semantic embedding', true)
ON CONFLICT ("model_name") DO NOTHING;

-- Add foreign key constraints
ALTER TABLE "fingerprint_vectors" 
ADD CONSTRAINT "fingerprint_vectors_evidence_id_fkey" 
FOREIGN KEY ("evidence_id") REFERENCES "evidences"("evidence_id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "fingerprint_vectors" 
ADD CONSTRAINT "fingerprint_vectors_model_id_fkey" 
FOREIGN KEY ("model_id") REFERENCES "model_registry"("model_id") ON DELETE RESTRICT ON UPDATE CASCADE;
