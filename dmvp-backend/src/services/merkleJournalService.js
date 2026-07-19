/**
 * @file src/services/merkleJournalService.js
 * @description DMVP v4.0 — Merkle Journal + Multi-Tier Timestamping Service
 *
 * Batches registrations into Merkle trees, anchors root hash to:
 *   T1: RFC 3161 TSA (DigiCert/Entrust) — legally recognized
 *   T2a: Sigstore Rekor — append-only transparency log
 *   T2b: OpenTimestamps — Bitcoin anchoring
 *   T2c: Arbitrum/Optimism (EVM L2) — EU enterprise (eIDAS2)
 *
 * @module services/merkleJournalService
 * @version dmvp-v4.0.0
 */

'use strict';

const crypto = require('crypto');

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const BATCH_SIZE = 100; // registrations per batch
const TIMESTAMP_TIERS = {
  T1_RFC3161: 'RFC3161_TSA',
  T2A_SIGSTORE: 'Sigstore_Rekor',
  T2B_OPENTIMESTAMPS: 'OpenTimestamps_Bitcoin',
  T2C_EVM_L2: 'EVM_L2_Arbitrum',
};

// ─────────────────────────────────────────────────────────────────────────────
// Merkle Tree Builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compute SHA-256 hash of data
 * @param {string|Buffer} data
 * @returns {string} Hex-encoded hash
 */
function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

/**
 * Build Merkle tree from leaf hashes
 * @param {string[]} leafHashes - Array of hex-encoded leaf hashes
 * @returns {{ root: string, layers: string[][], depth: number }}
 */
function buildMerkleTree(leafHashes) {
  if (leafHashes.length === 0) {
    throw new Error('Cannot build Merkle tree from empty leaves');
  }

  const layers = [leafHashes.slice()];
  let currentLayer = leafHashes.slice();

  while (currentLayer.length > 1) {
    const nextLayer = [];
    
    for (let i = 0; i < currentLayer.length; i += 2) {
      if (i + 1 < currentLayer.length) {
        // Hash pair
        const combined = currentLayer[i] + currentLayer[i + 1];
        nextLayer.push(sha256(combined));
      } else {
        // Odd node — promote
        nextLayer.push(currentLayer[i]);
      }
    }
    
    layers.push(nextLayer);
    currentLayer = nextLayer;
  }

  return {
    root: currentLayer[0],
    layers,
    depth: layers.length - 1,
  };
}

/**
 * Generate Merkle proof for a leaf
 * @param {string[]} leafHashes - All leaf hashes
 * @param {number} leafIndex - Index of the leaf to prove
 * @returns {{ leaf: string, proof: string[], path: number[] }}
 */
function generateMerkleProof(leafHashes, leafIndex) {
  if (leafIndex < 0 || leafIndex >= leafHashes.length) {
    throw new Error('Leaf index out of bounds');
  }

  const { layers } = buildMerkleTree(leafHashes);
  const proof = [];
  const path = [];
  let currentIndex = leafIndex;

  for (let i = 0; i < layers.length - 1; i++) {
    const layer = layers[i];
    const isRight = currentIndex % 2 === 1;
    const siblingIndex = isRight ? currentIndex - 1 : currentIndex + 1;

    if (siblingIndex < layer.length) {
      proof.push(layer[siblingIndex]);
      path.push(isRight ? 0 : 1); // 0 = left, 1 = right
    }

    currentIndex = Math.floor(currentIndex / 2);
  }

  return {
    leaf: leafHashes[leafIndex],
    proof,
    path,
  };
}

/**
 * Verify Merkle proof
 * @param {string} leaf - Leaf hash
 * @param {string[]} proof - Sibling hashes
 * @param {number[]} path - Path indicators (0=left, 1=right)
 * @param {string} root - Expected root hash
 * @returns {boolean}
 */
function verifyMerkleProof(leaf, proof, path, root) {
  let currentHash = leaf;

  for (let i = 0; i < proof.length; i++) {
    const sibling = proof[i];
    const isRight = path[i] === 1;
    
    const combined = isRight 
      ? currentHash + sibling 
      : sibling + currentHash;
    
    currentHash = sha256(combined);
  }

  return currentHash === root;
}

// ─────────────────────────────────────────────────────────────────────────────
// Batch Builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Create batch from registration records
 * @param {object[]} registrations - Array of registration records
 * @returns {{ batchId: string, leafHashes: string[], tree: object, createdAt: string }}
 */
function createBatch(registrations) {
  if (registrations.length === 0) {
    throw new Error('Cannot create empty batch');
  }

  const batchId = crypto.randomUUID();
  const leafHashes = registrations.map(reg => {
    const canonical = JSON.stringify({
      evidenceId: reg.evidenceId,
      uaid: reg.uaid,
      sha256: reg.sha256,
      timestamp: reg.createdAt || new Date().toISOString(),
    });
    return sha256(canonical);
  });

  const tree = buildMerkleTree(leafHashes);

  return {
    batchId,
    registrations: registrations.map(r => r.evidenceId),
    leafHashes,
    tree,
    createdAt: new Date().toISOString(),
    status: 'PENDING_TIMESTAMP',
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// T1: RFC 3161 Timestamp Authority
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Request RFC 3161 timestamp (simulated)
 *
 * In production, integrate with:
 *   - DigiCert TSA: https://timestamp.digicert.com
 *   - Entrust TSA: https://timestamp.entrust.net/TSS/RFC3161SHA2TS
 *   - FreeTSA: https://freetsa.org/tsr
 *
 * Cost: ~$0.001/stamp
 * Latency: ~5 seconds
 *
 * @param {string} rootHash - Merkle root hash to timestamp
 * @returns {object} Timestamp response
 */
async function requestRFC3161Timestamp(rootHash) {
  // Simulated RFC 3161 response
  // In production: POST to TSA with TimeStampReq ASN.1 structure
  
  const timestampToken = {
    tsa_url: 'https://timestamp.digicert.com',
    hash_algorithm: 'SHA-256',
    hashed_message: rootHash,
    serial_number: crypto.randomBytes(20).toString('hex'),
    timestamp: new Date().toISOString(),
    accuracy_ms: 10,
    policy_oid: '2.16.840.1.114412.7.1.1', // DigiCert policy
    status: 'granted',
  };

  return {
    tier: TIMESTAMP_TIERS.T1_RFC3161,
    root_hash: rootHash,
    timestamp_token: timestampToken,
    verified: true,
    legally_recognized: true, // eIDAS2 QTS compliant
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// T2a: Sigstore Rekor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Submit to Sigstore Rekor transparency log (simulated)
 *
 * In production, use:
 *   - @sigstore/rekor-js (npm)
 *   - POST to https://rekor.sigstore.dev/api/v1/log/entries
 *
 * Cost: Free
 * Latency: ~2 seconds
 *
 * @param {string} rootHash - Merkle root hash
 * @returns {object} Rekor entry
 */
async function submitToSigstoreRekor(rootHash) {
  // Simulated Rekor entry
  // In production: const rekor = require('@sigstore/rekor-js');
  
  const logEntry = {
    log_index: Math.floor(Math.random() * 10000000),
    log_id: crypto.randomBytes(32).toString('hex'),
    body: Buffer.from(JSON.stringify({
      apiVersion: '0.0.1',
      kind: 'hashedrekord',
      spec: {
        data: { hash: { algorithm: 'sha256', value: rootHash } },
        signature: { format: 'x509', content: 'base64...' },
      },
    })).toString('base64'),
    integrated_time: Math.floor(Date.now() / 1000),
    verification: {
      inclusion_proof: {
        log_index: Math.floor(Math.random() * 10000000),
        root_hash: crypto.randomBytes(32).toString('hex'),
        tree_size: Math.floor(Math.random() * 10000000),
        hashes: [crypto.randomBytes(32).toString('hex')],
      },
    },
  };

  return {
    tier: TIMESTAMP_TIERS.T2A_SIGSTORE,
    root_hash: rootHash,
    rekor_entry: logEntry,
    transparency_log: true,
    publicly_auditable: true,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// T2b: OpenTimestamps (Bitcoin)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Submit to OpenTimestamps for Bitcoin anchoring (simulated)
 *
 * In production, use:
 *   - opentimestamps-client (npm)
 *   - CLI: ots submit <file>
 *
 * Cost: Free
 * Latency: ~10 minutes (Bitcoin confirmation)
 *
 * @param {string} rootHash - Merkle root hash
 * @returns {object} OpenTimestamps receipt
 */
async function submitToOpenTimestamps(rootHash) {
  // Simulated OpenTimestamps receipt
  // In production: const ots = require('opentimestamps-client');
  
  const receipt = {
    hash: rootHash,
    timestamp: new Date().toISOString(),
    attestations: [
      {
        type: 'bitcoin',
        anchor: 'bitcoin',
        height: 800000 + Math.floor(Math.random() * 10000),
        txid: crypto.randomBytes(32).toString('hex'),
        status: 'pending', // Will be 'confirmed' after ~10 min
      },
    ],
    calendar_servers: [
      'https://alice.btc.calendar.opentimestamps.org',
      'https://bob.btc.calendar.opentimestamps.org',
    ],
  };

  return {
    tier: TIMESTAMP_TIERS.T2B_OPENTIMESTAMPS,
    root_hash: rootHash,
    receipt,
    blockchain: 'bitcoin',
    immutable: true,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// T2c: EVM L2 (Arbitrum/Optimism) — eIDAS2 Compliant
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Submit to EVM L2 blockchain (simulated)
 *
 * In production, use:
 *   - ethers.js or web3.js
 *   - Arbitrum: https://arb1.arbitrum.io/rpc
 *   - Optimism: https://mainnet.optimism.io
 *
 * Cost: ~$0.01/tx
 * Latency: ~2 seconds
 * eIDAS2: Legally recognized from 2026
 *
 * @param {string} rootHash - Merkle root hash
 * @returns {object} Transaction receipt
 */
async function submitToEvmL2(rootHash) {
  // Simulated EVM transaction
  // In production: const ethers = require('ethers');
  
  const txReceipt = {
    chain: 'arbitrum',
    chain_id: 42161,
    tx_hash: '0x' + crypto.randomBytes(32).toString('hex'),
    block_number: 100000000 + Math.floor(Math.random() * 100000),
    block_hash: '0x' + crypto.randomBytes(32).toString('hex'),
    from: '0x' + crypto.randomBytes(20).toString('hex'),
    to: '0x' + crypto.randomBytes(20).toString('hex'), // DMVP contract
    gas_used: 50000 + Math.floor(Math.random() * 10000),
    status: 1, // success
    timestamp: new Date().toISOString(),
    data: {
      merkle_root: rootHash,
      batch_size: BATCH_SIZE,
    },
  };

  return {
    tier: TIMESTAMP_TIERS.T2C_EVM_L2,
    root_hash: rootHash,
    tx_receipt: txReceipt,
    eidas2_compliant: true, // EU legally recognized from 2026
    etsi_en_319_422: true,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Multi-Tier Timestamping
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Anchor batch to all timestamp tiers
 * @param {object} batch - Batch with Merkle tree
 * @returns {object} Multi-tier timestamp attestations
 */
async function anchorBatch(batch) {
  const rootHash = batch.tree.root;

  // Request all timestamps in parallel
  const [t1, t2a, t2b, t2c] = await Promise.all([
    requestRFC3161Timestamp(rootHash),
    submitToSigstoreRekor(rootHash),
    submitToOpenTimestamps(rootHash),
    submitToEvmL2(rootHash),
  ]);

  return {
    batch_id: batch.batchId,
    merkle_root: rootHash,
    tree_depth: batch.tree.depth,
    leaf_count: batch.leafHashes.length,
    timestamps: {
      t1_rfc3161: t1,
      t2a_sigstore: t2a,
      t2b_opentimestamps: t2b,
      t2c_evm_l2: t2c,
    },
    anchored_at: new Date().toISOString(),
    status: 'ANCHORED',
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Exports
// ─────────────────────────────────────────────────────────────────────────────

module.exports = {
  // Constants
  BATCH_SIZE,
  TIMESTAMP_TIERS,
  
  // Merkle tree
  sha256,
  buildMerkleTree,
  generateMerkleProof,
  verifyMerkleProof,
  
  // Batch
  createBatch,
  
  // Individual timestamp tiers
  requestRFC3161Timestamp,
  submitToSigstoreRekor,
  submitToOpenTimestamps,
  submitToEvmL2,
  
  // Multi-tier anchoring
  anchorBatch,
};
