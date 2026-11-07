package com.spkup.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spkup.app.ui.theme.DarkSpkUpColors
import com.spkup.app.ui.theme.LightSpkUpColors
import com.spkup.app.ui.theme.SpkUpTheme
import kotlin.math.sin

/**
 * The 3 dynamic animation states of the SpkUP signature waveform.
 */
enum class WaveformState {
    /** Continuous slow ambient sine-wave breathing (~1800ms) on dashboard, splash, or idle bubble. */
    IDLE_BREATHING,

    /** Real-time 60 FPS height scaling and frequency oscillation driven by live audio amplitude. */
    ACTIVE_SPEAKING,

    /** Smooth collapse of 7 bars into a rotating circular progress arc spinner during inference. */
    PROCESSING
}

// Symmetric Height Profile: Center is 1.0f, flanked by 0.65f, 0.40f, and outer small bars 0.18f
private val SYMMETRIC_HEIGHT_PROFILE = floatArrayOf(0.18f, 0.40f, 0.65f, 1.00f, 0.65f, 0.40f, 0.18f)

// Voice responsiveness multipliers during ACTIVE_SPEAKING
private val VOICE_MULTIPLIERS = floatArrayOf(0.40f, 0.70f, 1.05f, 1.40f, 1.05f, 0.70f, 0.40f)

// Staggered phase offsets per bar to create natural organic wave motion
private val PHASE_OFFSETS = floatArrayOf(0.0f, 0.55f, 1.10f, 1.65f, 2.20f, 2.75f, 3.30f)

/**
 * Signature 7-Bar Animated Waveform Component.
 *
 * Serves as:
 * 1. SpkUP App Icon & Launch Logo
 * 2. Dashboard Animated Center Header
 * 3. Audio Activity Visualizer
 * 4. Floating Bubble Overlay Icon & Processing Spinner
 *
 * @param modifier Layout modifier (recommended height 32.dp - 64.dp)
 * @param state Current [WaveformState]
 * @param audioAmplitude Normalized mic amplitude in [0.0f..1.0f]
 * @param color Tint color (defaults to [SpkUpTheme.colors.primaryAccent])
 * @param barThickness Custom bar thickness (~4.5dp to 5.5dp for tactile bold look)
 * @param barSpacing Horizontal space between bars
 */
@Composable
fun SpkUpWaveform(
    modifier: Modifier = Modifier,
    state: WaveformState = WaveformState.IDLE_BREATHING,
    audioAmplitude: Float = 0f,
    color: Color = SpkUpTheme.colors.primaryAccent,
    barThickness: Dp = 5.dp,
    barSpacing: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spkup_waveform_anim")

    // ── 1. IDLE_BREATHING Animation (~1800ms cycle) ──────────────────────────
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_breathing_phase"
    )

    // ── 2. ACTIVE_SPEAKING Oscillation (~600ms ripple) ────────────────────────
    val speakingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speaking_ripple_phase"
    )

    // Smooth amplitude transition to prevent jarring jumps
    val smoothAmplitude by animateFloatAsState(
        targetValue = audioAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "smooth_audio_amplitude"
    )

    // ── 3. PROCESSING Morphing & Spinner Rotation ────────────────────────────
    val isProcessing = state == WaveformState.PROCESSING
    val morphProgress by animateFloatAsState(
        targetValue = if (isProcessing) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "morph_to_spinner_progress"
    )

    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    Canvas(
        modifier = modifier
    ) {
        val barThicknessPx = barThickness.toPx()
        val barSpacingPx = barSpacing.toPx()
        val totalBars = 7
        val totalBarsWidth = totalBars * barThicknessPx + (totalBars - 1) * barSpacingPx
        val startX = (size.width - totalBarsWidth) / 2f
        val centerY = size.height / 2f
        val availableHeight = size.height * 0.85f

        // ── A. Draw 7-Bar Waveform (Visible during IDLE and ACTIVE, fades out in PROCESSING) ──
        if (morphProgress < 1f) {
            val barsAlpha = (1f - morphProgress).coerceIn(0f, 1f)
            val collapseHeightFactor = (1f - morphProgress * 0.7f)

            for (i in 0 until totalBars) {
                val profileHeight = SYMMETRIC_HEIGHT_PROFILE[i]

                val heightScale = when (state) {
                    WaveformState.IDLE_BREATHING, WaveformState.PROCESSING -> {
                        // Ambient breathing sine-wave calculation
                        val sineVal = sin(idlePhase + PHASE_OFFSETS[i]).toFloat()
                        val breathingWave = 0.55f + 0.45f * sineVal
                        (profileHeight * (0.60f + 0.40f * breathingWave)).coerceIn(0.18f, 1.0f)
                    }
                    WaveformState.ACTIVE_SPEAKING -> {
                        // Live microphone RMS dynamic scaling
                        val ripple = sin(speakingPhase + PHASE_OFFSETS[i] * 1.5f).toFloat()
                        val dynamicBoost = smoothAmplitude * VOICE_MULTIPLIERS[i] * (0.85f + 0.30f * ripple)
                        (profileHeight * 0.28f + dynamicBoost * 0.95f).coerceIn(0.18f, 1.35f)
                    }
                }

                val targetBarHeight = (availableHeight * heightScale * collapseHeightFactor).coerceAtLeast(barThicknessPx)
                val halfBarHeight = (targetBarHeight - barThicknessPx) / 2f
                val barCenterX = startX + i * (barThicknessPx + barSpacingPx) + barThicknessPx / 2f

                // StrokeCap.Round ensures smooth rounded pill capsules and circular dot caps
                drawLine(
                    color = color.copy(alpha = barsAlpha),
                    start = Offset(barCenterX, centerY - halfBarHeight),
                    end = Offset(barCenterX, centerY + halfBarHeight),
                    strokeWidth = barThicknessPx,
                    cap = StrokeCap.Round
                )
            }
        }

        // ── B. Draw Circular Arc Spinner (Fades in during PROCESSING) ────────────
        if (morphProgress > 0f) {
            val spinnerAlpha = morphProgress.coerceIn(0f, 1f)
            val spinnerDiameter = (size.minDimension * 0.70f) * morphProgress
            val spinnerRadius = spinnerDiameter / 2f

            rotate(degrees = spinnerRotation, pivot = Offset(size.width / 2f, centerY)) {
                drawArc(
                    color = color.copy(alpha = spinnerAlpha),
                    startAngle = 0f,
                    sweepAngle = 275f,
                    useCenter = false,
                    topLeft = Offset(size.width / 2f - spinnerRadius, centerY - spinnerRadius),
                    size = Size(spinnerDiameter, spinnerDiameter),
                    style = Stroke(width = barThicknessPx, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS (Light Mode & OLED Dark Mode across all 3 states)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Light Mode - All 3 States", showBackground = true, backgroundColor = 0xFFF7F7F5)
@Composable
fun PreviewSpkUpWaveformLight() {
    SpkUpTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(LightSpkUpColors.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("IDLE_BREATHING", style = SpkUpTheme.typography.labelLarge, color = LightSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.IDLE_BREATHING
            )

            Text("ACTIVE_SPEAKING (65% Volume)", style = SpkUpTheme.typography.labelLarge, color = LightSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.ACTIVE_SPEAKING,
                audioAmplitude = 0.65f
            )

            Text("PROCESSING (Spinner Morph)", style = SpkUpTheme.typography.labelLarge, color = LightSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.PROCESSING
            )
        }
    }
}

@Preview(name = "OLED Dark Mode - All 3 States", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewSpkUpWaveformDark() {
    SpkUpTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(DarkSpkUpColors.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("IDLE_BREATHING", style = SpkUpTheme.typography.labelLarge, color = DarkSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.IDLE_BREATHING
            )

            Text("ACTIVE_SPEAKING (80% Volume)", style = SpkUpTheme.typography.labelLarge, color = DarkSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.ACTIVE_SPEAKING,
                audioAmplitude = 0.80f
            )

            Text("PROCESSING (Spinner Morph)", style = SpkUpTheme.typography.labelLarge, color = DarkSpkUpColors.textPrimary)
            SpkUpWaveform(
                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                state = WaveformState.PROCESSING
            )
        }
    }
}
