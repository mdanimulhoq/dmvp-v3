package com.dmvp.app.security

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import timber.log.Timber
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SignatureUtils"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val EC_KEY_ALGORITHM = "EC"
private const val SHA_256 = "SHA-256"
private const val HMAC_SHA_256 = "HmacSHA256"

object SignatureUtils {

    private val gson = Gson()

    private val iso8601Format = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun generateNonce(): String {
        return UUID.randomUUID().toString()
    }

    fun generateTimestamp(): String {
        return iso8601Format.format(Date())
    }

    fun canonicalizePayload(payload: Any): String {
        return try {
            val json = gson.toJson(payload)
            val jsonElement = JsonParser.parseString(json)
            gson.toJson(sortJson(jsonElement))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to canonicalize payload")
            gson.toJson(payload)
        }
    }

    fun signPayload(payload: Any): String? {
        return try {
            val canonicalPayload = canonicalizePayload(payload)
            signString(canonicalPayload)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sign payload")
            null
        }
    }

    fun signString(data: String): String? {
        return signBytes(data.toByteArray(Charsets.UTF_8))
    }

    fun signBytes(data: ByteArray): String? {
        return try {
            val signatureBytes = DeviceKeyManager.signData(data)
            signatureBytes?.let { Base64.getEncoder().encodeToString(it) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sign bytes")
            null
        }
    }

    fun verifySignature(
        data: String,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean {
        return verifySignature(
            data = data.toByteArray(Charsets.UTF_8),
            signatureBase64 = signatureBase64,
            publicKeyBase64 = publicKeyBase64
        )
    }

    fun verifySignature(
        data: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean {
        return try {
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)

            verifySignature(
                data = data,
                signature = signatureBytes,
                publicKeyBytes = publicKeyBytes
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decode signature or public key")
            false
        }
    }

    fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance(EC_KEY_ALGORITHM)
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            val publicKey = keyFactory.generatePublic(publicKeySpec)

            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(data)

            verifier.verify(signature)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Signature verification failed")
            false
        }
    }

    fun signRequest(payload: Any): Map<String, String> {
        val nonce = generateNonce()
        val timestamp = generateTimestamp()
        val canonicalPayload = canonicalizePayload(payload)
        val canonicalRequest = buildCanonicalRequest(
            canonicalPayload = canonicalPayload,
            nonce = nonce,
            timestamp = timestamp
        )
        val signature = signString(canonicalRequest).orEmpty()

        return mapOf(
            "signature" to signature,
            "nonce" to nonce,
            "timestamp" to timestamp
        )
    }

    fun verifyRequestSignature(
        payload: Any,
        signatureBase64: String,
        nonce: String,
        timestamp: String,
        publicKeyBase64: String
    ): Boolean {
        return try {
            val canonicalPayload = canonicalizePayload(payload)
            val canonicalRequest = buildCanonicalRequest(
                canonicalPayload = canonicalPayload,
                nonce = nonce,
                timestamp = timestamp
            )

            verifySignature(
                data = canonicalRequest,
                signatureBase64 = signatureBase64,
                publicKeyBase64 = publicKeyBase64
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Request signature verification failed")
            false
        }
    }

    fun hmacSha256(message: String, key: ByteArray): String {
        val mac = Mac.getInstance(HMAC_SHA_256)
        val secretKey = SecretKeySpec(key, HMAC_SHA_256)

        mac.init(secretKey)

        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHexString()
    }

    fun sha256(input: String): String {
        return sha256(input.toByteArray(Charsets.UTF_8))
    }

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance(SHA_256)
        return digest.digest(data).toHexString()
    }

    fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance(SHA_256)
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))

        return Base64.getEncoder().encodeToString(hash)
    }

    private fun buildCanonicalRequest(
        canonicalPayload: String,
        nonce: String,
        timestamp: String
    ): String {
        return "$canonicalPayload\n$nonce\n$timestamp"
    }

    private fun sortJson(element: JsonElement): JsonElement {
        return when {
            element.isJsonObject -> {
                val sortedObject = JsonObject()
                val sourceObject = element.asJsonObject

                sourceObject.entrySet()
                    .map { it.key }
                    .sorted()
                    .forEach { key ->
                        sortedObject.add(key, sortJson(sourceObject.get(key)))
                    }

                sortedObject
            }

            element.isJsonArray -> {
                val sortedArray = com.google.gson.JsonArray()

                element.asJsonArray.forEach { item ->
                    sortedArray.add(sortJson(item))
                }

                sortedArray
            }

            else -> element
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
