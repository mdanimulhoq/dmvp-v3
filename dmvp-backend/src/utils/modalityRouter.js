'use strict';

/**
 * Modality Router - Asset Type Detection
 * TDD v5 Phase 1 Step 1.7
 * 
 * Detects asset type from magic bytes (file signature) with MIME type fallback.
 * Routes assets to correct processing pipeline.
 */

const { fromBuffer } = require('file-type');

/**
 * Asset type categories
 */
const AssetType = {
  IMAGE: 'image',
  VIDEO: 'video',
  AUDIO: 'audio',
  PDF: 'pdf',
  DOCUMENT: 'document',
  CODE: 'code',
  ARCHIVE: 'archive',
  MODEL_3D: '3d',
  BINARY: 'binary',
  UNKNOWN: 'unknown',
};

/**
 * File extension to asset type mapping
 */
const EXTENSION_MAP = {
  // Images
  jpg: AssetType.IMAGE, jpeg: AssetType.IMAGE, png: AssetType.IMAGE,
  gif: AssetType.IMAGE, webp: AssetType.IMAGE, bmp: AssetType.IMAGE,
  tiff: AssetType.IMAGE, tif: AssetType.IMAGE, svg: AssetType.IMAGE,
  heic: AssetType.IMAGE, heif: AssetType.IMAGE, avif: AssetType.IMAGE,
  
  // Videos
  mp4: AssetType.VIDEO, mov: AssetType.VIDEO, avi: AssetType.VIDEO,
  mkv: AssetType.VIDEO, webm: AssetType.VIDEO, flv: AssetType.VIDEO,
  wmv: AssetType.VIDEO, m4v: AssetType.VIDEO,
  
  // Audio
  mp3: AssetType.AUDIO, wav: AssetType.AUDIO, flac: AssetType.AUDIO,
  aac: AssetType.AUDIO, ogg: AssetType.AUDIO, wma: AssetType.AUDIO,
  m4a: AssetType.AUDIO, aiff: AssetType.AUDIO,
  
  // Documents
  pdf: AssetType.PDF,
  doc: AssetType.DOCUMENT, docx: AssetType.DOCUMENT,
  xls: AssetType.DOCUMENT, xlsx: AssetType.DOCUMENT,
  ppt: AssetType.DOCUMENT, pptx: AssetType.DOCUMENT,
  txt: AssetType.DOCUMENT, rtf: AssetType.DOCUMENT,
  
  // Code
  js: AssetType.CODE, ts: AssetType.CODE, py: AssetType.CODE,
  java: AssetType.CODE, kt: AssetType.CODE, cpp: AssetType.CODE,
  c: AssetType.CODE, h: AssetType.CODE, cs: AssetType.CODE,
  go: AssetType.CODE, rs: AssetType.CODE, rb: AssetType.CODE,
  php: AssetType.CODE, swift: AssetType.CODE, scala: AssetType.CODE,
  json: AssetType.CODE, xml: AssetType.CODE, yaml: AssetType.CODE,
  yml: AssetType.CODE, html: AssetType.CODE, css: AssetType.CODE,
  
  // Archives
  zip: AssetType.ARCHIVE, tar: AssetType.ARCHIVE, gz: AssetType.ARCHIVE,
  bz2: AssetType.ARCHIVE, xz: AssetType.ARCHIVE, '7z': AssetType.ARCHIVE,
  rar: AssetType.ARCHIVE,
  
  // 3D Models
  obj: AssetType.MODEL_3D, fbx: AssetType.MODEL_3D, glb: AssetType.MODEL_3D,
  gltf: AssetType.MODEL_3D, stl: AssetType.MODEL_3D, ply: AssetType.MODEL_3D,
};

/**
 * MIME type to asset type mapping
 */
const MIME_MAP = {
  'image/jpeg': AssetType.IMAGE, 'image/png': AssetType.IMAGE,
  'image/gif': AssetType.IMAGE, 'image/webp': AssetType.IMAGE,
  'image/bmp': AssetType.IMAGE, 'image/tiff': AssetType.IMAGE,
  'image/svg+xml': AssetType.IMAGE, 'image/heic': AssetType.IMAGE,
  'image/avif': AssetType.IMAGE,
  
  'video/mp4': AssetType.VIDEO, 'video/quicktime': AssetType.VIDEO,
  'video/x-msvideo': AssetType.VIDEO, 'video/x-matroska': AssetType.VIDEO,
  'video/webm': AssetType.VIDEO, 'video/x-flv': AssetType.VIDEO,
  
  'audio/mpeg': AssetType.AUDIO, 'audio/wav': AssetType.AUDIO,
  'audio/flac': AssetType.AUDIO, 'audio/aac': AssetType.AUDIO,
  'audio/ogg': AssetType.AUDIO, 'audio/mp4': AssetType.AUDIO,
  
  'application/pdf': AssetType.PDF,
  'application/msword': AssetType.DOCUMENT,
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': AssetType.DOCUMENT,
  'application/vnd.ms-excel': AssetType.DOCUMENT,
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': AssetType.DOCUMENT,
  
  'application/zip': AssetType.ARCHIVE,
  'application/x-tar': AssetType.ARCHIVE,
  'application/gzip': AssetType.ARCHIVE,
};

/**
 * Detect asset type from buffer using magic bytes
 * 
 * @param {Buffer} buffer - File buffer (at least first 4KB recommended)
 * @returns {Promise<Object>} Detection result
 */
async function detectAssetType(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    return {
      assetType: AssetType.UNKNOWN,
      mimeType: null,
      extension: null,
      confidence: 'none',
      method: 'none',
    };
  }

  // Try magic bytes detection first (most reliable)
  try {
    const fileType = await fromBuffer(buffer);
    
    if (fileType) {
      const assetType = MIME_MAP[fileType.mime] || AssetType.UNKNOWN;
      return {
        assetType,
        mimeType: fileType.mime,
        extension: fileType.ext,
        confidence: 'high',
        method: 'magic_bytes',
      };
    }
  } catch (error) {
    console.error('file-type detection error:', error.message);
    // fileType detection failed, will fall back
  }

  // Fallback: could not detect
  return {
    assetType: AssetType.UNKNOWN,
    mimeType: null,
    extension: null,
    confidence: 'none',
    method: 'failed',
  };
}

/**
 * Detect asset type from file extension
 * 
 * @param {string} filename - Filename or extension
 * @returns {Object} Detection result
 */
function detectFromExtension(filename) {
  if (!filename || typeof filename !== 'string') {
    return {
      assetType: AssetType.UNKNOWN,
      mimeType: null,
      extension: null,
      confidence: 'none',
      method: 'none',
    };
  }

  // Extract extension
  const ext = filename.split('.').pop()?.toLowerCase();
  
  if (!ext || ext === filename) {
    return {
      assetType: AssetType.UNKNOWN,
      mimeType: null,
      extension: null,
      confidence: 'none',
      method: 'no_extension',
    };
  }

  const assetType = EXTENSION_MAP[ext] || AssetType.UNKNOWN;
  
  return {
    assetType,
    mimeType: null, // Extension doesn't give us MIME
    extension: ext,
    confidence: assetType !== AssetType.UNKNOWN ? 'medium' : 'none',
    method: 'extension',
  };
}

/**
 * Route asset to appropriate processing pipeline
 * Combines magic bytes detection with extension fallback
 * 
 * @param {Buffer} buffer - File buffer
 * @param {string} [filename] - Optional filename for fallback
 * @param {string} [mimeType] - Optional MIME type hint
 * @returns {Promise<Object>} Routing decision
 */
async function routeAsset(buffer, filename = null, mimeType = null) {
  // Priority 1: Magic bytes (most reliable)
  const magicResult = await detectAssetType(buffer);
  
  if (magicResult.confidence === 'high') {
    return {
      ...magicResult,
      pipeline: getPipelineForType(magicResult.assetType),
      finalDecision: 'magic_bytes',
    };
  }

  // Priority 2: MIME type hint
  if (mimeType && MIME_MAP[mimeType]) {
    return {
      assetType: MIME_MAP[mimeType],
      mimeType,
      extension: null,
      confidence: 'medium',
      method: 'mime_hint',
      pipeline: getPipelineForType(MIME_MAP[mimeType]),
      finalDecision: 'mime_hint',
    };
  }

  // Priority 3: File extension
  if (filename) {
    const extResult = detectFromExtension(filename);
    if (extResult.confidence !== 'none') {
      return {
        ...extResult,
        pipeline: getPipelineForType(extResult.assetType),
        finalDecision: 'extension',
      };
    }
  }

  // Fallback: Unknown
  return {
    assetType: AssetType.UNKNOWN,
    mimeType: mimeType || null,
    extension: null,
    confidence: 'none',
    method: 'unknown',
    pipeline: null,
    finalDecision: 'unknown',
  };
}

/**
 * Get processing pipeline for asset type
 * 
 * @param {string} assetType - Asset type
 * @returns {string|null} Pipeline name
 */
function getPipelineForType(assetType) {
  const pipelines = {
    [AssetType.IMAGE]: 'image_pipeline',
    [AssetType.VIDEO]: 'video_pipeline',
    [AssetType.AUDIO]: 'audio_pipeline',
    [AssetType.PDF]: 'document_pipeline',
    [AssetType.DOCUMENT]: 'document_pipeline',
    [AssetType.CODE]: 'code_pipeline',
    [AssetType.ARCHIVE]: 'archive_pipeline',
    [AssetType.MODEL_3D]: '3d_pipeline',
    [AssetType.BINARY]: 'binary_pipeline',
  };
  
  return pipelines[assetType] || null;
}

module.exports = {
  AssetType,
  detectAssetType,
  detectFromExtension,
  routeAsset,
  getPipelineForType,
  EXTENSION_MAP,
  MIME_MAP,
};
