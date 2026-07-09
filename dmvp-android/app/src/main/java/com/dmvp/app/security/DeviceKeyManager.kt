/**
 * app/src/main/java/com/dmvp/app/security/DeviceKeyManager.kt
 *
 * Device key management for DMVP v3.0 Android app.
 * Uses Android Keystore for hardware-backed EC P-256 key generation and signing.
 * Provides:
 *   - Key generation with attestation (where supported)
 *   - Signing of CEE payloads
 *   - Public key export (Base64 encoded)
 *   - Key existence checks
 *   - Attestation extraction
 *
 * Supports Android 8.0 (API 26) and above, with stronger security on API 28+.
 */

package com.dmvp.app.security

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.*
import javax.security.auth.x500.X500Principal

/**
 * Device key manager singleton.
 * Handles all Keystore operations for device-bound signing keys.
 */
object DeviceKeyManager {
    private const val TAG = "DeviceKeyManager"
    private const val KEYSTORE_ALIAS = "dmvp_device_key"
    private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val CURVE = "secp256r1" // NIST P-256
    private const val KEY_SIZE = 256

    // Lazy initialization of the keystore
    private val keystore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    /**
     * Check if a device key exists in the keystore.
     * @return true if key exists, false otherwise.
     */
    fun hasDeviceKey(): Boolean {
        return try {
            keystore.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking key existence", e)
            false
        }
    }

    /**
     * Generate a new device key in the Android Keystore.
     * Uses hardware-backed storage when available (StrongBox for API 28+).
     *
     * @param context Context for accessing Keystore.
     * @param attestationChallenge Optional challenge bytes for attestation (for KeyAttestation).
     * @return Pair of (publicKeyPem, attestationCertChain) or null if generation fails.
     */
    fun generateDeviceKey(
        context: Context,
        attestationChallenge: ByteArray? = null
    ): Pair<String, List<X509Certificate>>? {
        // Check if key already exists
        if (hasDeviceKey()) {
            Log.w(TAG, "Device key already exists. Use getPublicKey() instead.")
            return getPublicKeyWithAttestation()
        }

        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, "AndroidKeyStore")

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use KeyGenParameterSpec for API 23+
                val specBuilder = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false) // No screen lock required for MVP
                    .setKeyValidityStart(Date())
                    .setKeyValidityEnd(Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)) // 1 year

                // Try to use StrongBox if available (API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        specBuilder.setIsStrongBoxBacked(true)
                    } catch (e: StrongBoxUnavailableException) {
                        Log.w(TAG, "StrongBox not available, falling back to regular Keystore")
                    }
                }

                // Attestation support (API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attestationChallenge != null) {
                    try {
                        specBuilder.setAttestationChallenge(attestationChallenge)
                    } catch (e: Exception) {
                        Log.w(TAG, "Attestation not supported", e)
                    }
                }

                specBuilder.build()
            } else {
                // API < 23: use KeyPairGeneratorSpec
                val startDate = Calendar.getInstance()
                val endDate = Calendar.getInstance()
                endDate.add(Calendar.YEAR, 1)

                @Suppress("DEPRECATION")
                KeyPairGeneratorSpec.Builder(context)
                    .setAlias(KEYSTORE_ALIAS)
                    .setSubject(X500Principal("CN=DMVP Device Key"))
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(startDate.time)
                    .setEndDate(endDate.time)
                    .setKeySize(KEY_SIZE)
                    .setKeyType("EC")
                    .build()
            }

            keyPairGenerator.initialize(builder)
            val keyPair = keyPairGenerator.generateKeyPair()

            // Extract public key and certificate
            val publicKey = keyPair.public
            val certificate = keystore.getCertificate(KEYSTORE_ALIAS) as? X509Certificate

            if (certificate == null) {
                Log.e(TAG, "Failed to get certificate from Keystore")
                return null
            }

            // Build certificate chain
            val certChain = getCertificateChain()

            val publicKeyEncoded = Base64.getEncoder().encodeToString(publicKey.encoded)
            Log.d(TAG, "Device key generated successfully. Public key: ${publicKeyEncoded.take(20)}...")

            Pair(publicKeyEncoded, certChain)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device key", e)
            null
        }
    }

    /**
     * Get the device key alias used in the Keystore.
     */
    fun getKeyAlias(): String = KEYSTORE_ALIAS

    /**
     * Get the public key from the Keystore (if it exists).
     * @return Base64-encoded public key, or null if not found.
     */
    fun getPublicKey(): String? {
        return try {
            val publicKey = keystore.getCertificate(KEYSTORE_ALIAS)?.publicKey
            publicKey?.encoded?.let { Base64.getEncoder().encodeToString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            null
        }
    }

    /**
     * Get the public key with attestation certificates.
     * @return Pair of (publicKeyBase64, certChain) or null.
     */
    fun getPublicKeyWithAttestation(): Pair<String, List<X509Certificate>>? {
        return try {
            val publicKey = keystore.getCertificate(KEYSTORE_ALIAS)?.publicKey
            if (publicKey == null) {
                Log.e(TAG, "No public key found")
                return null
            }
            val certChain = getCertificateChain()
            val publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)
            Pair(publicKeyBase64, certChain)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key with attestation", e)
            null
        }
    }

    /**
     * Get the X.509 certificate chain for the device key.
     * @return List of X509Certificate objects (ordered: leaf first).
     */
    fun getCertificateChain(): List<X509Certificate> {
        val chain = mutableListOf<X509Certificate>()
        try {
            // Get the certificate chain from Keystore
            @Suppress("UNCHECKED_CAST")
            val certs = keystore.getCertificateChain(KEYSTORE_ALIAS) as? Array<X509Certificate>
            if (certs != null) {
                chain.addAll(certs)
            } else {
                // Fallback: just the leaf certificate
                val cert = keystore.getCertificate(KEYSTORE_ALIAS) as? X509Certificate
                if (cert != null) {
                    chain.add(cert)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate chain", e)
        }
        return chain
    }

    /**
     * Get the attestation certificate chain if available.
     * The attestation chain is the full chain including the leaf certificate.
     * @return List of X509Certificate objects (leaf first) or empty list.
     */
    fun getAttestationChain(): List<X509Certificate> {
        return getCertificateChain()
    }

    /**
     * Extract attestation data from the certificate chain.
     * On Android 8.0+ (API 26+), the Keystore includes attestation extensions.
     * For MVP, we return a basic summary.
     *
     * @return Map containing attestation details.
     */
    fun getAttestationSummary(): Map<String, Any> {
        val certs = getCertificateChain()
        val leaf = certs.firstOrNull()

        val summary = mutableMapOf<String, Any>()
        summary["valid"] = true
        summary["hardware_backed"] = isHardwareBacked()
        summary["platform"] = "android"
        summary["api_level"] = Build.VERSION.SDK_INT

        if (leaf != null) {
            try {
                summary["subject"] = leaf.subjectX500Principal?.name ?: "unknown"
                summary["issuer"] = leaf.issuerX500Principal?.name ?: "unknown"
                summary["serial"] = leaf.serialNumber?.toString(16) ?: "unknown"
                summary["not_before"] = leaf.notBefore?.time ?: 0L
                summary["not_after"] = leaf.notAfter?.time ?: 0L
            } catch (e: Exception) {
                // Ignore
            }

            // Check for attestation extension (Android Key Attestation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // For API 28+, we can extract KeyDescription from the certificate
                    // This is done via the KeyInfo class which can be obtained from the private key
                    val privateKey = keystore.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
                    if (privateKey != null) {
                        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, "AndroidKeyStore")
                        val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                        summary["key_security_level"] = keyInfo.securityLevel.name
                        summary["inside_secure_hardware"] = keyInfo.isInsideSecureHardware
                        summary["user_authentication_required"] = keyInfo.isUserAuthenticationRequired
                    }
                } catch (e: Exception) {
                    // KeyInfo not available
                }
            }
        }

        return summary
    }

    /**
     * Check if the key is hardware-backed.
     * @return true if hardware-backed, false otherwise.
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val privateKey = keystore.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
            if (privateKey != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, "AndroidKeyStore")
                val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                keyInfo.isInsideSecureHardware
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check hardware backing", e)
            false
        }
    }

    /**
     * Sign data using the device private key.
     *
     * @param data The data to sign (byte array).
     * @return The signature as a byte array, or null if signing fails.
     */
    fun signData(data: ByteArray): ByteArray? {
        return try {
            if (!hasDeviceKey()) {
                Log.e(TAG, "No device key found. Generate one first.")
                return null
            }

            val privateKey = keystore.getKey(KEYSTORE_ALIAS, null) as? PrivateKey
            if (privateKey == null) {
                Log.e(TAG, "Failed to get private key")
                return null
            }

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign data", e)
            null
        }
    }

    /**
     * Sign a string using the device private key.
     *
     * @param data String to sign (UTF-8 encoded).
     * @return Base64-encoded signature, or null if signing fails.
     */
    fun signString(data: String): String? {
        val signature = signData(data.toByteArray(Charsets.UTF_8))
        return signature?.let { Base64.getEncoder().encodeToString(it) }
    }

    /**
     * Delete the device key from the Keystore.
     * Use with caution - this cannot be undone.
     * @return true if deleted, false otherwise.
     */
    fun deleteDeviceKey(): Boolean {
        return try {
            keystore.deleteEntry(KEYSTORE_ALIAS)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete device key", e)
            false
        }
    }

    /**
     * Check if the device key is available and has valid attributes.
     * For MVP, we check if the key exists and is not expired.
     *
     * @return true if the key is usable, false otherwise.
     */
    fun isKeyUsable(): Boolean {
        return try {
            if (!hasDeviceKey()) return false

            // Check certificate validity
            val cert = keystore.getCertificate(KEYSTORE_ALIAS) as? X509Certificate
            if (cert != null) {
                try {
                    cert.checkValidity()
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Certificate is invalid or expired", e)
                    return false
                }
            }
            true // If no certificate, assume usable (should not happen)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check key usability", e)
            false
        }
    }

    /**
     * Get key information for display/debugging.
     * @return Map of key attributes.
     */
    fun getKeyInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        info["exists"] = hasDeviceKey()
        info["usability"] = isKeyUsable()
        info["hardware_backed"] = isHardwareBacked()

        try {
            val cert = keystore.getCertificate(KEYSTORE_ALIAS) as? X509Certificate
            if (cert != null) {
                info["algorithm"] = cert.publicKey.algorithm
                info["subject"] = cert.subjectX500Principal?.name ?: "unknown"
                info["not_after"] = cert.notAfter?.time ?: 0L
                info["not_before"] = cert.notBefore?.time ?: 0L
                // Get key size if possible
                try {
                    val keySize = if (cert.publicKey is java.security.interfaces.ECPublicKey) {
                        (cert.publicKey as java.security.interfaces.ECPublicKey).params.curve.field.fieldSize
                    } else {
                        "unknown"
                    }
                    info["key_size"] = keySize
                } catch (_: Exception) {
                    info["key_size"] = "unknown"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate info", e)
        }

        return info
    }

    /**
     * Reset the keystore state (delete all DMVP keys).
     * Use with caution - this will remove the device identity.
     * @return true if all keys deleted, false otherwise.
     */
    fun resetKeystore(): Boolean {
        return try {
            if (hasDeviceKey()) {
                deleteDeviceKey()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset keystore", e)
            false
        }
    }
}
