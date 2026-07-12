/**
 * app/src/main/java/com/dmvp/app/security/HashUtils.kt
 *
 * Cryptographic hash utilities for DMVP v3.0 Android app.
 * Provides SHA-256 hashing, canonical media hashing (with EXIF stripping for images),
 * Hamming distance computation, and fingerprint profile comparison.
 *
 * All functions are pure Kotlin/Java based, using Android's built-in
 * MessageDigest and Bitmap/MediaMetadataRetriever for media processing.
 *
 * For canonical hashing, we use Android's BitmapFactory with options to strip metadata,
 * and re-encode to JPEG with deterministic settings.
 */

package com.dmvp.app.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.min
import timber.log.Timber

/**
 * SHA-256 hash utilities.
 */
object HashUtils {

    /**
     * Compute SHA-256 hash of a byte array.
     * @param data Byte array to hash.
     * @return 64-character lowercase hex string.
     */
    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash of a string (UTF-8).
     * @param input String to hash.
     * @return 64-character lowercase hex string.
     */
    fun sha256(input: String): String {
        return sha256(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Compute SHA-256 hash of a file.
     * @param file File to hash.
     * @return 64-character lowercase hex string.
     */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash of an InputStream.
     * Note: This consumes the stream.
     */
    fun sha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute canonical media hash.
     * For images: strip EXIF and other metadata, re-encode to baseline JPEG with quality 90.
     * For videos: currently returns null (normalization not implemented for MVP).
     *
     * @param file File to process.
     * @param mediaType "image" or "video".
     * @return SHA-256 of the canonical representation, or null if not supported.
     */
    fun canonicalHash(file: File, mediaType: String): String? {
        return when (mediaType.lowercase()) {
            "image" -> canonicalHashImage(file)
            "video" -> canonicalHashVideo(file)
            else -> null
        }
    }

    /**
     * Compute canonical hash for an image file.
     * Decodes the image, strips metadata by using BitmapFactory with inJustDecodeBounds and then
     * re-encode to JPEG with quality 90 after stripping EXIF.
     */
    private fun canonicalHashImage(file: File): String? {
        return try {
            // Load image as bitmap with options that strip metadata
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565 // simple config
                // No EXIF parsing
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            // Re-encode to JPEG with deterministic quality
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val jpegBytes = outputStream.toByteArray()
            // Compute SHA-256 of the JPEG bytes
            sha256(jpegBytes)
        } catch (e: Exception) {
            // On error, fallback to null (or original hash? but we return null to indicate canonical not available)
            Timber.e(e, "Failed to compute canonical hash for image")
            null
        }
    }

    /**
     * Compute canonical hash for a video file.
     * For MVP, we return null (video canonical normalization not implemented).
     * In production, you could strip container metadata using MediaMetadataRetriever.
     */
    private fun canonicalHashVideo(file: File): String? {
        // For MVP, return null.
        // Could later implement using MediaMetadataRetriever to strip metadata and re-encode?
        return null
    }

    /**
     * Compute Hamming distance between two binary or hex hash strings.
     * Handles both binary strings (e.g., "010101") and hex strings (e.g., "a1b2...").
     * Converts hex to binary if needed.
     *
     * @param hashA First hash string.
     * @param hashB Second hash string.
     * @return Number of differing bits (integer).
     * @throws IllegalArgumentException if lengths differ or invalid characters.
     */
    fun hammingDistance(hashA: String, hashB: String): Int {
        val binA = toBinaryString(hashA)
        val binB = toBinaryString(hashB)
        require(binA.length == binB.length) { "Hash lengths must be equal" }
        return binA.zip(binB).count { (a, b) -> a != b }
    }

    /**
     * Convert a hex string or binary string to a binary string of '0' and '1'.
     * If the input is hex (0-9a-fA-F), it expands each hex digit to 4 bits.
     * If input is already binary (only 0/1), returns as is.
     */
    private fun toBinaryString(hash: String): String {
        return if (hash.matches(Regex("^[0-9a-fA-F]+$"))) {
            // Hex: expand each character to 4 bits
            hash.map { char ->
                val int = char.digitToInt(16)
                String.format("%4s", Integer.toBinaryString(int)).replace(' ', '0')
            }.joinToString("")
        } else {
            // Assume binary string
            hash
        }
    }

    /**
     * Compare two robust fingerprint profiles and compute a similarity score (0.0 to 1.0).
     * Uses weighted average of phash, dhash, and blockHash if available.
     *
     * @param profileA First profile (map with keys: phash, dhash, blockHash).
     * @param profileB Second profile.
     * @param weights Optional weights map (default: phash=0.5, dhash=0.3, blockHash=0.2).
     * @return Similarity score between 0.0 and 1.0.
     */
    fun compareFingerprintProfiles(
        profileA: Map<String, String>,
        profileB: Map<String, String>,
        weights: Map<String, Double> = mapOf("phash" to 0.5, "dhash" to 0.3, "blockHash" to 0.2)
    ): Double {
        val totalWeight = weights.values.sum()
        require(totalWeight > 0) { "Weights must sum to > 0" }

        var weightedSimilarity = 0.0
        var usedWeight = 0.0

        for ((field, weight) in weights) {
            val hashA = profileA[field]
            val hashB = profileB[field]
            if (hashA != null && hashB != null && hashA.isNotEmpty() && hashB.isNotEmpty()) {
                try {
                    val distance = hammingDistance(hashA, hashB)
                    // Bit length: if hex, length*4; else length
                    val bitLen = if (hashA.matches(Regex("^[0-9a-fA-F]+$"))) hashA.length * 4 else hashA.length
                    val similarity = if (bitLen > 0) 1.0 - (distance.toDouble() / bitLen) else 0.0
                    weightedSimilarity += weight * similarity
                    usedWeight += weight
                } catch (e: Exception) {
                    // Skip this field on error
                    Timber.e(e, "Failed to compare fingerprint for field: $field")
                }
            }
        }

        return if (usedWeight > 0) {
            min(1.0, maxOf(0.0, weightedSimilarity / usedWeight))
        } else {
            0.0
        }
    }

    /**
     * Convenience function to compute SHA-256 of a Bitmap (as JPEG bytes).
     * Useful for hashing captured images before saving.
     */
    fun sha256(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 90): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return sha256(stream.toByteArray())
    }
}
