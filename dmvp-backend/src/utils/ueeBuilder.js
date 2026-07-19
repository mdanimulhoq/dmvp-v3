'use strict';

/**
 * UEE v5.0 (Universal Evidence Envelope) Builder
 * TDD v5 Phase 1 Step 1.8
 * 
 * Replaces CEE (Content Evidence Envelope) with UEE v5.0
 * Maintains backward compatibility via cee_compat field
 * 
 * UEE includes:
 * - Dual hashes (SHA-256 + BLAKE3)
 * - C2PA signals
 * - Embedding references
 * - L1-L10 layer results
 */

/**
 * UEE v5.0 Schema Structure
 */
const UEE_SCHEMA = {
  version: '5.0',
  
  // Core identifiers
  uaid: null,              // Universal Asset Identifier (from Step 1.1)
  tenant_id: null,
  asset_type: null,        // image, video, audio, pdf, document, code, 3d, binary
  
  // Content hashes (dual hash system from Step 1.3)
  content_hashes: {
    sha256: null,          // Legal/TSA/court use
    blake3: null,          // Internal primary (3-5× faster)
    canonical: null,       // Optional normalized hash
  },
  
  // Provenance signals (from Step 1.5)
  provenance_signals: {
    c2pa_status: null,     // verified | present_unverified | absent
    c2pa_manifest_hash: null,
    c2pa_claim_generator: null,
    trustmark_decoded_uaid: null,
    synthid_detected: false,
  },
  
  // Fingerprint layers (L1-L10)
  fingerprint_layers: {
    L1_exact: {
      hash_match: null,
      confidence: null,
    },
    L2_structural: {
      structural_hash: null,
      metadata_digest: null,
    },
    L3_perceptual: {
      pdq_hash: null,      // Primary (Meta production standard)
      phash: null,         // Compatibility
      dhash: null,         // Lightweight
    },
    L4_embedding: {
      model_id: null,
      vector_ref: null,    // Reference to vector in pgvector/Qdrant
      dimensions: null,
    },
    L5_semantic: {
      model_id: null,
      vector_ref: null,
    },
    L6_audio_temporal: null,
    L7_video_temporal: null,
    L8_ai_derivative: {
      status: null,        // possible_ai_derivative | unlikely_ai_derivative
      score: null,
      fpr: null,           // False Positive Rate (MANDATORY)
      fnr: null,           // False Negative Rate (MANDATORY)
      model_versions: [],
      disclaimer: 'Evidentiary signal only. Human review required.',
    },
    L9_local_descriptor: null,
    L10_privacy_preserving: null,
  },
  
  // Device and signer info
  signer: {
    device_key_id: null,
    public_key_reference: null,
    signature_algorithm: null,
    device_attestation: null,
    trust_tier: null,
  },
  
  // Timestamps
  timestamps: {
    registration_server_time: null,
    trusted_timestamp_token: null,
    capture_time_claim: null,
  },
  
  // Privacy and metadata
  privacy_flags: {
    gps: false,
    exif: false,
    device_info: false,
  },
  
  // Lineage
  lineage: {
    parent_uaid: null,
    derivation_type: null,
  },
  
  // Backward compatibility
  cee_compat: null,        // Original CEE JSON for legacy systems
  
  // Metadata
  metadata: {
    protocol_version: null,
    policy_version: null,
    client_app_version: null,
    audit_reference: null,
  },
};

/**
 * Build UEE v5.0 from CEE and additional data
 * 
 * @param {Object} cee - Original CEE object
 * @param {Object} options - Additional data
 * @returns {Object} UEE v5.0 object
 */
function buildUEEFromCEE(cee, options = {}) {
  const {
    uaid = null,
    tenantId = null,
    assetType = null,
    blake3Hash = null,
    c2paResult = null,
    fingerprintLayers = {},
  } = options;

  const uee = {
    version: '5.0',
    
    // Core identifiers
    uaid,
    tenant_id: tenantId,
    asset_type: assetType || cee.media_type,
    
    // Content hashes
    content_hashes: {
      sha256: cee.sha256_original,
      blake3: blake3Hash,
      canonical: cee.canonical_media_hash || null,
    },
    
    // Provenance signals
    provenance_signals: {
      c2pa_status: c2paResult?.c2pa_status || 'absent',
      c2pa_manifest_hash: c2paResult?.c2pa_manifest_hash || null,
      c2pa_claim_generator: c2paResult?.c2pa_manifest?.claim_generator || null,
      trustmark_decoded_uaid: null,
      synthid_detected: false,
    },
    
    // Fingerprint layers
    fingerprint_layers: {
      L1_exact: {
        hash_match: null,
        confidence: null,
      },
      L2_structural: {
        structural_hash: null,
        metadata_digest: null,
      },
      L3_perceptual: {
        pdq_hash: fingerprintLayers.pdq || null,
        phash: cee.robust_fingerprint_profile?.phash || null,
        dhash: cee.robust_fingerprint_profile?.dhash || null,
      },
      L4_embedding: {
        model_id: null,
        vector_ref: null,
        dimensions: null,
      },
      L5_semantic: {
        model_id: null,
        vector_ref: null,
      },
      L6_audio_temporal: null,
      L7_video_temporal: null,
      L8_ai_derivative: {
        status: null,
        score: null,
        fpr: null,
        fnr: null,
        model_versions: [],
        disclaimer: 'Evidentiary signal only. Human review required.',
      },
      L9_local_descriptor: null,
      L10_privacy_preserving: null,
      ...fingerprintLayers,
    },
    
    // Signer info
    signer: {
      device_key_id: cee.signer_device_key_id,
      public_key_reference: cee.signer_public_key_reference,
      signature_algorithm: cee.signature_algorithm,
      device_attestation: cee.device_attestation_summary,
      trust_tier: null,
    },
    
    // Timestamps
    timestamps: {
      registration_server_time: cee.registration_server_time,
      trusted_timestamp_token: cee.trusted_timestamp_token_reference || null,
      capture_time_claim: cee.capture_time_claim || null,
    },
    
    // Privacy flags
    privacy_flags: cee.privacy_flags || {
      gps: false,
      exif: false,
      device_info: false,
    },
    
    // Lineage
    lineage: {
      parent_uaid: null,
      derivation_type: null,
    },
    
    // Backward compatibility - store original CEE
    cee_compat: cee,
    
    // Metadata
    metadata: {
      protocol_version: cee.protocol_version,
      policy_version: cee.verification_policy_version,
      client_app_version: cee.client_app_version,
      audit_reference: cee.audit_reference,
    },
  };

  return uee;
}

/**
 * Validate UEE v5.0 structure
 * 
 * @param {Object} uee - UEE object
 * @returns {Object} Validation result
 */
function validateUEE(uee) {
  const errors = [];
  
  if (!uee.version || uee.version !== '5.0') {
    errors.push('Invalid or missing UEE version');
  }
  
  if (!uee.content_hashes?.sha256) {
    errors.push('Missing required field: content_hashes.sha256');
  }
  
  if (!uee.signer?.device_key_id) {
    errors.push('Missing required field: signer.device_key_id');
  }
  
  if (!uee.timestamps?.registration_server_time) {
    errors.push('Missing required field: timestamps.registration_server_time');
  }
  
  // L8 AI derivative must have FPR/FNR if status is set
  if (uee.fingerprint_layers?.L8_ai_derivative?.status) {
    const l8 = uee.fingerprint_layers.L8_ai_derivative;
    if (l8.fpr === null || l8.fpr === undefined) {
      errors.push('L8 AI derivative must include false_positive_rate');
    }
    if (l8.fnr === null || l8.fnr === undefined) {
      errors.push('L8 AI derivative must include false_negative_rate');
    }
  }
  
  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Extract CEE from UEE for backward compatibility
 * 
 * @param {Object} uee - UEE v5.0 object
 * @returns {Object|null} Original CEE or null
 */
function extractCEEFromUEE(uee) {
  return uee?.cee_compat || null;
}

module.exports = {
  UEE_SCHEMA,
  buildUEEFromCEE,
  validateUEE,
  extractCEEFromUEE,
};
