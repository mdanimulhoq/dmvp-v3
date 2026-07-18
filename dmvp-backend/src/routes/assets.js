/**
 * @file src/routes/assets.js
 * @description DMVP v3.0 → UDOVP v5.0 — Asset Management Routes
 *
 * Phase 1 Step 1.1: UAID Generator endpoint
 *
 * Endpoints:
 *   POST /assets/uaid/generate — Generate new UAID for an asset
 *
 * @module routes/assets
 * @version dmvp-v5.0.0-phase1
 */

'use strict';

const express = require('express');
const router = express.Router();

const { generateUAID, isValidUAID } = require('../utils/uaidGenerator');

/**
 * POST /assets/uaid/generate
 * 
 * Generate a new Universal Asset Identifier (UAID) for an asset.
 * 
 * Request body:
 * {
 *   "tenant_id": "t1"  // Required: tenant identifier
 * }
 * 
 * Response:
 * {
 *   "uaid": "uaid_5_t1_01J3ZQX...",
 *   "valid": true,
 *   "version": "5"
 * }
 */
router.post('/uaid/generate', (req, res) => {
  try {
    const { tenant_id } = req.body;

    if (!tenant_id) {
      return res.status(400).json({
        error_code: 'MISSING_TENANT_ID',
        message: 'tenant_id is required in request body',
        detail: {
          required_field: 'tenant_id',
          example: { tenant_id: 't1' },
        },
      });
    }

    const uaid = generateUAID(tenant_id);

    return res.status(201).json({
      uaid,
      valid: isValidUAID(uaid),
      version: '5',
      tenant_id: tenant_id.replace(/[_\s]/g, '').toLowerCase(),
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    if (error.message.includes('tenantId')) {
      return res.status(400).json({
        error_code: 'INVALID_TENANT_ID',
        message: error.message,
        detail: {
          tenant_id: req.body.tenant_id,
        },
      });
    }

    console.error('[UAID Generation Error]', {
      error: error.message,
      stack: error.stack,
      tenant_id: req.body.tenant_id,
      timestamp: new Date().toISOString(),
    });

    return res.status(500).json({
      error_code: 'UAID_GENERATION_FAILED',
      message: 'Failed to generate UAID',
      detail: process.env.NODE_ENV === 'development' ? { error: error.message } : null,
    });
  }
});

module.exports = router;
