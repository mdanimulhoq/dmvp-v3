/**
 * app/src/main/java/com/dmvp/app/security/SignatureUtils.kt
 *
 * Signature utilities for DMVP v3.0 Android app.
 * Provides functions for signing CEE payloads, verifying signatures,
 * generating nonces, and preparing request signatures for API calls.
 *
 * Uses Android Keystore via DeviceKeyManager for signing operations.
 * Implements canonical serialization for deterministic signature verification.
 */

package com.dmvp.app.security

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.security.MessageDigest
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber

private const val TAG = "SignatureUtils"

/**
 * Signature utilities object.
 * Handles cryptographic signing and verification for DMVP operations.
 */
object SignatureUtils {

    // Date format for ISO 8601 timestamps
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Nonce counter for generating unique nonces (simple sequence)
    private val nonceCounter = AtomicLong(System.currentTimeMillis())

    /**
     * Generate a cryptographic nonce string.
     * Combines timestamp and a counter to ensure uniqueness.
     * @return Base64-encoded nonce string.
     */
    fun generateNonce(): String {
        val timestamp = System.currentTimeMillis()
        val counter = nonceCounter.incrementAndGet()
        val nonceBytes = "$timestamp:$counter".toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
    }

    /**
     * Generate a timestamp in ISO 8601 format for API requests.
     * @return ISO 8601 timestamp string.
     */
    fun generateTimestamp(): String {
        return iso8601Format.format(Date())
    }

    /**
     * Create a canonical string representation of a payload for signing.
     * The canonical format must match what the server expects.
     * For DMVP v3.0, we use a deterministic JSON serialization.
     *
     * @param payload The payload object (any type that can be serialized by Gson).
     * @return Canonical string representation.
     */
    fun canonicalizePayload(payload: Any): String {
        // Use Gson with a custom serializer that sorts keys for deterministic output.
        // For simplicity, we'll use the default Gson with a sorted tree.
        val gson = Gson()
        val json = gson.toJson(payload)
        // Parse and sort keys to ensure canonical representation
        try {
            val jsonElement = JsonParser().parse(json)
            val sortedJson = sortJson(jsonElement)
            return gson.toJson(sortedJson)
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to canonicalize JSON, falling back to original")
            return json
        }
    }

    /**
     * Recursively sort JSON keys for deterministic serialization.
     */
    private fun sortJson(element: com.google.gson.JsonElement): com.google.gson.JsonElement {
        return when {
            element.isJsonObject -> {
                val sorted = com.google.gson.JsonObject()
                val entrySet = element.asJsonObject.entrySet()
                val sortedKeys = entrySet.map { it.key }.sorted()
                for (key in sortedKeys) {
                    val value = entrySet.find { it.key == key }?.value
                    if (value != null) {
                        sorted.add(key, sortJson(value))
                    }
                }
                sorted
            }
            element.isJsonArray -> {
                val array = element.asJsonArray
                val sorted = com.google.gson.JsonArray()
                for (item in array) {
                    sorted.add(sortJson(item))
                }
                sorted
            }
            else -> element
        }
    }

    /**
     * Sign a payload using the device key.
     * Builds a canonical representation of the payload and signs it.
     *
     * @param payload The payload object to sign.
     * @return Base64-encoded signature string, or null if signing fails.
     */
    fun signPayload(payload: Any): String? {
        return try {
            val canonical = canonicalizePayload(payload)
            signString(canonical)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign payload")
            null
        }
    }

    /**
     * Sign a string using the device key.
     * @param data The string to sign (UTF-8 encoded).
     * @return Base64-encoded signature, or null if signing fails.
     */
    fun signString(data: String): String? {
        return try {
            // Use DeviceKeyManager to get the private key and sign
            val signatureBytes = DeviceKeyManager.signData(data.toByteArray(Charsets.UTF_8))
            signatureBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign string")
            null
        }
    }

    /**
     * Sign a byte array using the device key.
     * @param data The byte array to sign.
     * @return Base64-encoded signature, or null if signing fails.
     */
    fun signBytes(data: ByteArray): String? {
        return try {
            val signatureBytes = DeviceKeyManager.signData(data)
            signatureBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign bytes")
            null
        }
    }

    /**
     * Verify a signature against data using the provided public key.
     * @param data The original data that was signed (string).
     * @param signatureBase64 The signature to verify (Base64 encoded).
     * @param publicKeyBase64 The public key (Base64 encoded) to use for verification.
     * @return true if signature is valid, false otherwise.
     */
    fun verifySignature(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            verifySignature(data.toByteArray(Charsets.UTF_8), signatureBytes, publicKeyBytes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify signature")
            false
        }
    }

    /**
     * Verify a signature using raw bytes and public key bytes.
     * @param data The original data bytes.
     * @param signature The signature bytes.
     * @param publicKeyBytes The public key bytes (X.509 encoded).
     * @return true if signature is valid, false otherwise.
     */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val spec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            val publicKey = keyFactory.generatePublic(spec)

            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            Timber.e(e, "Signature verification failed")
            false
        }
    }

    /**
     * Generate an HMAC-SHA256 signature of a message (for internal use, not device key).
     * @param message The message to sign.
     * @param key The secret key (bytes).
     * @return HMAC-SHA256 digest as hex string.
     */
    fun hmacSha256(message: String, key: ByteArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKey = javax.crypto.spec.SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sign a request for API submission.
     * This combines the payload, nonce, and timestamp into a canonical string,
     * signs it, and returns the signature along with nonce and timestamp.
     *
     * @param payload The payload object to sign (will be canonicalized).
     * @return Map containing "signature", "nonce", "timestamp" headers.
     */
    fun signRequest(payload: Any): Map<String, String> {
        val nonce = generateNonce()
        val timestamp = generateTimestamp()
        val canonicalPayload = canonicalizePayload(payload)
        val canonicalRequest = "$canonicalPayload\n$nonce\n$timestamp"
        val signature = signString(canonicalRequest)

        return mapOf(
            "signature" to (signature ?: ""),
            "nonce" to nonce,
            "timestamp" to timestamp
        )
    }

    /**
     * Verify a request signature from the server side (for local verification).
     * This is a convenience method for testing or offline verification.
     *
     * @param payload The original payload object.
     * @param signatureBase64 The signature to verify.
     * @param nonce The nonce used in signing.
     * @param timestamp The timestamp used in signing.
     * @param publicKeyBase64 The public key to verify against.
     * @return true if signature is valid, false otherwise.
     */
    fun verifyRequestSignature(
        payload: Any,
        signatureBase64: String,
        nonce: String,
        timestamp: String,
        publicKeyBase64: String
    ): Boolean {
        return try {
            val canonicalPayload = canonicalizePayload(payload)
            val canonicalRequest = "$canonicalPayload\n$nonce\n$timestamp"
            verifySignature(canonicalRequest, signatureBase64, publicKeyBase64)
        } catch (e: Exception) {
            Timber.e(e, "Request signature verification failed")
            false
        }
    }

    /**
     * Compute SHA-256 digest of a string (for hashing, not signing).
     * @param input The string to hash.
     * @return Hex-encoded SHA-256 digest.
     */
    fun sha256(input: String): String {
        return sha256(input.toByteArray(Charsets.UTF_8))
    }

    /**
     * Compute SHA-256 digest of bytes.
     * @param data The bytes to hash.
     * @return Hex-encoded SHA-256 digest.
     */
    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 digest and return as Base64.
     */
    fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
