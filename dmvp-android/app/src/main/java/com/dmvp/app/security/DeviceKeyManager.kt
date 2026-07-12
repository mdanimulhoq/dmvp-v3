package com.dmvp.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
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
private const val EC_CURVE = "secp256r1"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

object DeviceKeyManager {

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    fun hasDeviceKey(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check for device key")
            false
        }
    }

    fun getPublicKey(): String? {
        return try {
            val certificate = keyStore.getCertificate(KEY_ALIAS) ?: return null
            Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get public key")
            null
        }
    }

    fun generateDeviceKey(
        context: android.content.Context,
        attestationChallenge: ByteArray
    ): Pair<String, List<X509Certificate>>? {
        return try {
            val store = keyStore

            if (store.containsAlias(KEY_ALIAS)) {
                store.deleteEntry(KEY_ALIAS)
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false)
            }

            if (attestationChallenge.isNotEmpty()) {
                builder.setAttestationChallenge(attestationChallenge)
            }

            keyPairGenerator.initialize(builder.build())
            keyPairGenerator.generateKeyPair()

            val certificateChain = store.getCertificateChain(KEY_ALIAS)
                ?.filterIsInstance<X509Certificate>()
                ?: emptyList()

            val publicKey = getPublicKey() ?: return null

            Timber.tag(TAG).d(
                "Device key generated: curve=$EC_CURVE hardwareBacked=${isHardwareBacked()} trustTier=${getTrustTierName()} certCount=${certificateChain.size}"
            )

            Pair(publicKey, certificateChain)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate device key")
            null
        }
    }

    fun isHardwareBacked(): Boolean {
        return getHardwareBackedStatus() == true
    }

    fun getTrustTierName(): String {
        return resolveTrustTierForHardwareStatus(getHardwareBackedStatus())
    }

    fun signString(data: String): String? {
        return signData(data.toByteArray(Charsets.UTF_8))?.let { signatureBytes ->
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        }
    }

    fun signData(data: ByteArray): ByteArray? {
        return try {
            val privateKey = getPrivateKey() ?: return null
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)

            signature.initSign(privateKey)
            signature.update(data)

            signature.sign()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sign data")
            null
        }
    }

    fun getAttestationSummary(): Map<String, Any> {
        return try {
            if (!hasDeviceKey()) {
                return emptyMap()
            }

            val hardwareBackedStatus = getHardwareBackedStatus()
            val hardwareBacked = hardwareBackedStatus == true
            val trustTier = resolveTrustTierForHardwareStatus(hardwareBackedStatus)

            mapOf(
                "valid" to (hardwareBackedStatus != null),
                "hardware_backed" to hardwareBacked,
                "trust_tier" to trustTier,
                "platform" to "android",
                "key_algorithm" to KeyProperties.KEY_ALGORITHM_EC,
                "curve" to EC_CURVE,
                "signature_algorithm" to SIGNATURE_ALGORITHM,
                "inside_secure_hardware" to hardwareBacked,
                "attestation_mode" to "android_keystore_certificate_chain"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get attestation summary")
            emptyMap()
        }
    }

    fun deleteDeviceKeyForRecoveryOnly(): Boolean {
        return try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete device key")
            false
        }
    }

    private fun getHardwareBackedStatus(): Boolean? {
        return try {
            val privateKey = getPrivateKey()
            if (privateKey == null) {
                Timber.tag(TAG).w("Device key is not available for hardware-backed check")
                return null
            }

            val keyFactory = KeyFactory.getInstance(
                privateKey.algorithm,
                ANDROID_KEYSTORE
            )

            val keyInfo = keyFactory.getKeySpec(
                privateKey,
                KeyInfo::class.java
            ) as KeyInfo

            keyInfo.isInsideSecureHardware
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check hardware-backed status")
            null
        }
    }

    internal fun resolveTrustTierForHardwareStatus(hardwareBackedStatus: Boolean?): String {
        return when (hardwareBackedStatus) {
            true -> "TIER_A"
            false -> "TIER_C"
            null -> "TIER_D"
        }
    }

    private fun getPrivateKey(): PrivateKey? {
        return try {
            keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load private key")
            null
        }
    }
}
