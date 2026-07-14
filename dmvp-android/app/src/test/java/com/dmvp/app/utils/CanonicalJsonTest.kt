package com.dmvp.app.utils

import com.dmvp.app.data.model.CEE
import com.dmvp.app.data.model.FingerprintAlgorithmVersions
import com.dmvp.app.data.model.PrivacyFlags
import com.dmvp.app.data.model.RobustFingerprint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalJsonTest {

    @Test
    fun canonicalize_sortsKeysRecursivelyAndRemovesWhitespace() {
        val payload = linkedMapOf(
            "z" to 1,
            "a" to linkedMapOf(
                "b" to true,
                "a" to "first"
            ),
            "m" to listOf(
                linkedMapOf(
                    "d" to 4,
                    "c" to 3
                )
            )
        )

        val canonical = CanonicalJson.canonicalize(payload)

        assertEquals(
            """{"a":{"a":"first","b":true},"m":[{"c":3,"d":4}],"z":1}""",
            canonical
        )
    }

    @Test
    fun canonicalize_excludeSignature_removesSignatureField() {
        val payload = linkedMapOf(
            "z" to "last",
            "signature" to "base64-signature",
            "a" to "first"
        )

        val canonical = CanonicalJson.canonicalize(
            value = payload,
            excludeSignature = true
        )

        assertEquals("""{"a":"first","z":"last"}""", canonical)
        assertFalse(canonical.contains("signature"))
    }

    @Test
    fun canonicalize_ceeUsesSerializedNamesAndStableUtf8Bytes() {
        val cee = CEE(
            protocolVersion = "dmvp-v3.0.0",
            evidenceId = "11111111-1111-4111-8111-111111111111",
            mediaType = "image",
            sha256Original = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            canonicalMediaHash = null,
            robustFingerprintProfile = RobustFingerprint(
                phash = "abcd",
                dhash = "1234"
            ),
            fingerprintAlgorithmVersions = FingerprintAlgorithmVersions(
                fingerprint = "v1.0",
                similarity = "v1.0",
                normalization = "v1.0"
            ),
            signerDeviceKeyId = "device_test",
            signerPublicKeyReference = "device_test",
            signatureAlgorithm = "SHA256withECDSA",
            deviceAttestationSummary = null,
            registrationServerTime = "2026-07-14T00:00:00.000Z",
            trustedTimestampTokenReference = null,
            captureTimeClaim = null,
            geolocationClaim = null,
            privacyFlags = PrivacyFlags(
                gps = false,
                exif = false,
                deviceInfo = false
            ),
            clientAppVersion = "3.0.0",
            verificationPolicyVersion = "policy-v3.0.0",
            chainParentEvidenceId = null,
            auditReference = "audit-test",
            signature = "signature-to-exclude"
        )

        val canonical = CanonicalJson.canonicalize(
            value = cee,
            excludeSignature = true
        )

        assertTrue(canonical.startsWith("""{"audit_reference":"audit-test""""))
        assertTrue(canonical.contains(""""protocol_version":"dmvp-v3.0.0""""))
        assertTrue(canonical.contains(""""verification_policy_version":"policy-v3.0.0""""))
        assertFalse(canonical.contains("signature-to-exclude"))
        assertFalse(canonical.contains("\n"))
        assertFalse(canonical.contains(" "))

        assertArrayEquals(
            canonical.toByteArray(Charsets.UTF_8),
            CanonicalJson.canonicalizeToUtf8(cee, excludeSignature = true)
        )
    }
}
