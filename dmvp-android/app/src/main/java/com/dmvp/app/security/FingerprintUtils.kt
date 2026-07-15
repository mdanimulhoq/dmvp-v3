/**
 * app/src/main/java/com/dmvp/app/security/FingerprintUtils.kt
 *
 * Robust fingerprint utilities for DMVP v3.0 Android app.
 * Provides perceptual hash (pHash) generation for images,
 * keyframe extraction for videos, and fingerprint profile creation.
 *
 * Uses Android's Bitmap and MediaMetadataRetriever for media processing.
 * Implements a perceptual hash algorithm based on DCT (similar to pHash).
 *
 * All functions are pure Kotlin with no external dependencies beyond Android SDK.
 */

package com.dmvp.app.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.util.Log
import com.dmvp.app.data.model.KeyframeFingerprint
import com.dmvp.app.data.model.RobustFingerprint
import kotlin.math.cos
import kotlin.math.sqrt
import timber.log.Timber

private const val TAG = "FingerprintUtils"

/**
 * Fingerprint utilities object.
 * Provides methods for generating robust fingerprints from images and videos.
 */
object FingerprintUtils {

    // DCT constants for pHash
    private const val PHASH_SIZE = 32  // Resize to 32x32
    private const val DCT_SIZE = 8     // Top-left 8x8 DCT coefficients

    /**
     * Generate a robust fingerprint for an image from a file path.
     * @param filePath Path to the image file.
     * @param algorithmVersion Version of the algorithm.
     * @return RobustFingerprint object or null if generation fails.
     */
    fun generateImageFingerprint(filePath: String, algorithmVersion: String = "v1.0"): RobustFingerprint? {
        return try {
            val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
            generateImageFingerprint(bitmap, algorithmVersion)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate image fingerprint from file: $filePath")
            null
        }
    }

    /**
     * Generate a robust fingerprint for an image from a Bitmap.
     * @param bitmap The bitmap image.
     * @param algorithmVersion Version of the algorithm.
     * @return RobustFingerprint object or null if generation fails.
     */
    fun generateImageFingerprint(bitmap: Bitmap, algorithmVersion: String = "v1.0"): RobustFingerprint? {
        return try {
            // Convert to grayscale and resize to 32x32
            val resized = Bitmap.createScaledBitmap(bitmap, PHASH_SIZE, PHASH_SIZE, true)
            val pixels = IntArray(PHASH_SIZE * PHASH_SIZE)
            resized.getPixels(pixels, 0, PHASH_SIZE, 0, 0, PHASH_SIZE, PHASH_SIZE)

            // Convert to grayscale doubles
            val gray = DoubleArray(PHASH_SIZE * PHASH_SIZE)
            for (i in pixels.indices) {
                val r = Color.red(pixels[i])
                val g = Color.green(pixels[i])
                val b = Color.blue(pixels[i])
                gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
            }

            // Apply 2D DCT
            val dct = applyDCT(gray, PHASH_SIZE)

            // Extract top-left DCT_SIZE x DCT_SIZE coefficients (excluding DC)
            val dctCoeffs = Array(DCT_SIZE) { row ->
                DoubleArray(DCT_SIZE) { col ->
                    dct[row * PHASH_SIZE + col]
                }
            }

            // Compute median of coefficients (excluding DC at [0,0])
            val coeffsList = mutableListOf<Double>()
            for (row in 0 until DCT_SIZE) {
                for (col in 0 until DCT_SIZE) {
                    if (row == 0 && col == 0) continue // skip DC
                    coeffsList.add(dctCoeffs[row][col])
                }
            }
            coeffsList.sort()
            val median = if (coeffsList.isNotEmpty()) coeffsList[coeffsList.size / 2] else 0.0

            // Build binary hash: 1 if coefficient > median, else 0
            val hashBits = StringBuilder()
            for (row in 0 until DCT_SIZE) {
                for (col in 0 until DCT_SIZE) {
                    if (row == 0 && col == 0) continue
                    hashBits.append(if (dctCoeffs[row][col] > median) '1' else '0')
                }
            }
            // Pad to multiple of 4? Not necessary, we can keep as binary string.
            val phash = hashBits.toString()

            // Also compute a difference hash (dhash) as a secondary hash
            val dhash = computeDifferenceHash(bitmap)

            // Block hash (simplified: divide into blocks and compute average)
            val blockHash = computeBlockHash(bitmap)

            // ── Step 4.7: Build fingerprint with width and height ──
            RobustFingerprint(
                phash = phash,
                dhash = dhash,
                blockHash = blockHash,
                localFeatures = null, // Not implemented for MVP
                embedding = null,
                width = bitmap.width,     // ── Step 4.7: Add width ──
                height = bitmap.height,   // ── Step 4.7: Add height ──
                codec = null              // ── Step 4.7: Image has no codec ──
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate image fingerprint from bitmap")
            null
        }
    }

    /**
     * Generate a robust fingerprint for a video file.
     * Extracts keyframes at regular intervals and computes fingerprints for each.
     * Also computes an audio fingerprint (placeholder) and motion summary (placeholder).
     *
     * ── Step 4.2: Added durationMs and fps extraction ──
     * ── Step 4.7: Added width, height, codec extraction ──
     *
     * @param filePath Path to the video file.
     * @param maxKeyframes Maximum number of keyframes to extract (default 10).
     * @param algorithmVersion Version of the algorithm.
     * @return RobustFingerprint object or null if generation fails.
     */
    fun generateVideoFingerprint(
        filePath: String,
        maxKeyframes: Int = 10,
        algorithmVersion: String = "v1.0"
    ): RobustFingerprint? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            // Get duration
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs <= 0) {
                Timber.w("Video duration is zero or invalid")
                retriever.release()
                return null
            }

            // ── Step 4.2: Extract FPS from video metadata ──
            val fpsString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            var fps: Double? = fpsString?.toDoubleOrNull()

            // Fallback: calculate fps from frame count and duration if available
            if (fps == null || fps <= 0) {
                val frameCountString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                if (frameCountString != null && durationMs > 0) {
                    val frameCount = frameCountString.toLongOrNull()
                    if (frameCount != null && frameCount > 0) {
                        fps = (frameCount * 1000.0) / durationMs
                    }
                }
            }

            // ── Step 4.7: Extract video metadata for transformation detection ──
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            // ── Fix: Use METADATA_KEY_MIMETYPE instead of METADATA_KEY_VIDEO_MIMETYPE ──
            val codecStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val width = widthStr?.toIntOrNull()
            val height = heightStr?.toIntOrNull()
            val codec = codecStr

            // Extract keyframes
            val keyframes = mutableListOf<KeyframeFingerprint>()
            val interval = if (maxKeyframes > 0) durationMs / maxKeyframes else durationMs
            for (i in 0 until maxKeyframes) {
                val timeMs = i * interval
                val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val fingerprint = generateImageFingerprint(frame, algorithmVersion)
                    if (fingerprint != null) {
                        keyframes.add(
                            KeyframeFingerprint(
                                frameIndex = i,
                                timestampMs = timeMs,
                                fingerprint = fingerprint
                            )
                        )
                    }
                }
            }

            retriever.release()

            // For MVP, we won't compute audio fingerprint or motion summary.
            // We'll use the first keyframe's phash as the main phash? Actually, we need a composite.
            // For simplicity, we'll use the first keyframe's phash as the primary phash.
            // In production, you'd combine or use a temporal signature.
            val primaryFingerprint = keyframes.firstOrNull()?.fingerprint
            val phash = primaryFingerprint?.phash ?: ""

            // ── Step 4.2 + 4.7: Build the fingerprint object with all metadata ──
            RobustFingerprint(
                phash = phash,
                dhash = null,
                blockHash = null,
                localFeatures = null,
                embedding = null,
                keyframes = keyframes,
                audioFingerprint = null, // Not implemented
                motionSummary = null,
                durationMs = durationMs,      // ── Step 4.2: Add duration ──
                fps = fps,                    // ── Step 4.2: Add fps ──
                width = width,                // ── Step 4.7: Add width ──
                height = height,              // ── Step 4.7: Add height ──
                codec = codec                 // ── Step 4.7: Add codec ──
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate video fingerprint")
            null
        }
    }

    /**
     * Compute a difference hash (dhash) for an image.
     * Resize to 9x8, compare adjacent pixels horizontally.
     * @param bitmap The image bitmap.
     * @return Binary string hash (length 64).
     */
    private fun computeDifferenceHash(bitmap: Bitmap): String {
        // Resize to 9x8 (or 8x9 for vertical; we'll use 9x8)
        val size = 9
        val resized = Bitmap.createScaledBitmap(bitmap, size, size - 1, true)
        val pixels = IntArray(resized.width * resized.height)
        resized.getPixels(pixels, 0, resized.width, 0, 0, resized.width, resized.height)

        // Convert to grayscale
        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        // Compare adjacent pixels horizontally
        val hash = StringBuilder()
        for (row in 0 until resized.height) {
            for (col in 0 until resized.width - 1) {
                hash.append(if (gray[row * resized.width + col] > gray[row * resized.width + col + 1]) '1' else '0')
            }
        }
        return hash.toString()
    }

    /**
     * Compute a block hash (simplified) for an image.
     * Divides image into 4x4 blocks and computes average grayscale, compares to overall average.
     * @param bitmap The image bitmap.
     * @return Binary string hash (length 16).
     */
    private fun computeBlockHash(bitmap: Bitmap): String {
        val blockSize = 4
        val resized = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
        val pixels = IntArray(resized.width * resized.height)
        resized.getPixels(pixels, 0, resized.width, 0, 0, resized.width, resized.height)

        // Convert to grayscale
        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        // Compute block averages
        val blockSizePx = resized.width / blockSize
        val blockAverages = DoubleArray(blockSize * blockSize)
        for (by in 0 until blockSize) {
            for (bx in 0 until blockSize) {
                var sum = 0.0
                var count = 0
                for (y in by * blockSizePx until (by + 1) * blockSizePx) {
                    for (x in bx * blockSizePx until (bx + 1) * blockSizePx) {
                        sum += gray[y * resized.width + x]
                        count++
                    }
                }
                blockAverages[by * blockSize + bx] = sum / count
            }
        }

        // Compute overall average
        val totalAvg = blockAverages.average()

        // Build hash: 1 if block average > overall average
        val hash = StringBuilder()
        for (value in blockAverages) {
            hash.append(if (value > totalAvg) '1' else '0')
        }
        return hash.toString()
    }

    /**
     * Apply 2D DCT to a square matrix.
     * @param input Flattened array of size N x N.
     * @param n Size of the matrix.
     * @return Flattened array of DCT coefficients.
     */
    private fun applyDCT(input: DoubleArray, n: Int): DoubleArray {
        val output = DoubleArray(n * n)
        val c = DoubleArray(n)
        for (i in 0 until n) {
            c[i] = if (i == 0) 1.0 / sqrt(2.0) else 1.0
        }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        val cos1 = cos(((2 * x + 1) * u * Math.PI) / (2.0 * n))
                        val cos2 = cos(((2 * y + 1) * v * Math.PI) / (2.0 * n))
                        sum += input[x * n + y] * cos1 * cos2
                    }
                }
                sum *= c[u] * c[v] / sqrt(2.0 * n)
                output[u * n + v] = sum
            }
        }
        return output
    }

    /**
     * Helper to compute fingerprint profile from a byte array (image data).
     * @param imageData Byte array of image (JPEG/PNG).
     * @param mediaType "image" or "video" (only "image" supported for this method).
     * @param algorithmVersion Version of the algorithm.
     * @return RobustFingerprint or null.
     */
    fun generateFingerprintFromBytes(imageData: ByteArray, mediaType: String, algorithmVersion: String = "v1.0"): RobustFingerprint? {
        return if (mediaType.lowercase() == "image") {
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap != null) {
                generateImageFingerprint(bitmap, algorithmVersion)
            } else {
                null
            }
        } else {
            // For video, we would need to decode from bytes; not implemented for MVP.
            // Could use MediaMetadataRetriever with a file or URI, but bytes to file is inefficient.
            null
        }
    }

    /**
     * Compute Hamming distance between two binary strings.
     * Provided for convenience; uses the same logic as HashUtils.hammingDistance.
     */
    fun hammingDistance(hashA: String, hashB: String): Int {
        val binA = if (hashA.matches(Regex("^[0-9a-fA-F]+$"))) hexToBinary(hashA) else hashA
        val binB = if (hashB.matches(Regex("^[0-9a-fA-F]+$"))) hexToBinary(hashB) else hashB
        require(binA.length == binB.length) { "Hash lengths must be equal" }
        return binA.zip(binB).count { (a, b) -> a != b }
    }

    private fun hexToBinary(hex: String): String {
        return hex.map { char ->
            val int = char.digitToInt(16)
            String.format("%4s", Integer.toBinaryString(int)).replace(' ', '0')
        }.joinToString("")
    }

    /**
     * Compare two fingerprint profiles and return similarity score (0.0 to 1.0).
     * Uses weighted average of phash, dhash, blockHash if available.
     */
    fun compareFingerprintProfiles(
        profileA: RobustFingerprint,
        profileB: RobustFingerprint,
        weights: Map<String, Double> = mapOf("phash" to 0.5, "dhash" to 0.3, "blockHash" to 0.2)
    ): Double {
        val totalWeight = weights.values.sum()
        require(totalWeight > 0) { "Weights must sum to > 0" }

        var weightedSimilarity = 0.0
        var usedWeight = 0.0

        // For phash
        if (weights.containsKey("phash") && profileA.phash.isNotEmpty() && profileB.phash.isNotEmpty()) {
            try {
                val dist = hammingDistance(profileA.phash, profileB.phash)
                val bitLen = if (profileA.phash.matches(Regex("^[0-9a-fA-F]+$"))) profileA.phash.length * 4 else profileA.phash.length
                val similarity = if (bitLen > 0) 1.0 - (dist.toDouble() / bitLen) else 0.0
                weightedSimilarity += weights["phash"]!! * similarity
                usedWeight += weights["phash"]!!
            } catch (_: Exception) { /* skip */ }
        }

        // For dhash
        val dhashA = profileA.dhash
        val dhashB = profileB.dhash
        if (weights.containsKey("dhash") && dhashA != null && dhashB != null && dhashA.isNotEmpty() && dhashB.isNotEmpty()) {
            try {
                val dist = hammingDistance(dhashA, dhashB)
                val bitLen = if (dhashA.matches(Regex("^[0-9a-fA-F]+$"))) dhashA.length * 4 else dhashA.length
                val similarity = if (bitLen > 0) 1.0 - (dist.toDouble() / bitLen) else 0.0
                weightedSimilarity += weights["dhash"]!! * similarity
                usedWeight += weights["dhash"]!!
            } catch (_: Exception) { /* skip */ }
        }

        // For blockHash
        val blockA = profileA.blockHash
        val blockB = profileB.blockHash
        if (weights.containsKey("blockHash") && blockA != null && blockB != null && blockA.isNotEmpty() && blockB.isNotEmpty()) {
            try {
                val dist = hammingDistance(blockA, blockB)
                val bitLen = if (blockA.matches(Regex("^[0-9a-fA-F]+$"))) blockA.length * 4 else blockA.length
                val similarity = if (bitLen > 0) 1.0 - (dist.toDouble() / bitLen) else 0.0
                weightedSimilarity += weights["blockHash"]!! * similarity
                usedWeight += weights["blockHash"]!!
            } catch (_: Exception) { /* skip */ }
        }

        return if (usedWeight > 0) {
            (weightedSimilarity / usedWeight).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }
}
