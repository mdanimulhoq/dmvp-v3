'use strict';

const {
  AssetType,
  detectAssetType,
  detectFromExtension,
  routeAsset,
  getPipelineForType,
} = require('../src/utils/modalityRouter');

describe('Modality Router (TDD v5 Phase 1 Step 1.7)', () => {
  describe('detectAssetType', () => {
    test('detects JPEG from magic bytes', async () => {
      // Minimal valid JPEG (SOI + APP0 marker)
      const jpegBuffer = Buffer.from([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46,
        0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00
      ]);
      const result = await detectAssetType(jpegBuffer);
      expect(result.assetType).toBe(AssetType.IMAGE);
      expect(result.mimeType).toBe('image/jpeg');
      expect(result.confidence).toBe('high');
      expect(result.method).toBe('magic_bytes');
    });

    test('detects PNG from magic bytes', async () => {
      // PNG needs more than just the header - needs IHDR chunk
      const pngBuffer = Buffer.from([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
        0x00, 0x00, 0x00, 0x0D, // IHDR chunk length
        0x49, 0x48, 0x44, 0x52, // IHDR chunk type
        0x00, 0x00, 0x00, 0x01, // width: 1
        0x00, 0x00, 0x00, 0x01, // height: 1
        0x08, 0x02,             // bit depth: 8, color type: 2 (RGB)
        0x00, 0x00, 0x00        // compression, filter, interlace
      ]);
      const result = await detectAssetType(pngBuffer);
      expect(result.assetType).toBe(AssetType.IMAGE);
      expect(result.mimeType).toBe('image/png');
      expect(result.confidence).toBe('high');
    });

    test('detects PDF from magic bytes', async () => {
      const pdfHeader = Buffer.from('%PDF-1.4\n');
      const result = await detectAssetType(pdfHeader);
      expect(result.assetType).toBe(AssetType.PDF);
      expect(result.mimeType).toBe('application/pdf');
    });

    test('returns UNKNOWN for empty buffer', async () => {
      const result = await detectAssetType(Buffer.alloc(0));
      expect(result.assetType).toBe(AssetType.UNKNOWN);
      expect(result.confidence).toBe('none');
    });

    test('returns UNKNOWN for invalid input', async () => {
      const result = await detectAssetType('not a buffer');
      expect(result.assetType).toBe(AssetType.UNKNOWN);
    });
  });

  describe('detectFromExtension', () => {
    test('detects image from .jpg extension', () => {
      const result = detectFromExtension('photo.jpg');
      expect(result.assetType).toBe(AssetType.IMAGE);
      expect(result.extension).toBe('jpg');
      expect(result.confidence).toBe('medium');
      expect(result.method).toBe('extension');
    });

    test('detects video from .mp4 extension', () => {
      const result = detectFromExtension('video.mp4');
      expect(result.assetType).toBe(AssetType.VIDEO);
      expect(result.extension).toBe('mp4');
    });

    test('detects audio from .mp3 extension', () => {
      const result = detectFromExtension('song.mp3');
      expect(result.assetType).toBe(AssetType.AUDIO);
    });

    test('detects code from .py extension', () => {
      const result = detectFromExtension('script.py');
      expect(result.assetType).toBe(AssetType.CODE);
    });

    test('detects 3D model from .glb extension', () => {
      const result = detectFromExtension('model.glb');
      expect(result.assetType).toBe(AssetType.MODEL_3D);
    });

    test('returns UNKNOWN for unknown extension', () => {
      const result = detectFromExtension('file.xyz');
      expect(result.assetType).toBe(AssetType.UNKNOWN);
      expect(result.confidence).toBe('none');
    });

    test('handles filename without extension', () => {
      const result = detectFromExtension('noextension');
      expect(result.assetType).toBe(AssetType.UNKNOWN);
      expect(result.method).toBe('no_extension');
    });
  });

  describe('routeAsset', () => {
    test('prioritizes magic bytes over extension', async () => {
      const jpegBuffer = Buffer.from([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46,
        0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00
      ]);
      const result = await routeAsset(jpegBuffer, 'wrong.txt');
      expect(result.assetType).toBe(AssetType.IMAGE);
      expect(result.finalDecision).toBe('magic_bytes');
    });

    test('falls back to extension when magic bytes fail', async () => {
      const unknownBuffer = Buffer.from([0x00, 0x00, 0x00, 0x00]);
      const result = await routeAsset(unknownBuffer, 'photo.jpg');
      expect(result.assetType).toBe(AssetType.IMAGE);
      expect(result.finalDecision).toBe('extension');
    });

    test('uses MIME hint when provided', async () => {
      const unknownBuffer = Buffer.from([0x00, 0x00]);
      const result = await routeAsset(unknownBuffer, null, 'video/mp4');
      expect(result.assetType).toBe(AssetType.VIDEO);
      expect(result.finalDecision).toBe('mime_hint');
    });

    test('returns pipeline for detected type', async () => {
      const jpegBuffer = Buffer.from([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46,
        0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
        0x00, 0x01, 0x00, 0x00
      ]);
      const result = await routeAsset(jpegBuffer);
      expect(result.pipeline).toBe('image_pipeline');
    });

    test('returns null pipeline for unknown type', async () => {
      const result = await routeAsset(Buffer.alloc(0));
      expect(result.pipeline).toBeNull();
    });
  });

  describe('getPipelineForType', () => {
    test('returns image_pipeline for IMAGE', () => {
      expect(getPipelineForType(AssetType.IMAGE)).toBe('image_pipeline');
    });

    test('returns video_pipeline for VIDEO', () => {
      expect(getPipelineForType(AssetType.VIDEO)).toBe('video_pipeline');
    });

    test('returns audio_pipeline for AUDIO', () => {
      expect(getPipelineForType(AssetType.AUDIO)).toBe('audio_pipeline');
    });

    test('returns document_pipeline for PDF', () => {
      expect(getPipelineForType(AssetType.PDF)).toBe('document_pipeline');
    });

    test('returns code_pipeline for CODE', () => {
      expect(getPipelineForType(AssetType.CODE)).toBe('code_pipeline');
    });

    test('returns 3d_pipeline for MODEL_3D', () => {
      expect(getPipelineForType(AssetType.MODEL_3D)).toBe('3d_pipeline');
    });

    test('returns null for UNKNOWN', () => {
      expect(getPipelineForType(AssetType.UNKNOWN)).toBeNull();
    });
  });
});
