package com.dmvp.app.security

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * C2PA (Coalition for Content Provenance and Authenticity) Manifest Detector
 * TDD v5 Phase 1 Step 1.6
 * 
 * Detects C2PA Content Credentials in image files.
 * Checks for JPEG APP11 chunk or PNG c2PA chunk.
 * 
 * Samsung Galaxy S25, Google Pixel 10 natively sign with C2PA.
 * Detection provides strong provenance proof.
 * 
 * Important: C2PA absence does NOT mean rejection - it's just a signal.
 */
object C2paDetector {

    // C2PA JPEG marker: APP11 (0xEB)
    private const val JPEG_APP11_MARKER = 0xEB.toByte()
    
    // C2PA PNG chunk type: "c2PA"
    private const val C2PA_PNG_CHUNK = "c2PA"
    
    // C2PA manifest box type (ISOBMFF/MP4)
    private const val C2PA_BOX_TYPE = "c2pa"

    /**
     * C2PA detection result
     */
    data class C2paResult(
        val present: Boolean,
        val verified: Boolean = false,
        val manifestHash: String? = null,
        val claimGenerator: String? = null,
        val format: String? = null,
        val error: String? = null
    )

    /**
     * Detect C2PA manifest in image file
     * 
     * @param file Image file to check
     * @return C2paResult with detection status
     */
    fun detect(file: File): C2paResult {
        if (!file.exists() || !file.canRead()) {
            return C2paResult(present = false, error = "File not accessible")
        }

        return try {
            FileInputStream(file).use { inputStream ->
                val header = ByteArray(16)
                inputStream.read(header)
                
                when {
                    isJpeg(header) -> detectJpegC2pa(inputStream)
                    isPng(header) -> detectPngC2pa(inputStream)
                    else -> C2paResult(present = false, error = "Unsupported format")
                }
            }
        } catch (e: Exception) {
            C2paResult(present = false, error = e.message)
        }
    }

    /**
     * Detect C2PA manifest from byte array
     * 
     * @param data Image data
     * @return C2paResult with detection status
     */
    fun detect(data: ByteArray): C2paResult {
        if (data.isEmpty()) {
            return C2paResult(present = false, error = "Empty data")
        }

        return try {
            when {
                isJpeg(data) -> detectJpegC2pa(data)
                isPng(data) -> detectPngC2pa(data)
                else -> C2paResult(present = false, error = "Unsupported format")
            }
        } catch (e: Exception) {
            C2paResult(present = false, error = e.message)
        }
    }

    /**
     * Check if data is JPEG format
     */
    private fun isJpeg(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()
    }

    /**
     * Check if data is PNG format
     */
    private fun isPng(data: ByteArray): Boolean {
        return data.size >= 8 &&
            data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() && // P
            data[2] == 0x4E.toByte() && // N
            data[3] == 0x47.toByte() && // G
            data[4] == 0x0D.toByte() &&
            data[5] == 0x0A.toByte() &&
            data[6] == 0x1A.toByte() &&
            data[7] == 0x0A.toByte()
    }

    /**
     * Detect C2PA in JPEG file by scanning for APP11 marker
     */
    private fun detectJpegC2pa(inputStream: InputStream): C2paResult {
        val buffer = ByteArray(8192)
        var bytesRead = inputStream.read(buffer)
        
        while (bytesRead != -1) {
            // Look for APP11 marker (0xFF 0xEB)
            for (i in 0 until bytesRead - 1) {
                if (buffer[i] == 0xFF.toByte() && buffer[i + 1] == JPEG_APP11_MARKER) {
                    // Found C2PA marker
                    return C2paResult(
                        present = true,
                        verified = false, // Would need full manifest parsing to verify
                        format = "JPEG"
                    )
                }
            }
            bytesRead = inputStream.read(buffer)
        }
        
        return C2paResult(present = false, format = "JPEG")
    }

    /**
     * Detect C2PA in JPEG byte array
     */
    private fun detectJpegC2pa(data: ByteArray): C2paResult {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == JPEG_APP11_MARKER) {
                return C2paResult(
                    present = true,
                    verified = false,
                    format = "JPEG"
                )
            }
        }
        return C2paResult(present = false, format = "JPEG")
    }

    /**
     * Detect C2PA in PNG file by scanning for c2PA chunk
     */
    private fun detectPngC2pa(inputStream: InputStream): C2paResult {
        val buffer = ByteArray(8192)
        var bytesRead = inputStream.read(buffer)
        
        while (bytesRead != -1) {
            // Look for "c2PA" chunk type
            val bufferStr = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)
            if (bufferStr.contains(C2PA_PNG_CHUNK)) {
                return C2paResult(
                    present = true,
                    verified = false,
                    format = "PNG"
                )
            }
            bytesRead = inputStream.read(buffer)
        }
        
        return C2paResult(present = false, format = "PNG")
    }

    /**
     * Detect C2PA in PNG byte array
     */
    private fun detectPngC2pa(data: ByteArray): C2paResult {
        val dataStr = String(data, Charsets.ISO_8859_1)
        if (dataStr.contains(C2PA_PNG_CHUNK)) {
            return C2paResult(
                present = true,
                verified = false,
                format = "PNG"
            )
        }
        return C2paResult(present = false, format = "PNG")
    }

    /**
     * Compute hash of C2PA manifest for storage
     * (Simplified - in production would extract and hash the actual manifest)
     */
    fun computeManifestHash(data: ByteArray): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(data).toHexString()
        } catch (e: Exception) {
            null
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
