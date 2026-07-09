/**
 * app/src/main/java/com/dmvp/app/ui/components/LoadingOverlay.kt
 *
 * LoadingOverlay composable for DMVP v3.0 Android app.
 * Provides a consistent loading overlay with progress indicator,
 * optional message, and cancel button.
 *
 * Features:
 *   - Full-screen or scrim overlay with dimmed background
 *   - Circular progress indicator (Material3)
 *   - Optional progress text (percentage or message)
 *   - Optional cancel button (with callback)
 *   - Optional success/error state transition
 *   - Dark theme optimized
 *
 * Used in:
 *   - All screens for network operations (registration, verification, search)
 *   - Device operations (rotation, revocation, recovery)
 *   - Long-running media processing
 */

package com.dmvp.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.theme.*

/**
 * Loading state for the overlay.
 */
enum class LoadingState {
    LOADING,
    SUCCESS,
    ERROR
}

/**
 * LoadingOverlay composable.
 *
 * @param isLoading Whether the overlay is visible.
 * @param modifier Modifier for the overlay container.
 * @param message Optional message to display below the progress indicator.
 * @param progress Optional progress value (0.0 to 1.0) for determinate progress.
 * @param state Loading state (loading, success, error) - default: Loading.
 * @param showCancelButton Whether to show the cancel button (default: false).
 * @param onCancel Optional callback when cancel is clicked.
 * @param onDismiss Optional callback when overlay is dismissed (for success/error states).
 * @param content The content behind the overlay (dimmed).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: Float? = null,
    state: LoadingState = LoadingState.LOADING,
    showCancelButton: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background content
        content()

        // Overlay (animated visibility)
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(200)
            )
        ) {
            // Dimmed background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) { /* consume clicks */ }
            ) {
                // Loading content centered
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(32.dp)
                        .widthIn(min = 200.dp, max = 300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress indicator
                        when (state) {
                            LoadingState.LOADING -> {
                                if (progress != null) {
                                    // Determinate progress
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(56.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeCap = StrokeCap.Round
                                    )
                                    if (message == null) {
                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    // Indeterminate progress
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(56.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                            LoadingState.SUCCESS -> {
                                AnimatedContent(
                                    targetState = true,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) +
                                                scaleIn(initialScale = 0.5f, animationSpec = spring())
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Success,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                            LoadingState.ERROR -> {
                                AnimatedContent(
                                    targetState = true,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) +
                                                scaleIn(initialScale = 0.5f, animationSpec = spring())
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = Error,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        // Message
                        if (message != null) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Cancel button (only in LOADING state)
                        if (showCancelButton && state == LoadingState.LOADING && onCancel != null) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // Dismiss button (for success/error states)
                        if ((state == LoadingState.SUCCESS || state == LoadingState.ERROR) && onDismiss != null) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state == LoadingState.SUCCESS) Success else Error
                                )
                            ) {
                                Text(
                                    text = if (state == LoadingState.SUCCESS) "Done" else "Dismiss",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simplified loading overlay with just a spinner and optional message.
 * Useful for quick loading states without complex state management.
 */
@Composable
fun SimpleLoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: @Composable () -> Unit
) {
    LoadingOverlay(
        isLoading = isLoading,
        modifier = modifier,
        message = message,
        state = LoadingState.LOADING,
        content = content
    )
}

/**
 * Loading overlay with progress percentage.
 */
@Composable
fun ProgressLoadingOverlay(
    isLoading: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: @Composable () -> Unit
) {
    LoadingOverlay(
        isLoading = isLoading,
        modifier = modifier,
        progress = progress,
        message = message ?: "Processing... ${(progress * 100).toInt()}%",
        state = LoadingState.LOADING,
        content = content
    )
}

/**
 * Success/Error overlay with auto-dismiss after delay.
 */
@Composable
fun ResultLoadingOverlay(
    isLoading: Boolean,
    state: LoadingState,
    modifier: Modifier = Modifier,
    message: String? = null,
    onDismiss: () -> Unit,
    autoDismissDelay: Long = 2000L,
    content: @Composable () -> Unit
) {
    // Auto-dismiss after delay for success/error
    LaunchedEffect(state) {
        if (state != LoadingState.LOADING && isLoading) {
            delay(autoDismissDelay)
            onDismiss()
        }
    }

    LoadingOverlay(
        isLoading = isLoading,
        modifier = modifier,
        message = message,
        state = state,
        onDismiss = onDismiss,
        content = content
    )
}

// ================================
// Previews
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun LoadingOverlayPreview() {
    DMVPTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            var isLoading by remember { mutableStateOf(true) }
            var state by remember { mutableStateOf(LoadingState.LOADING) }
            var progress by remember { mutableStateOf(0.5f) }

            LoadingOverlay(
                isLoading = isLoading,
                message = "Processing media...",
                progress = progress,
                state = state,
                showCancelButton = true,
                onCancel = { isLoading = false },
                onDismiss = { isLoading = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Background Content",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = {
                            isLoading = true
                            state = LoadingState.LOADING
                            progress = 0.0f
                            // Simulate progress
                            var p = 0.0f
                            // In a real app, use a coroutine
                        }) {
                            Text("Show Loading")
                        }
                        Button(onClick = {
                            isLoading = true
                            state = LoadingState.SUCCESS
                        }) {
                            Text("Show Success")
                        }
                        Button(onClick = {
                            isLoading = true
                            state = LoadingState.ERROR
                        }) {
                            Text("Show Error")
                        }
                    }
                    // Simulate progress update
                    if (isLoading && state == LoadingState.LOADING) {
                        LaunchedEffect(Unit) {
                            while (isLoading && progress < 1.0f) {
                                delay(200)
                                progress = (progress + 0.05f).coerceAtMost(1.0f)
                            }
                            if (progress >= 1.0f) {
                                // Auto-transition to success
                                state = LoadingState.SUCCESS
                                delay(1500)
                                isLoading = false
                            }
                        }
                    }
                }
            }
        }
    }
}
