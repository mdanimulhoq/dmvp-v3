/**
 * app/src/main/java/com/dmvp/app/ui/components/VerdictCard.kt
 *
 * VerdictCard UI component for DMVP v3.0 Android app.
 * Displays multi-axis verification results in a professional, forensic-style card.
 *
 * Features:
 *   - Overall verdict status with color coding
 *   - Summary UI score (0-100)
 *   - Four verdict axes: Integrity, Provenance, Similarity, Evidence Quality
 *   - Transformation indicators as chips
 *   - Warnings section
 *   - Matched evidence references
 *   - Expandable/collapsible detail view
 *   - Dark theme optimized
 *
 * Used in:
 *   - VerifyScreen: showing verification results
 *   - VerdictDetailScreen: expanded verdict details
 *   - SearchScreen: showing match results
 */

package com.dmvp.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.data.model.*
import com.dmvp.app.ui.theme.*

/**
 * Display mode for the verdict card.
 */
enum class VerdictCardMode {
    COMPACT,    // Small preview, used in search results or lists
    STANDARD,   // Standard view with all axes
    EXPANDED    // Full detail view with all information
}

/**
 * Overall verdict status.
 */
enum class VerdictStatus(val color: Color, val label: String) {
    VERIFIED(Success, "Verified"),
    PARTIAL(Warning, "Partial"),
    // ── Step 3.5: Changed "Unverified" to "Failed" ──
    UNVERIFIED(Error, "Failed"),
    UNKNOWN(Color.Gray, "Unknown")
}

/**
 * Main VerdictCard composable.
 *
 * @param verdict The multi-axis verdict to display.
 * @param mode Display mode (compact, standard, expanded).
 * @param modifier Modifier for the card.
 * @param onEvidenceClick Callback when a matched evidence is clicked.
 * @param onExpandToggle Callback when expand/collapse is toggled (for expanded mode).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VerdictCard(
    verdict: MultiAxisVerdict,
    mode: VerdictCardMode = VerdictCardMode.STANDARD,
    modifier: Modifier = Modifier,
    onEvidenceClick: ((String) -> Unit)? = null,
    onExpandToggle: ((Boolean) -> Unit)? = null
) {
    val status = getVerdictStatus(verdict)
    val isExpanded = remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = status.color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
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
            // Header: Status and Score
            VerdictHeader(
                verdict = verdict,
                status = status,
                mode = mode,
                onExpandToggle = if (mode == VerdictCardMode.EXPANDED) {
                    { isExpanded.value = !isExpanded.value }
                } else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Step 3.5: Forensic meta strip (evidence_id / device / timestamp) ──
            if (mode != VerdictCardMode.COMPACT) {
                Spacer(modifier = Modifier.height(8.dp))
                VerdictMetaStrip(verdict = verdict)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verdict axes (show all in standard/expanded, only integrity in compact)
            if (mode != VerdictCardMode.COMPACT) {
                VerdictAxes(verdict = verdict)
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                // Compact: show only integrity verdict
                CompactVerdictSummary(verdict = verdict)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Transformation indicators (if any)
            if (verdict.transformationIndicators.isNotEmpty() && mode != VerdictCardMode.COMPACT) {
                TransformationIndicators(indicators = verdict.transformationIndicators)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Warnings (if any)
            if (verdict.warnings.isNotEmpty() && mode != VerdictCardMode.COMPACT) {
                WarningsSection(warnings = verdict.warnings)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Matched evidence (show in standard/expanded)
            if (verdict.matchedEvidenceList.isNotEmpty() && mode != VerdictCardMode.COMPACT) {
                MatchedEvidenceSection(
                    matchedEvidence = verdict.matchedEvidenceList,
                    onEvidenceClick = onEvidenceClick
                )
            }

            // Expanded content (for EXPANDED mode)
            if (mode == VerdictCardMode.EXPANDED) {
                AnimatedVisibility(
                    visible = isExpanded.value,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                        ExpandedVerdictDetails(verdict = verdict)
                    }
                }
            }
        }
    }
}

/**
 * Verdict header: status badge and summary score.
 */
@Composable
private fun VerdictHeader(
    verdict: MultiAxisVerdict,
    status: VerdictStatus,
    mode: VerdictCardMode,
    onExpandToggle: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(status.color)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.titleMedium,
                color = status.color
            )
            if (mode == VerdictCardMode.COMPACT) {
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = verdict.integrityVerdict?.name?.replace("_", " ") ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary UI score
            verdict.summaryUiScore?.let { score ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                score >= 70 -> Success.copy(alpha = 0.2f)
                                score >= 40 -> Warning.copy(alpha = 0.2f)
                                else -> Error.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = when {
                            score >= 70 -> Success
                            score >= 40 -> Warning
                            else -> Error
                        }
                    )
                }
            }

            // Expand toggle (for expanded mode)
            if (onExpandToggle != null) {
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ── Step 3.5: Forensic meta strip ──────────────────────────────────────────

/**
 * Step 3.5 — Forensic meta strip:
 * evidence_id (truncated), signer_device_key_id (truncated), registration timestamp.
 * Reads from matchedEvidenceList[0] and metadata; gracefully falls back to "—".
 */
@Composable
private fun VerdictMetaStrip(verdict: MultiAxisVerdict) {
    val firstMatch = verdict.matchedEvidenceList.firstOrNull()
    val evidenceId = firstMatch?.evidenceId
    val timestamp = firstMatch?.timestamp
        ?: verdict.metadata?.get("registered_at") as? String
    val deviceKeyId = verdict.metadata?.get("signer_device_key_id") as? String

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VerdictMetaRow(
            label = "Evidence",
            value = evidenceId?.let { truncateMiddle(it, 8, 6) } ?: "—"
        )
        VerdictMetaRow(
            label = "Signed by",
            value = deviceKeyId?.let { truncateMiddle(it, 10, 6) } ?: "—"
        )
        VerdictMetaRow(
            label = "Registered",
            value = timestamp ?: "—"
        )
    }
}

@Composable
private fun VerdictMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f)
        )
    }
}

private fun truncateMiddle(s: String, headLen: Int, tailLen: Int): String {
    if (s.length <= headLen + tailLen + 1) return s
    return s.take(headLen) + "…" + s.takeLast(tailLen)
}

/**
 * Compact verdict summary (used in compact mode).
 */
@Composable
private fun CompactVerdictSummary(verdict: MultiAxisVerdict) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VerdictAxisCompact(
            label = "Integrity",
            value = verdict.integrityVerdict?.name?.replace("_", " ") ?: "—",
            isPositive = verdict.integrityVerdict == IntegrityVerdict.EXACT_MATCH ||
                    verdict.integrityVerdict == IntegrityVerdict.CANONICAL_MATCH
        )
        VerdictAxisCompact(
            label = "Provenance",
            value = verdict.provenanceVerdict?.name?.replace("_", " ") ?: "—",
            isPositive = verdict.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE
        )
        VerdictAxisCompact(
            label = "Similarity",
            value = verdict.similarityVerdict?.name?.replace("_", " ") ?: "—",
            isPositive = verdict.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ||
                    verdict.similarityVerdict == SimilarityVerdict.PROBABLE_DERIVATIVE
        )
    }
}

@Composable
private fun VerdictAxisCompact(label: String, value: String, isPositive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value.take(12),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = if (isPositive) Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Full verdict axes display.
 */
@Composable
private fun VerdictAxes(verdict: MultiAxisVerdict) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VerdictAxisRow(
            label = "Integrity",
            value = verdict.integrityVerdict?.name?.replace("_", " ") ?: "—",
            icon = when (verdict.integrityVerdict) {
                IntegrityVerdict.EXACT_MATCH -> Icons.Default.CheckCircle
                IntegrityVerdict.CANONICAL_MATCH -> Icons.Default.CheckCircle
                else -> Icons.Default.Cancel
            },
            color = when (verdict.integrityVerdict) {
                IntegrityVerdict.EXACT_MATCH, IntegrityVerdict.CANONICAL_MATCH -> Success
                else -> Error
            }
        )
        VerdictAxisRow(
            label = "Provenance",
            value = verdict.provenanceVerdict?.name?.replace("_", " ") ?: "—",
            icon = when (verdict.provenanceVerdict) {
                ProvenanceVerdict.SIGNED_TRUSTED_DEVICE -> Icons.Default.Verified
                ProvenanceVerdict.SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE -> Icons.Default.Info
                else -> Icons.Default.Warning
            },
            color = when (verdict.provenanceVerdict) {
                ProvenanceVerdict.SIGNED_TRUSTED_DEVICE -> Success
                ProvenanceVerdict.SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE -> Warning
                else -> Error
            }
        )
        VerdictAxisRow(
            label = "Similarity",
            value = verdict.similarityVerdict?.name?.replace("_", " ") ?: "—",
            icon = when (verdict.similarityVerdict) {
                SimilarityVerdict.STRONG_DERIVATIVE -> Icons.Default.CheckCircle
                SimilarityVerdict.PROBABLE_DERIVATIVE -> Icons.Default.Info
                else -> Icons.Default.Warning
            },
            color = when (verdict.similarityVerdict) {
                SimilarityVerdict.STRONG_DERIVATIVE -> Success
                SimilarityVerdict.PROBABLE_DERIVATIVE -> Warning
                else -> Error
            }
        )
        VerdictAxisRow(
            label = "Evidence Quality",
            value = verdict.evidenceQualityVerdict?.name?.replace("_", " ") ?: "—",
            icon = when (verdict.evidenceQualityVerdict) {
                EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH -> Icons.Default.Verified
                EvidenceQualityVerdict.MODERATE_EVIDENTIARY_STRENGTH -> Icons.Default.Info
                else -> Icons.Default.Warning
            },
            color = when (verdict.evidenceQualityVerdict) {
                EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH -> Success
                EvidenceQualityVerdict.MODERATE_EVIDENTIARY_STRENGTH -> Warning
                else -> Error
            }
        )
    }
}

/**
 * Single verdict axis row.
 */
@Composable
private fun VerdictAxisRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = color,
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Transformation indicators as chips.
 */
@Composable
private fun TransformationIndicators(indicators: List<TransformationIndicator>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Transformations Detected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(indicators) { indicator ->
                AssistChip(
                    onClick = { /* No-op, display only */ },
                    label = {
                        Text(
                            text = indicator.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Transform,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/**
 * Warnings section.
 */
@Composable
private fun WarningsSection(warnings: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Warning,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Warnings",
                style = MaterialTheme.typography.labelMedium,
                color = Warning
            )
        }
        warnings.forEach { warning ->
            Text(
                text = "• $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Matched evidence section.
 */
@Composable
private fun MatchedEvidenceSection(
    matchedEvidence: List<MatchedEvidence>,
    onEvidenceClick: ((String) -> Unit)?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Matched Evidence (${matchedEvidence.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        matchedEvidence.take(3).forEach { match ->
            val evidenceId = match.evidenceId.orEmpty()
            val matchType = match.matchType.orEmpty()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onEvidenceClick != null && evidenceId.isNotEmpty()) {
                        onEvidenceClick?.invoke(evidenceId)
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (matchType.isNotEmpty())
                                matchType.replaceFirstChar { it.uppercase() }
                            else "Unknown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (evidenceId.length > 20) evidenceId.take(20) + "..." else evidenceId.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (match.similarityScore != null) {
                        Text(
                            text = "${(match.similarityScore * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                match.similarityScore >= 0.8 -> Success
                                match.similarityScore >= 0.5 -> Warning
                                else -> Error
                            }
                        )
                    }
                }
            }
        }
        if (matchedEvidence.size > 3) {
            Text(
                text = "+ ${matchedEvidence.size - 3} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Expanded details (for EXPANDED mode).
 */
@Composable
private fun ExpandedVerdictDetails(verdict: MultiAxisVerdict) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Algorithm versions
        if (verdict.algorithmVersionsUsed.isNotEmpty()) {
            Text(
                text = "Algorithm Versions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            verdict.algorithmVersionsUsed.forEach { (key, value) ->
                Text(
                    text = "$key: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // ── Step 7.2: Policy version ──
        verdict.policyVersion?.let { policyVersion ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Policy Version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = policyVersion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // Metadata
        verdict.metadata?.let { metadata ->
            if (metadata.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Metadata",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                metadata.forEach { (key, value) ->
                    Text(
                        text = "$key: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Determine the overall verdict status.
 */
fun getVerdictStatus(verdict: MultiAxisVerdict): VerdictStatus {
    val integrityOk = verdict.integrityVerdict == IntegrityVerdict.EXACT_MATCH ||
            verdict.integrityVerdict == IntegrityVerdict.CANONICAL_MATCH
    val provenanceOk = verdict.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE
    val similarityOk = verdict.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ||
            verdict.similarityVerdict == SimilarityVerdict.PROBABLE_DERIVATIVE
    val qualityOk = verdict.evidenceQualityVerdict == EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH

    return when {
        integrityOk && provenanceOk && qualityOk -> VerdictStatus.VERIFIED
        integrityOk || (similarityOk && provenanceOk) -> VerdictStatus.PARTIAL
        else -> VerdictStatus.UNVERIFIED
    }
}

/**
 * Preview function for VerdictCard (for development).
 */
@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun VerdictCardPreview() {
    DMVPTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            VerdictCard(
                verdict = MultiAxisVerdict(
                    integrityVerdict = IntegrityVerdict.EXACT_MATCH,
                    provenanceVerdict = ProvenanceVerdict.SIGNED_TRUSTED_DEVICE,
                    similarityVerdict = SimilarityVerdict.STRONG_DERIVATIVE,
                    evidenceQualityVerdict = EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH,
                    transformationIndicators = listOf(
                        TransformationIndicator.COMPRESSION_DETECTED,
                        TransformationIndicator.CROP_RESIZE
                    ),
                    matchedEvidenceList = listOf(
                        MatchedEvidence(
                            evidenceId = "12345678-1234-1234-1234-123456789012",
                            matchType = "exact",
                            similarityScore = 1.0
                        )
                    ),
                    warnings = listOf("Fast verification mode used - reduced accuracy for similarity."),
                    algorithmVersionsUsed = mapOf("fingerprint" to "v1.0", "similarity" to "v1.0"),
                    // ── Step 7.2: Policy version in preview ──
                    policyVersion = "policy-v3.0.0",
                    summaryUiScore = 85
                ),
                mode = VerdictCardMode.STANDARD,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
