package com.spkup.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spkup.app.ui.theme.SpkUpTheme

/**
 * Custom SpkUP Slider with exact geometry and color bindings:
 * - Thick rounded active track (#121A24 Slate in Light, #FF9F0A Amber in Dark)
 * - Vertical rounded bar thumb divider (4dp wide, 28dp tall)
 * - Soft Light Lavender (#E6DEFA) inactive track (gap fill) preserved in both Light and Dark modes
 * - Trailing dot at the far right end
 * - Top-right value label in primaryAccent
 */
@Composable
fun SpkUpSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueLabel: String,
    modifier: Modifier = Modifier,
    activeColor: Color = SpkUpTheme.colors.sliderActiveTrack,
    inactiveColor: Color = SpkUpTheme.colors.sliderInactiveTrack,
    thumbColor: Color = SpkUpTheme.colors.sliderActiveTrack,
    trackHeight: Dp = 8.dp,
    thumbWidth: Dp = 4.dp,
    thumbHeight: Dp = 28.dp,
    thumbGap: Dp = 4.dp,
    trailingDotRadius: Dp = 4.dp
) {
    val density = LocalDensity.current

    val normalizedFraction = remember(value, valueRange) {
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Top Row: Title on Left, Value Label on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = SpkUpTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = SpkUpTheme.colors.textPrimary
            )
            Text(
                text = valueLabel,
                style = SpkUpTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = SpkUpTheme.colors.primaryAccent
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Canvas Track & Thumb
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
        ) {
            val widthPx = size.width
            val heightPx = size.height
            val centerY = heightPx / 2f

            val trackHeightPx = trackHeight.toPx()
            val thumbWidthPx = thumbWidth.toPx()
            val thumbHeightPx = thumbHeight.toPx()
            val thumbGapPx = thumbGap.toPx()
            val trailingDotRadiusPx = trailingDotRadius.toPx()

            val trackCornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            val thumbCornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)

            // Usable track width: leave space for trailing dot at far right
            val trailingDotMargin = trailingDotRadiusPx * 2f + 6.dp.toPx()
            val usableTrackWidth = widthPx - trailingDotMargin

            // Calculate thumb center X position
            val thumbCenterX = (usableTrackWidth * normalizedFraction).coerceIn(
                thumbWidthPx / 2f,
                usableTrackWidth - thumbWidthPx / 2f
            )

            // ── 1. Active Left Track ──────────────────────────────────────────
            val activeTrackRight = (thumbCenterX - thumbWidthPx / 2f - thumbGapPx).coerceAtLeast(0f)
            if (activeTrackRight > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(activeTrackRight, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
            }

            // ── 2. Vertical Thumb Divider (4dp x 28dp) ────────────────────────
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbCenterX - thumbWidthPx / 2f, centerY - thumbHeightPx / 2f),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = thumbCornerRadius
            )

            // ── 3. Inactive Right Track (#E6DEFA Soft Light Lavender Gap Fill) ──
            val inactiveTrackLeft = (thumbCenterX + thumbWidthPx / 2f + thumbGapPx).coerceAtMost(usableTrackWidth)
            val inactiveTrackWidth = (usableTrackWidth - inactiveTrackLeft).coerceAtLeast(0f)
            if (inactiveTrackWidth > 0f) {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(inactiveTrackLeft, centerY - trackHeightPx / 2f),
                    size = Size(inactiveTrackWidth, trackHeightPx),
                    cornerRadius = trackCornerRadius
                )
            }

            // ── 4. Trailing Dot at Far Right End ─────────────────────────────
            val dotCenterX = widthPx - trailingDotRadiusPx
            drawCircle(
                color = inactiveColor,
                radius = trailingDotRadiusPx,
                center = Offset(dotCenterX, centerY)
            )
        }
    }
}
