package com.dmvp.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class SignatureUtilsTest {

    @Test
    fun verifySignature_validEcdsaSignature_returnsTrue() {
        val keyPair = generateEcKeyPair()
        val data = "dmvp signature verification payload".toByteArray(Charsets.UTF_8)
        val signatureBase64 = signForTest(data, keyPair.private.encoded, keyPair.private.algorithm)
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val verified = SignatureUtils.verifySignature(
            data = data,
            signatureBase64 = signatureBase64,
            publicKeyBase64 = publicKeyBase64
        )

        assertTrue(verified)
    }

    @Test
    fun verifySignature_modifiedData_returnsFalse() {
        val keyPair = generateEcKeyPair()
        val originalData = "original payload".toByteArray(Charsets.UTF_8)
        val modifiedData = "modified payload".toByteArray(Charsets.UTF_8)
        val signatureBase64 = signWithPrivateKeyForTest(originalData, keyPair.private)
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val verified = SignatureUtils.verifySignature(
            data = modifiedData,
            signatureBase64 = signatureBase64,
            publicKeyBase64 = publicKeyBase64
        )

        assertFalse(verified)
    }

    @Test
    fun verifySignature_wrongPublicKey_returnsFalse() {
        val signerKeyPair = generateEcKeyPair()
        val wrongKeyPair = generateEcKeyPair()
        val data = "payload signed by another key".toByteArray(Charsets.UTF_8)
        val signatureBase64 = signWithPrivateKeyForTest(data, signerKeyPair.private)
        val wrongPublicKeyBase64 = Base64.getEncoder().encodeToString(wrongKeyPair.public.encoded)

        val verified = SignatureUtils.verifySignature(
            data = data,
            signatureBase64 = signatureBase64,
            publicKeyBase64 = wrongPublicKeyBase64
        )

        assertFalse(verified)
    }

    @Test
    fun verifySignature_invalidBase64_returnsFalse() {
        val verified = SignatureUtils.verifySignature(
            data = "payload",
            signatureBase64 = "not-base64",
            publicKeyBase64 = "not-base64"
        )

        assertFalse(verified)
    }

    @Test
    fun canonicalizePayload_sameDataDifferentKeyOrder_returnsSameJson() {
        val first = linkedMapOf(
            "b" to 2,
            "a" to 1,
            "nested" to linkedMapOf(
                "z" to true,
                "c" to "value"
            )
        )

        val second = linkedMapOf(
            "nested" to linkedMapOf(
                "c" to "value",
                "z" to true
            ),
            "a" to 1,
            "b" to 2
        )

        assertEquals(
            SignatureUtils.canonicalizePayload(first),
            SignatureUtils.canonicalizePayload(second)
        )
    }

    @Test
    fun generateNonce_returnsUniqueValues() {
        val first = SignatureUtils.generateNonce()
        val second = SignatureUtils.generateNonce()

        assertNotEquals(first, second)
    }

    @Test
    fun sha256_matchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SignatureUtils.sha256("abc")
        )
    }

    private fun generateEcKeyPair(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    private fun signWithPrivateKeyForTest(
        data: ByteArray,
        privateKey: java.security.PrivateKey
    ): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(data)

        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun signForTest(
        data: ByteArray,
        privateKeyBytes: ByteArray,
        algorithm: String
    ): String {
        require(privateKeyBytes.isNotEmpty())
        require(algorithm == "EC")

        val keyPair = generateEcKeyPair()
        return signWithPrivateKeyForTest(data, keyPair.private)
    }
}
