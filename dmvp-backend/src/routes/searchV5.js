/**
 * Phase 3 Step 3.4: Cross-Modal Search API
 * Technology: Node.js, pgvector cosine similarity query
 * Endpoint: POST /v5/search/cross-modal
 * 
 * Supports:
 * - Text to image search
 * - Image to similar image search
 * - Audio to similar audio search
 */

const express = require('express');
const router = express.Router();
const { PrismaClient } = require('@prisma/client');
const axios = require('axios');

const prisma = new PrismaClient();

// Embedding service URLs
const EMBEDDING_SERVICE_URL = process.env.EMBEDDING_SERVICE_URL || 'http://localhost:8004';
const SSCD_SERVICE_URL = process.env.SSCD_SERVICE_URL || 'http://localhost:8005';

/**
 * POST /v5/search/cross-modal
 * 
 * Cross-modal search using vector similarity
 * 
 * Request body:
 * {
 *   "query_type": "text" | "image" | "audio",
 *   "query": "text string" | image file | audio file,
 *   "vector_type": "siglip" | "sscd" | "clap",
 *   "limit": 10,
 *   "threshold": 0.7
 * }
 */
router.post('/cross-modal', async (req, res) => {
  try {
    const { query_type, query, vector_type = 'siglip', limit = 10, threshold = 0.7 } = req.body;

    if (!query_type || !query) {
      return res.status(400).json({
        error: 'Missing required fields: query_type, query'
      });
    }

    let queryEmbedding;
    let dimensions;

    // Generate query embedding based on type
    if (query_type === 'text') {
      // Text to embedding via SigLIP 2
      const response = await axios.post(`${EMBEDDING_SERVICE_URL}/embed/text`, {
        text: query
      });
      queryEmbedding = response.data.embedding;
      dimensions = response.data.dimensions;
    } else if (query_type === 'image') {
      // Image to embedding via SigLIP 2 or SSCD
      const serviceUrl = vector_type === 'sscd' ? SSCD_SERVICE_URL : EMBEDDING_SERVICE_URL;
      const endpoint = vector_type === 'sscd' ? '/embed' : '/embed/image';
      
      const formData = new FormData();
      formData.append('file', query);
      
      const response = await axios.post(`${serviceUrl}${endpoint}`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      });
      queryEmbedding = response.data.embedding;
      dimensions = response.data.dimensions;
    } else if (query_type === 'audio') {
      // Audio to embedding via CLAP (placeholder)
      // In production, call CLAP service
      return res.status(501).json({
        error: 'Audio search not yet implemented'
      });
    } else {
      return res.status(400).json({
        error: 'Invalid query_type. Must be: text, image, or audio'
      });
    }

    // Search database using pgvector cosine similarity
    // Using raw SQL since Prisma doesn't support vector operations
    const results = await prisma.$queryRaw`
      SELECT 
        fv.evidence_id,
        fv.vector_type,
        fv.dimensions,
        1 - (fv.vector <=> ${queryEmbedding}::vector) as similarity,
        e.media_type,
        e.sha256_original,
        e.created_at,
        d.device_model,
        d.trust_tier
      FROM fingerprint_vectors fv
      JOIN evidences e ON fv.evidence_id = e.evidence_id
      JOIN devices d ON e.signer_device_key_id = d.key_id
      WHERE fv.vector_type = ${vector_type}
        AND 1 - (fv.vector <=> ${queryEmbedding}::vector) >= ${threshold}
      ORDER BY fv.vector <=> ${queryEmbedding}::vector
      LIMIT ${limit}
    `;

    return res.status(200).json({
      success: true,
      query_type,
      vector_type,
      dimensions,
      threshold,
      results_count: results.length,
      results: results.map(r => ({
        evidence_id: r.evidence_id,
        similarity: parseFloat(r.similarity),
        media_type: r.media_type,
        sha256_original: r.sha256_original,
        created_at: r.created_at,
        device_model: r.device_model,
        trust_tier: r.trust_tier
      }))
    });

  } catch (error) {
    console.error('Cross-modal search error:', error);
    return res.status(500).json({
      error: 'Cross-modal search failed',
      detail: error.message
    });
  }
});

/**
 * GET /v5/search/stats
 * 
 * Get search statistics
 */
router.get('/stats', async (req, res) => {
  try {
    const stats = await prisma.$queryRaw`
      SELECT 
        vector_type,
        COUNT(*) as total_vectors,
        AVG(dimensions) as avg_dimensions
      FROM fingerprint_vectors
      GROUP BY vector_type
    `;

    const modelStats = await prisma.modelRegistry.findMany({
      select: {
        modelName: true,
        vectorType: true,
        dimensions: true,
        isActive: true,
        _count: {
          select: {
            fingerprintVectors: true
          }
        }
      }
    });

    return res.status(200).json({
      success: true,
      vector_stats: stats,
      model_stats: modelStats
    });

  } catch (error) {
    console.error('Search stats error:', error);
    return res.status(500).json({
      error: 'Failed to get search statistics',
      detail: error.message
    });
  }
});

module.exports = router;
