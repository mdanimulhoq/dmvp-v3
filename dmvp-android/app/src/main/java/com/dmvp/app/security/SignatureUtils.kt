/**
 * app/src/main/java/com/dmvp/app/security/DeviceKeyManager.kt
 *
 * Manages the device's cryptographic signing key inside the Android
 * Keystore. Generates an EC key pair with hardware attestation,
 * checks hardware-backing status, and signs data for the DMVP protocol.
 */

package com.dmvp.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.dmvp.app.utils.DmvpConstants
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

private const val TAG = "DeviceKeyManager"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "dmvp_device_signing_key"

object DeviceKeyManager {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * Check whether a device signing key already exists in the Keystore.
     */
    fun hasDeviceKey(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for device key", e)
            false
        }
    }

    /**
     * Get the current public key, Base64-encoded. Returns null if no key exists.
     */
    fun getPublicKey(): String? {
        return try {
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
            Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get public key", e)
            null
        }
    }

    /**
     * Generate a new EC device key pair inside the Android Keystore,
     * requesting hardware attestation with the given challenge.
     * Returns (Base64-encoded public key, certificate chain), or null on failure.
     */
    fun generateDeviceKey(context: Context, attestationChallenge: ByteArray): Pair<String, List<X509Certificate>>? {
        return try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(attestationChallenge)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()

            val certChain = keyStore.getCertificateChain(KEY_ALIAS)
                ?.filterIsInstance<X509Certificate>()
                ?: emptyList()

            val publicKey = getPublicKey() ?: return null
            Pair(publicKey, certChain)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device key", e)
            null
        }
    }

    /**
     * Whether the current device key is backed by dedicated secure hardware
     * (StrongBox or TEE).
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return false
            val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            keyInfo.isInsideSecureHardware
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check hardware-backed status", e)
            false
        }
    }

    /**
     * Sign an arbitrary string with the device's private key.
     * Returns a Base64-encoded signature, or null on failure.
     */
    fun signString(data: String): String? {
        return try {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance(DmvpConstants.SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign string", e)
            null
        }
    }

    /**
     * Sign raw bytes with the device's private key, returning the raw
     * (non-Base64) signature bytes. Used by SignatureUtils, which handles
     * its own Base64 encoding for both String and ByteArray inputs.
     */
    fun signData(data: ByteArray): ByteArray? {
        return try {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance(DmvpConstants.SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign data", e)
            null
        }
    }
}
