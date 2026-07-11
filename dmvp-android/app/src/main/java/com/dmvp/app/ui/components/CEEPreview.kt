/**
 * app/src/main/java/com/dmvp/app/ui/components/CEEPreview.kt
 *
 * CEEPreview composable for DMVP v3.0 Android app.
 * Displays a preview of the Canonical Evidence Envelope (CEE) before registration.
 *
 * Features:
 *   - Shows all CEE fields in a structured, forensic-style layout
 *   - Collapsible sections for detailed fields (fingerprint, attestation)
 *   - Signature status indicator (signed/unsigned)
 *   - Copy-to-clipboard for hash and ID
 *   - Dark theme optimized
 *
 * Used in:
 *   - RegisterScreen: showing CEE preview before submission
 *   - EvidenceDetailScreen: showing CEE of registered evidence
 */

package com.dmvp.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.data.model.*
import com.dmvp.app.ui.theme.*

/**
 * CEEPreview composable.
 *
 * @param cee The Canonical Evidence Envelope to preview.
 * @param modifier Modifier for the container.
 * @param showSignature Whether to show the signature status and field (default: true).
 * @param onCopyClick Optional callback when copy button is clicked (field and value).
 */
@Composable
fun CEEPreview(
    cee: CEE,
    modifier: Modifier = Modifier,
    showSignature: Boolean = true,
    onCopyClick: ((field: String, value: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var expandedSections by remember { mutableStateOf(setOf<String>()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and signature status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Canonical Evidence Envelope",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showSignature) {
                    SignatureStatusBadge(
                        isSigned = cee.signature.isNotEmpty(),
                        modifier = Modifier
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Basic fields (always visible)
            CEEFieldRow(
                label = "Evidence ID",
                value = cee.evidenceId,
                icon = Icons.Default.Info,
                onCopy = { onCopyClick?.invoke("Evidence ID", cee.evidenceId) }
            )
            CEEFieldRow(
                label = "Media Type",
                value = cee.mediaType,
                icon = if (cee.mediaType == "image") Icons.Default.Image else Icons.Default.Videocam,
                onCopy = null
            )
            CEEFieldRow(
                label = "SHA-256",
                value = cee.sha256Original.take(16) + "..." + cee.sha256Original.takeLast(8),
                fullValue = cee.sha256Original,
                icon = Icons.Default.Fingerprint,
                onCopy = { onCopyClick?.invoke("SHA-256", cee.sha256Original) }
            )
            if (cee.canonicalMediaHash != null) {
                CEEFieldRow(
                    label = "Canonical Hash",
                    value = cee.canonicalMediaHash.take(16) + "..." + cee.canonicalMediaHash.takeLast(8),
                    fullValue = cee.canonicalMediaHash,
                    icon = Icons.Default.Fingerprint,
                    onCopy = { onCopyClick?.invoke("Canonical Hash", cee.canonicalMediaHash) }
                )
            }
            CEEFieldRow(
                label = "Device Key ID",
                value = cee.signerDeviceKeyId.take(20) + "...",
                fullValue = cee.signerDeviceKeyId,
                icon = Icons.Default.DeviceHub,
                onCopy = { onCopyClick?.invoke("Device Key ID", cee.signerDeviceKeyId) }
            )
            CEEFieldRow(
                label = "Client Version",
                value = cee.clientAppVersion,
                icon = Icons.Default.Android,
                onCopy = null
            )
            CEEFieldRow(
                label = "Policy Version",
                value = cee.verificationPolicyVersion,
                icon = Icons.Default.Policy,
                onCopy = null
            )

            // Privacy flags
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Privacy Flags",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrivacyFlagChip(
                    label = "GPS",
                    enabled = cee.privacyFlags.gps
                )
                PrivacyFlagChip(
                    label = "EXIF",
                    enabled = cee.privacyFlags.exif
                )
                PrivacyFlagChip(
                    label = "Device Info",
                    enabled = cee.privacyFlags.deviceInfo
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamps
            TimestampSection(cee = cee)

            // Expandable sections
            ExpandableSection(
                title = "Fingerprint Profile",
                isExpanded = "fingerprint" in expandedSections,
                onToggle = {
                    expandedSections = if ("fingerprint" in expandedSections) {
                        expandedSections - "fingerprint"
                    } else {
                        expandedSections + "fingerprint"
                    }
                },
                icon = Icons.Default.Tune
            ) {
                FingerprintPreview(fingerprint = cee.robustFingerprintProfile)
            }

            if (cee.deviceAttestationSummary != null) {
                ExpandableSection(
                    title = "Attestation Summary",
                    isExpanded = "attestation" in expandedSections,
                    onToggle = {
                        expandedSections = if ("attestation" in expandedSections) {
                            expandedSections - "attestation"
                        } else {
                            expandedSections + "attestation"
                        }
                    },
                    icon = Icons.Default.Verified
                ) {
                    AttestationPreview(attestation = cee.deviceAttestationSummary)
                }
            }

            // Geolocation if present
            cee.geolocationClaim?.let { geo ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Geolocation: ${geo.lat}, ${geo.lng}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Signature
            if (showSignature && cee.signature.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Signature: ${cee.signature.take(20)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Signature status badge.
 */
@Composable
private fun SignatureStatusBadge(
    isSigned: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSigned) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f),
        border = if (isSigned) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            Error.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSigned) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isSigned) Success else Error,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isSigned) "Signed" else "Unsigned",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSigned) Success else Error
            )
        }
    }
}

/**
 * Single field row with label, value, and optional copy button.
 */
@Composable
private fun CEEFieldRow(
    label: String,
    value: String,
    fullValue: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(18.dp))
        }
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (label.contains("Hash") || label.contains("ID")) androidx.compose.ui.text.font.FontFamily.Monospace else null
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Privacy flag chip.
 */
@Composable
private fun PrivacyFlagChip(label: String, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (enabled) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (enabled) Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Timestamp section.
 */
@Composable
private fun TimestampSection(cee: CEE) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Timestamps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = "Registration: ${cee.registrationServerTime}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        cee.captureTimeClaim?.let {
            Text(
                text = "Capture: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        cee.trustedTimestampTokenReference?.let {
            Text(
                text = "Trusted Timestamp: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Expandable section with toggle.
 */
@Composable
private fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (isExpanded) {
            content()
        }
    }
}

/**
 * Fingerprint preview.
 */
@Composable
private fun FingerprintPreview(fingerprint: RobustFingerprint) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val fields = listOf(
            "phash" to fingerprint.phash,
            "dhash" to fingerprint.dhash,
            "blockHash" to fingerprint.blockHash,
            "keyframes" to (fingerprint.keyframes?.size?.toString() ?: "0"),
            "audio" to (fingerprint.audioFingerprint?.take(20) ?: "none")
        )
        fields.forEach { (label, value) ->
            if (value != null && value != "none") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$label:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = value.take(30) + (if (value.length > 30) "..." else ""),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (fingerprint.embedding != null) {
            Text(
                text = "Embedding: ${fingerprint.embedding.size} dims",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Attestation preview.
 */
@Composable
private fun AttestationPreview(attestation: AttestationSummary) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Valid:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = if (attestation.valid) "Yes" else "No",
                style = MaterialTheme.typography.bodySmall,
                color = if (attestation.valid) Success else Error
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Hardware:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = if (attestation.hardwareBacked) "Backed" else "Software",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Platform:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = attestation.platform ?: "unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        attestation.rooted?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rooted:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = if (it) "Yes" else "No",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it) Warning else Success
                )
            }
        }
        attestation.extra?.take(2)?.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$key:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = value.toString().take(30),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Copy to clipboard helper.
 */
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

// ================================
// Preview
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun CEEPreviewPreview() {
    DMVPTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CEEPreview(
                cee = CEE(
                    protocolVersion = "dmvp-v3.0.0",
                    evidenceId = "12345678-1234-1234-1234-123456789012",
                    mediaType = "image",
                    sha256Original = "a1b2c3d4e5f6...",
                    robustFingerprintProfile = RobustFingerprint(
                        phash = "1010101010101010",
                        dhash = "0101010101010101",
                        blockHash = "1111000011110000"
                    ),
                    fingerprintAlgorithmVersions = FingerprintAlgorithmVersions(
                        fingerprint = "v1.0",
                        similarity = "v1.0"
                    ),
                    signerDeviceKeyId = "device_12345",
                    signerPublicKeyReference = "MIIBIjANBg...",
                    signatureAlgorithm = "SHA256withECDSA",
                    deviceAttestationSummary = AttestationSummary(
                        valid = true,
                        hardwareBacked = true,
                        platform = "android",
                        appIntegrity = true,
                        rooted = false
                    ),
                    registrationServerTime = "2026-07-09T12:00:00.000Z",
                    captureTimeClaim = "2026-07-09T11:59:00.000Z",
                    geolocationClaim = GeolocationClaim(lat = 51.5074, lng = -0.1278),
                    privacyFlags = PrivacyFlags(gps = true, exif = false, deviceInfo = true),
                    clientAppVersion = "3.0.0",
                    verificationPolicyVersion = "dmvp-v3.0.0",
                    chainParentEvidenceId = null,
                    auditReference = "audit_123",
                    signature = "signature_abc123"
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

