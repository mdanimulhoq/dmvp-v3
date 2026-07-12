package com.dmvp.app.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.min

object HashUtils {

    private const val SHA_256 = "SHA-256"
    private const val BUFFER_SIZE = 8192

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance(SHA_256)
        return digest.digest(data).toHexString()
    }

    fun sha256(input: String): String {
        return sha256(input.toByteArray(Charsets.UTF_8))
    }

    fun sha256(file: File): String {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
        require(file.canRead()) { "File cannot be read: ${file.absolutePath}" }

        return FileInputStream(file).use { inputStream ->
            sha256(inputStream)
        }
    }

    fun sha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance(SHA_256)
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break
            digest.update(buffer, 0, bytesRead)
        }

        return digest.digest().toHexString()
    }

    fun canonicalHash(file: File, mediaType: String): String? {
        return when (mediaType.lowercase()) {
            "image", "image/jpeg", "image/png", "image/webp" -> canonicalHashImage(file)
            "video", "video/mp4", "video/quicktime" -> canonicalHashVideo(file)
            else -> null
        }
    }

    private fun canonicalHashImage(file: File): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val outputStream = ByteArrayOutputStream()

            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

            sha256(outputStream.toByteArray())
        } catch (e: Exception) {
            null
        }
    }

    private fun canonicalHashVideo(file: File): String? {
        return null
    }

    fun hammingDistance(hashA: String, hashB: String): Int {
        val binA = toBinaryString(hashA)
        val binB = toBinaryString(hashB)

        require(binA.length == binB.length) {
            "Hash lengths must be equal"
        }

        return binA.zip(binB).count { (a, b) -> a != b }
    }

    private fun toBinaryString(hash: String): String {
        return if (hash.matches(Regex("^[0-9a-fA-F]+$"))) {
            hash.map { char ->
                val value = char.digitToInt(16)
                String.format("%4s", Integer.toBinaryString(value)).replace(' ', '0')
            }.joinToString("")
        } else {
            require(hash.matches(Regex("^[01]+$"))) {
                "Hash must be hexadecimal or binary"
            }
            hash
        }
    }

    fun compareFingerprintProfiles(
        profileA: Map<String, String>,
        profileB: Map<String, String>,
        weights: Map<String, Double> = mapOf(
            "phash" to 0.5,
            "dhash" to 0.3,
            "blockHash" to 0.2
        )
    ): Double {
        val totalWeight = weights.values.sum()
        require(totalWeight > 0) {
            "Weights must sum to > 0"
        }

        var weightedSimilarity = 0.0
        var usedWeight = 0.0

        for ((field, weight) in weights) {
            val hashA = profileA[field]
            val hashB = profileB[field]

            if (!hashA.isNullOrEmpty() && !hashB.isNullOrEmpty()) {
                try {
                    val distance = hammingDistance(hashA, hashB)
                    val bitLength = if (hashA.matches(Regex("^[0-9a-fA-F]+$"))) {
                        hashA.length * 4
                    } else {
                        hashA.length
                    }

                    val similarity = if (bitLength > 0) {
                        1.0 - (distance.toDouble() / bitLength)
                    } else {
                        0.0
                    }

                    weightedSimilarity += weight * similarity
                    usedWeight += weight
                } catch (e: Exception) {
                    // Skip invalid fingerprint field.
                }
            }
        }

        return if (usedWeight > 0) {
            min(1.0, maxOf(0.0, weightedSimilarity / usedWeight))
        } else {
            0.0
        }
    }

    fun sha256(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, quality, stream)
        return sha256(stream.toByteArray())
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
