package com.spkup.app.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.spkup.app.ui.components.SpkUpWaveform
import com.spkup.app.ui.components.WaveformState
import com.spkup.app.ui.theme.DarkPrimaryAccent
import com.spkup.app.ui.theme.DarkSurfaceCard
import com.spkup.app.ui.theme.LightPrimaryAccent
import com.spkup.app.ui.theme.LightSurfaceCard
import com.spkup.app.ui.theme.SpkUpTheme

/**
 * BubbleOverlayContent: Root Compose visual tree for the Floating Bubble Overlay.
 *
 * Implements:
 * 1. Live binding to Opacity, Size (40-80dp), Shape (0-50%), and Theme color.
 * 2. Morphing animation between Compact Bubble (52dp) and Expanded Pill Modal (208dp x 48dp).
 * 3. 100% silent interaction with zero system beep, click, or chime feedback.
 */
@Composable
fun BubbleOverlayContent(
    uiState: BubbleUiState,
    opacity: Float,
    sizeDp: Int,
    shapePercent: Int,
    isDark: Boolean,
    audioAmplitude: Float,
    onConfirmTap: () -> Unit,
    onCancelTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    view.isSoundEffectsEnabled = false
    view.isHapticFeedbackEnabled = false

    val isExpanded = uiState == BubbleUiState.TAP_MODAL

    val targetWidth by animateDpAsState(
        targetValue = if (isExpanded) 208.dp else sizeDp.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "overlay_width"
    )

    val targetHeight by animateDpAsState(
        targetValue = if (isExpanded) 48.dp else sizeDp.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "overlay_height"
    )

    // Corner radius calculation:
    // When compact: shapePercent 0% -> sizeDp / 2 (Circle); shapePercent 50% -> rounded squircle
    // When expanded pill: fixed 24dp capsule
    val compactCornerRadius = (sizeDp * (0.50f - (shapePercent / 100f) * 0.30f)).coerceAtLeast(10f)
    val targetCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else compactCornerRadius.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "overlay_corner_radius"
    )

    val containerAlpha by animateFloatAsState(
        targetValue = opacity.coerceIn(0.20f, 1.0f),
        animationSpec = tween(durationMillis = 200),
        label = "overlay_alpha"
    )

    val cardBg = if (isDark) DarkSurfaceCard else LightSurfaceCard
    val accentColor = if (isDark) DarkPrimaryAccent else LightPrimaryAccent

    Box(
        modifier = modifier
            .size(width = targetWidth, height = targetHeight)
            .alpha(containerAlpha)
            .clip(RoundedCornerShape(targetCornerRadius))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = if (uiState == BubbleUiState.HOLD_RECORDING) 0.6f else 0.25f),
                shape = RoundedCornerShape(targetCornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!isExpanded) {
            // ── COMPACT BUBBLE VIEW ──────────────────────────────────────────
            val waveformState = when (uiState) {
                BubbleUiState.COMPACT_IDLE -> WaveformState.IDLE_BREATHING
                BubbleUiState.HOLD_RECORDING -> WaveformState.ACTIVE_SPEAKING
                BubbleUiState.PROCESSING -> WaveformState.PROCESSING
                else -> WaveformState.IDLE_BREATHING
            }

            SpkUpWaveform(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                state = waveformState,
                audioAmplitude = audioAmplitude,
                color = accentColor,
                barThickness = 4.5.dp,
                barSpacing = 3.8.dp
            )
        } else {
            // ── EXPANDED PILL MODAL: [ ✕ | Live Waveform | ✓ ] ──────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Cancel Button (✕)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onCancelTap()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF453A), // iOS/Apple Soft Red
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 2. Center Live Waveform
                SpkUpWaveform(
                    modifier = Modifier
                        .size(width = 110.dp, height = 34.dp)
                        .padding(horizontal = 6.dp),
                    state = WaveformState.ACTIVE_SPEAKING,
                    audioAmplitude = audioAmplitude,
                    color = accentColor,
                    barThickness = 4.2.dp,
                    barSpacing = 3.6.dp
                )

                // 3. Confirm Button (✓)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.20f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onConfirmTap()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
