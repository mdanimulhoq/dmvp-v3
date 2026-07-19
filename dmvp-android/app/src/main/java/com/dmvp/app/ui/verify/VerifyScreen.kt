/**
 * Phase 3 Step 3.8: Android Verification Flow UI
 * Technology: Kotlin + Jetpack Compose
 * 
 * Shows:
 * - Verdict status
 * - Confidence score
 * - Which layer matched
 * - Layer-by-layer breakdown
 */

package com.dmvp.app.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmvp.app.data.model.VerificationVerdict
import com.dmvp.app.viewmodel.VerifyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    viewModel: VerifyViewModel = viewModel()
) {
    val verdict by viewModel.verdict.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification Result") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (verdict != null) {
                VerdictCard(verdict!!)
                Spacer(modifier = Modifier.height(16.dp))
                LayerBreakdownCard(verdict!!)
            } else if (errorMessage != null) {
                ErrorCard(errorMessage!!)
            }
        }
    }
}

@Composable
fun VerdictCard(verdict: VerificationVerdict) {
    val (verdictColor, verdictIcon, verdictText) = when (verdict.verdict) {
        "exact_match" -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "Exact Match")
        "near_copy" -> Triple(Color(0xFFFF9800), Icons.Default.Warning, "Near Copy")
        "derivative" -> Triple(Color(0xFFFF9800), Icons.Default.Transform, "Derivative")
        "similar" -> Triple(Color(0xFF2196F3), Icons.Default.Comparison, "Similar")
        "possible_ai_derivative" -> Triple(Color(0xFFF44336), Icons.Default.SmartToy, "Possible AI Generated")
        else -> Triple(Color(0xFF9E9E9E), Icons.Default.Help, "No Match")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = verdictIcon,
                    contentDescription = null,
                    tint = verdictColor,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = verdictText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor
                    )
                    Text(
                        text = "Confidence: ${(verdict.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryItem(
                    label = "Layers Evaluated",
                    value = verdict.summary.totalLayersEvaluated.toString()
                )
                SummaryItem(
                    label = "Layers with Signal",
                    value = verdict.summary.layersWithSignal.toString()
                )
                SummaryItem(
                    label = "Highest Layer",
                    value = verdict.summary.highestConfidenceLayer ?: "None"
                )
            }

            // AI Derivative Warning
            if (verdict.verdict == "possible_ai_derivative") {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚠️ AI-Generated Content Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This content may have been generated or modified by AI. Human review is required for evidentiary purposes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun LayerBreakdownCard(verdict: VerificationVerdict) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Layer-by-Layer Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            verdict.layers.forEach { (layerName, layerResult) ->
                LayerItem(
                    layerName = layerName,
                    signalDetected = layerResult.signalDetected,
                    confidence = layerResult.confidence
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun LayerItem(layerName: String, signalDetected: Boolean, confidence: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (signalDetected) Color(0xFF4CAF50) else Color(0xFFE0E0E0))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Layer name
        Text(
            text = layerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // Confidence
        Text(
            text = if (signalDetected) "${(confidence * 100).toInt()}%" else "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (signalDetected) Color(0xFF4CAF50) else Color.Gray
        )
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Verification Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
