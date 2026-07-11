/**
 * app/src/main/java/com/dmvp/app/utils/Extensions.kt
 *
 * Kotlin extension functions for DMVP v3.0 Android app.
 * Provides convenient extensions for common operations across the app.
 *
 * Includes extensions for:
 *   - Context: toast, color, resources
 *   - String: SHA-256, Base64, validation, formatting
 *   - File: MIME type, readable size, hash
 *   - Bitmap: compress, scale, convert
 *   - Date/Time: ISO 8601 formatting
 *   - View: visibility, show/hide
 *   - Collections: safe operations
 */

package com.dmvp.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ================================
// Context Extensions
// ================================

/**
 * Show a short toast message.
 */
fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Show a long toast message.
 */
fun Context.toastLong(message: String) {
    toast(message, Toast.LENGTH_LONG)
}

/**
 * Get a color from resources by ID (compat).
 */
fun Context.getColorCompat(colorResId: Int): Int {
    return ContextCompat.getColor(this, colorResId)
}

/**
 * Open a URL in browser.
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
}

/**
 * Share text via intent.
 */
fun Context.shareText(text: String, subject: String = "") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(Intent.createChooser(intent, "Share via"))
}

// ================================
// String Extensions
// ================================

/**
 * Compute SHA-256 hash of the string (UTF-8).
 */
fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

/**
 * Check if string is a valid SHA-256 hex hash (64 chars, hex).
 */
fun String.isValidSha256(): Boolean {
    return RegexPatterns.SHA256_HEX.matches(this)
}

/**
 * Encode string to Base64.
 */
fun String.toBase64(): String {
    return Base64.encodeToString(this.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

/**
 * Decode Base64 string to UTF-8 string.
 */
fun String.fromBase64(): String? {
    return try {
        String(Base64.decode(this, Base64.NO_WRAP), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

/**
 * Truncate a string to max length and add ellipsis if needed.
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (this.length <= maxLength) this else this.take(maxLength) + ellipsis
}

/**
 * Convert string to UUID or null if invalid.
 */
fun String.toUuidOrNull(): UUID? {
    return try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Check if string is a valid UUID.
 */
fun String.isValidUuid(): Boolean {
    return RegexPatterns.UUID.matches(this)
}

/**
 * Check if string is a valid ISO 8601 timestamp.
 */
fun String.isValidIso8601(): Boolean {
    return RegexPatterns.ISO8601.matches(this)
}

/**
 * Parse ISO 8601 string to Date.
 */
fun String.parseIso8601(): Date? {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(this)
    } catch (e: Exception) {
        null
    }
}

// ================================
// File Extensions
// ================================

/**
 * Get MIME type of a file based on extension.
 */
fun File.getMimeType(): String {
    val extension = this.extension.lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "3gp" -> "video/3gpp"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}

/**
 * Get human-readable file size (e.g., "12.5 MB").
 */
fun File.getReadableSize(): String {
    val size = this.length()
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Compute SHA-256 hash of a file.
 */
fun File.sha256(): String {
    return java.security.MessageDigest.getInstance("SHA-256").let { digest ->
        this.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Check if file is an image based on MIME type.
 */
fun File.isImage(): Boolean {
    val mime = this.getMimeType()
    return mime.startsWith("image/")
}

/**
 * Check if file is a video based on MIME type.
 */
fun File.isVideo(): Boolean {
    val mime = this.getMimeType()
    return mime.startsWith("video/")
}

// ================================
// Bitmap Extensions
// ================================

/**
 * Compress bitmap to JPEG byte array.
 */
fun Bitmap.toJpegBytes(quality: Int = 90): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}

/**
 * Compress bitmap to PNG byte array.
 */
fun Bitmap.toPngBytes(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

/**
 * Scale bitmap to target width/height maintaining aspect ratio.
 */
fun Bitmap.scale(targetWidth: Int, targetHeight: Int): Bitmap {
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

/**
 * Scale bitmap proportionally to fit within max dimensions.
 */
fun Bitmap.scaleWithin(maxWidth: Int, maxHeight: Int): Bitmap {
    val width = this.width
    val height = this.height
    val ratio = if (width > height) {
        maxWidth.toFloat() / width
    } else {
        maxHeight.toFloat() / height
    }
    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

/**
 * Convert bitmap to Base64 string (JPEG).
 */
fun Bitmap.toBase64(quality: Int = 90): String {
    val bytes = this.toJpegBytes(quality)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

// ================================
// Date/Time Extensions
// ================================

/**
 * Format Date to ISO 8601 string.
 */
fun Date.toIso8601(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return format.format(this)
}

/**
 * Convert Date to human-readable format.
 */
fun Date.toHumanReadable(format: String = "yyyy-MM-dd HH:mm:ss"): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}

/**
 * Get time ago string (e.g., "5 minutes ago").
 */
fun Date.timeAgo(): String {
    val now = Date()
    val diff = now.time - this.time
    return when {
        diff < 60_000 -> "just now"
        diff < 60_000 * 60 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} minutes ago"
        diff < 60_000 * 60 * 24 -> "${TimeUnit.MILLISECONDS.toHours(diff)} hours ago"
        diff < 60_000 * 60 * 24 * 7 -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
        else -> this.toHumanReadable("MMM d, yyyy")
    }
}

/**
 * Get current time as ISO 8601.
 */
fun currentIso8601(): String {
    return Date().toIso8601()
}

// ================================
// Collection Extensions
// ================================

/**
 * Get element or null if index out of bounds.
 */
fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in indices) this[index] else null
}

/**
 * Get first element or null if empty.
 */
fun <T> List<T>.firstOrNull(): T? {
    return if (this.isNotEmpty()) this[0] else null
}

/**
 * Check if list is not null or empty.
 */
fun <T> Collection<T>?.isNotNullOrEmpty(): Boolean {
    return this != null && this.isNotEmpty()
}

// ================================
// View Extensions
// ================================

/**
 * Show the view (set visibility to VISIBLE).
 */
fun View.show() {
    this.visibility = View.VISIBLE
}

/**
 * Hide the view (set visibility to GONE).
 */
fun View.hide() {
    this.visibility = View.GONE
}

/**
 * Make view invisible (still occupies space).
 */
fun View.invisible() {
    this.visibility = View.INVISIBLE
}

/**
 * Toggle visibility between VISIBLE and GONE.
 */
fun View.toggleVisibility() {
    this.visibility = if (this.visibility == View.VISIBLE) View.GONE else View.VISIBLE
}

// ================================
// Uri Extensions
// ================================

/**
 * Get file from Uri (requires FileProvider or content resolver).
 */
fun Uri.toFile(context: Context): File? {
    val cursor = context.contentResolver.query(this, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst()) {
            val fileName = it.getString(nameIndex)
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)
            val inputStream = context.contentResolver.openInputStream(this)
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return file
        }
    }
    return null
}

// ================================
// Number Extensions
// ================================

/**
 * Format a number as a percentage string.
 */
fun Double.toPercent(decimalPlaces: Int = 0): String {
    return String.format("%.${decimalPlaces}f%%", this * 100)
}

/**
 * Clamp a value between min and max.
 */
fun Double.clamp(min: Double, max: Double): Double {
    return if (this < min) min else if (this > max) max else this
}

fun Int.clamp(min: Int, max: Int): Int {
    return if (this < min) min else if (this > max) max else this
}

// ================================
// Map Extensions
// ================================

/**
 * Safely get a string value from map or default.
 */
fun Map<String, Any>.getString(key: String, default: String = ""): String {
    return (this[key] as? String) ?: default
}

/**
 * Safely get a boolean value from map or default.
 */
fun Map<String, Any>.getBoolean(key: String, default: Boolean = false): Boolean {
    return (this[key] as? Boolean) ?: default
}

/**
 * Safely get an int value from map or default.
 */
fun Map<String, Any>.getInt(key: String, default: Int = 0): Int {
    return when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

/**
 * Safely get a long value from map or default.
 */
fun Map<String, Any>.getLong(key: String, default: Long = 0L): Long {
    return when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}
