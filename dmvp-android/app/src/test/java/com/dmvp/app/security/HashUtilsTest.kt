package com.dmvp.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class HashUtilsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sha256_emptyByteArray_matchesKnownVector() {
        val actual = HashUtils.sha256(ByteArray(0))

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            actual
        )
    }

    @Test
    fun sha256_abcString_matchesKnownVector() {
        val actual = HashUtils.sha256("abc")

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            actual
        )
    }

    @Test
    fun sha256_quickBrownFox_matchesKnownVector() {
        val actual = HashUtils.sha256("The quick brown fox jumps over the lazy dog")

        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            actual
        )
    }

    @Test
    fun sha256_file_matchesByteArrayHash() {
        val bytes = "dmvp deterministic file hash test".toByteArray(Charsets.UTF_8)
        val file = temporaryFolder.newFile("sample-media.bin")

        file.writeBytes(bytes)

        assertEquals(
            HashUtils.sha256(bytes),
            HashUtils.sha256(file)
        )
    }

    @Test
    fun sha256_sameFileTwice_returnsSameHash() {
        val file = temporaryFolder.newFile("same-file.bin")
        file.writeText("same file must always produce same sha256")

        val firstHash = HashUtils.sha256(file)
        val secondHash = HashUtils.sha256(file)

        assertEquals(firstHash, secondHash)
    }

    @Test
    fun sha256_differentFiles_returnDifferentHashes() {
        val firstFile = temporaryFolder.newFile("first.bin")
        val secondFile = temporaryFolder.newFile("second.bin")

        firstFile.writeText("first file")
        secondFile.writeText("second file")

        assertNotEquals(
            HashUtils.sha256(firstFile),
            HashUtils.sha256(secondFile)
        )
    }

    @Test
    fun sha256_inputStream_matchesByteArrayHash() {
        val bytes = "stream hashing must match byte array hashing".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(bytes)

        assertEquals(
            HashUtils.sha256(bytes),
            HashUtils.sha256(inputStream)
        )
    }

    @Test
    fun hammingDistance_identicalHexHashes_returnsZero() {
        val hash = "0f0f"

        assertEquals(
            0,
            HashUtils.hammingDistance(hash, hash)
        )
    }

    @Test
    fun hammingDistance_hexHashes_countsDifferentBits() {
        assertEquals(
            8,
            HashUtils.hammingDistance("00", "ff")
        )
    }
}
