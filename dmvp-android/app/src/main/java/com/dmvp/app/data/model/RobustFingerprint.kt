/**
 * app/src/main/java/com/dmvp/app/data/model/RobustFingerprint.kt
 *
 * Robust fingerprint data models for DMVP v3.0.
 * Contains definitions for perceptual hashes, keyframe fingerprints,
 * algorithm versioning, and other fingerprint-related structures.
 *
 * These classes are used as components of the Canonical Evidence Envelope (CEE).
 * For consistency, all fingerprint-related models should be imported from this file.
 *
 * Note: If you have previously defined these classes inside CEE.kt, please remove
 * them from there and import them from this file to avoid duplication.
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Robust fingerprint profile for a media file.
 * Contains perceptual hash, difference hash, block hash, and optional features.
 *
 * ── Step 4.2: Added durationMs and fps fields for video support ──
 * ── Step 4.7: Added width, height, codec fields for transformation detection ──
 */
data class RobustFingerprint(
    // Perceptual hash (binary or hex string)
    @SerializedName("phash")
    val phash: String,

    // Difference hash (binary or hex string)
    @SerializedName("dhash")
    val dhash: String? = null,

    // Block/wavelet hash (binary or hex string)
    @SerializedName("blockHash")
    val blockHash: String? = null,

    // Optional local visual features (for re-ranking)
    @SerializedName("localFeatures")
    val localFeatures: List<Float>? = null,

    // Optional embedding vector (for search assistance only)
    @SerializedName("embedding")
    val embedding: List<Float>? = null,

    // For video: keyframe fingerprints
    @SerializedName("keyframes")
    val keyframes: List<KeyframeFingerprint>? = null,

    // Audio fingerprint track (for videos)
    @SerializedName("audioFingerprint")
    val audioFingerprint: String? = null,

    // Motion summary vector (for video)
    @SerializedName("motionSummary")
    val motionSummary: List<Float>? = null,

    // ── Step 4.2: Video duration in milliseconds ──
    @SerializedName("durationMs")
    val durationMs: Long? = null,

    // ── Step 4.2: Video frame rate (fps) ──
    @SerializedName("fps")
    val fps: Double? = null,

    // ── Step 4.7: Image/Video width for transformation detection ──
    @SerializedName("width")
    val width: Int? = null,

    // ── Step 4.7: Image/Video height for transformation detection ──
    @SerializedName("height")
    val height: Int? = null,

    // ── Step 4.7: Codec for transcode detection ──
    @SerializedName("codec")
    val codec: String? = null
)

/**
 * Keyframe fingerprint for video.
 * Contains the frame index, timestamp, and the fingerprint of that frame.
 */
data class KeyframeFingerprint(
    @SerializedName("frameIndex")
    val frameIndex: Int,

    @SerializedName("timestampMs")
    val timestampMs: Long,

    @SerializedName("fingerprint")
    val fingerprint: RobustFingerprint
)

/**
 * Fingerprint algorithm versions.
 * Tracks the versions of algorithms used for fingerprint generation.
 */
data class FingerprintAlgorithmVersions(
    @SerializedName("fingerprint")
    val fingerprint: String,   // e.g., "v1.0"

    @SerializedName("similarity")
    val similarity: String? = null,

    @SerializedName("normalization")
    val normalization: String? = null
)

/**
 * Utility object for fingerprint operations (e.g., conversion, distance).
 * Not a data model, but included here for convenience.
 */
object FingerprintUtils {
    /**
     * Convert a hex string to a binary string of '0' and '1'.
     */
    fun hexToBinary(hex: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16) }
        return bytes.joinToString("") { byte ->
            String.format("%8s", Integer.toBinaryString(byte)).replace(' ', '0')
        }
    }

    /**
     * Compute Hamming distance between two binary or hex strings.
     * If both are hex, they are converted to binary internally.
     */
    fun hammingDistance(hashA: String, hashB: String): Int {
        val binA = if (hashA.matches(Regex("^[0-9a-fA-F]+$"))) hexToBinary(hashA) else hashA
        val binB = if (hashB.matches(Regex("^[0-9a-fA-F]+$"))) hexToBinary(hashB) else hashB
        require(binA.length == binB.length) { "Hash lengths must be equal" }
        return binA.zip(binB).count { (a, b) -> a != b }
    }

    /**
     * Compute similarity score from Hamming distance.
     * Returns a value between 0.0 and 1.0 where 1.0 means identical.
     */
    fun similarityFromHamming(distance: Int, bitLength: Int): Double {
        return if (bitLength > 0) 1.0 - (distance.toDouble() / bitLength) else 0.0
    }
}
