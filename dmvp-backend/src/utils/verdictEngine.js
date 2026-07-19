/**
 * Phase 3 Step 3.7: 10-Layer Verdict Engine
 * Technology: Node.js + TypeScript
 * 
 * Aggregates signals from all 10 layers and produces final verdict:
 * - L1 exact hash match → exact_match (confidence 1.0)
 * - L3 perceptual match >90% → near_copy
 * - L4 semantic match >80% → derivative
 * - L4 match but lower → similar
 * - L8 AI signal → possible_ai_derivative
 * - Nothing → no_match
 * 
 * Endpoint: POST /v4/verify
 */

const axios = require('axios');

// Service URLs
const L8_SERVICE_URL = process.env.L8_SERVICE_URL || 'http://localhost:8006';

/**
 * Layer verdict types
 */
const VERDICT_TYPES = {
  EXACT_MATCH: 'exact_match',
  NEAR_COPY: 'near_copy',
  DERIVATIVE: 'derivative',
  SIMILAR: 'similar',
  POSSIBLE_AI_DERIVATIVE: 'possible_ai_derivative',
  NO_MATCH: 'no_match',
};

/**
 * Confidence thresholds
 */
const THRESHOLDS = {
  L1_EXACT: 1.0,
  L3_PERCEPTUAL_NEAR_COPY: 0.90,
  L3_PERCEPTUAL_SIMILAR: 0.70,
  L4_SEMANTIC_DERIVATIVE: 0.80,
  L4_SEMANTIC_SIMILAR: 0.60,
};

/**
 * Run 10-layer verification
 * 
 * @param {Object} input - Input asset data
 * @param {Array} layerResults - Results from each layer
 * @returns {Object} Final verdict with layer-by-layer breakdown
 */
async function runVerdictEngine(input, layerResults) {
  const verdict = {
    request_id: input.request_id || generateRequestId(),
    timestamp: new Date().toISOString(),
    verdict: VERDICT_TYPES.NO_MATCH,
    confidence: 0.0,
    layers: {},
    summary: {
      total_layers_evaluated: 0,
      layers_with_signal: 0,
      highest_confidence_layer: null,
    },
  };

  // Evaluate each layer
  for (const layer of layerResults) {
    verdict.layers[layer.layer] = {
      evaluated: true,
      signal_detected: layer.signal_detected || false,
      confidence: layer.confidence || 0.0,
      details: layer.details || {},
    };

    verdict.summary.total_layers_evaluated++;
    if (layer.signal_detected) {
      verdict.summary.layers_with_signal++;
    }
  }

  // Determine final verdict based on layer priorities
  // Priority: L1 > L3 > L4 > L8

  // L1: Exact hash match
  if (verdict.layers.L1?.signal_detected && verdict.layers.L1.confidence === THRESHOLDS.L1_EXACT) {
    verdict.verdict = VERDICT_TYPES.EXACT_MATCH;
    verdict.confidence = 1.0;
    verdict.summary.highest_confidence_layer = 'L1';
    return verdict;
  }

  // L3: Perceptual match
  if (verdict.layers.L3?.signal_detected) {
    const l3Confidence = verdict.layers.L3.confidence;
    
    if (l3Confidence >= THRESHOLDS.L3_PERCEPTUAL_NEAR_COPY) {
      verdict.verdict = VERDICT_TYPES.NEAR_COPY;
      verdict.confidence = l3Confidence;
      verdict.summary.highest_confidence_layer = 'L3';
      return verdict;
    } else if (l3Confidence >= THRESHOLDS.L3_PERCEPTUAL_SIMILAR) {
      verdict.verdict = VERDICT_TYPES.SIMILAR;
      verdict.confidence = l3Confidence;
      verdict.summary.highest_confidence_layer = 'L3';
      return verdict;
    }
  }

  // L4: Semantic match
  if (verdict.layers.L4?.signal_detected) {
    const l4Confidence = verdict.layers.L4.confidence;
    
    if (l4Confidence >= THRESHOLDS.L4_SEMANTIC_DERIVATIVE) {
      verdict.verdict = VERDICT_TYPES.DERIVATIVE;
      verdict.confidence = l4Confidence;
      verdict.summary.highest_confidence_layer = 'L4';
      return verdict;
    } else if (l4Confidence >= THRESHOLDS.L4_SEMANTIC_SIMILAR) {
      verdict.verdict = VERDICT_TYPES.SIMILAR;
      verdict.confidence = l4Confidence;
      verdict.summary.highest_confidence_layer = 'L4';
      return verdict;
    }
  }

  // L8: AI derivative detection
  if (verdict.layers.L8?.signal_detected) {
    verdict.verdict = VERDICT_TYPES.POSSIBLE_AI_DERIVATIVE;
    verdict.confidence = verdict.layers.L8.confidence;
    verdict.summary.highest_confidence_layer = 'L8';
    return verdict;
  }

  // No match
  verdict.verdict = VERDICT_TYPES.NO_MATCH;
  verdict.confidence = 0.0;

  return verdict;
}

/**
 * Evaluate L1: Exact hash match
 */
async function evaluateL1(input) {
  const { sha256_original, blake3_hash } = input;
  
  // Query database for exact hash match
  // This would be implemented in the actual route handler
  return {
    layer: 'L1',
    signal_detected: false, // Would be determined by database query
    confidence: 0.0,
    details: {
      sha256_checked: !!sha256_original,
      blake3_checked: !!blake3_hash,
    },
  };
}

/**
 * Evaluate L3: Perceptual hash match
 */
async function evaluateL3(input) {
  const { perceptual_hashes } = input;
  
  // Query database for perceptual similarity
  // This would use pgvector or fingerprint comparison
  return {
    layer: 'L3',
    signal_detected: false, // Would be determined by similarity search
    confidence: 0.0,
    details: {
      pdq_checked: !!perceptual_hashes?.pdq,
      phash_checked: !!perceptual_hashes?.phash,
      dhash_checked: !!perceptual_hashes?.dhash,
    },
  };
}

/**
 * Evaluate L4: Semantic embedding match
 */
async function evaluateL4(input) {
  const { embedding_vector } = input;
  
  // Query database for semantic similarity using pgvector
  // This would use cosine similarity search
  return {
    layer: 'L4',
    signal_detected: false, // Would be determined by vector similarity
    confidence: 0.0,
    details: {
      vector_dimensions: embedding_vector?.length || 0,
      model: 'siglip-2-large',
    },
  };
}

/**
 * Evaluate L8: AI derivative detection
 */
async function evaluateL8(imageBuffer) {
  try {
    // Call L8 service
    const formData = new FormData();
    formData.append('file', new Blob([imageBuffer]), 'image.jpg');
    
    const response = await axios.post(`${L8_SERVICE_URL}/detect`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    if (response.data.success && response.data.verdict) {
      const v = response.data.verdict;
      return {
        layer: 'L8',
        signal_detected: v.status === 'possible_ai_derivative',
        confidence: v.score,
        details: {
          status: v.status,
          fpr: v.fpr,
          fnr: v.fnr,
          model_versions: v.model_versions,
          is_evidentiary_signal: v.is_evidentiary_signal,
          disclaimer: v.disclaimer,
          tier1_results: v.tier1_results,
          tier2_results: v.tier2_results,
        },
      };
    }
  } catch (error) {
    console.error('L8 evaluation error:', error.message);
  }
  
  return {
    layer: 'L8',
    signal_detected: false,
    confidence: 0.0,
    details: {
      error: 'L8 service unavailable',
    },
  };
}

/**
 * Generate request ID
 */
function generateRequestId() {
  return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

module.exports = {
  runVerdictEngine,
  evaluateL1,
  evaluateL3,
  evaluateL4,
  evaluateL8,
  VERDICT_TYPES,
  THRESHOLDS,
};
