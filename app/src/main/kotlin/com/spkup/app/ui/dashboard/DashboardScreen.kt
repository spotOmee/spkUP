package com.spkup.app.ui.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spkup.app.data.PermissionManager
import com.spkup.app.data.SpkUpPreferences
import com.spkup.app.data.ThemeMode
import com.spkup.app.overlay.FloatingBubbleService
import com.spkup.app.ui.components.SpkUpSlider
import com.spkup.app.ui.components.SpkUpWaveform
import com.spkup.app.ui.components.WaveformState
import com.spkup.app.ui.theme.SpkUpTheme
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    prefs: SpkUpPreferences,
    onNavigateBackToWelcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    view.isSoundEffectsEnabled = false

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state by prefs.state.collectAsState()

    val isDark = SpkUpTheme.colors.isDark

    // Theme rotation animation
    val themeIconRotation by animateFloatAsState(
        targetValue = if (isDark) 360f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "theme_rotation"
    )

    // Gesture tracking for Top 30% swipe
    var topSwipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpkUpTheme.colors.background)
    ) {
        // ── Top 30% Left-to-Right Swipe Touch Detector ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(fraction = 0.30f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { topSwipeOffset = 0f },
                        onDragEnd = {
                            if (topSwipeOffset > swipeThresholdPx) {
                                onNavigateBackToWelcome()
                            }
                            topSwipeOffset = 0f
                        },
                        onDragCancel = { topSwipeOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            topSwipeOffset += dragAmount
                        }
                    )
                }
        )

        // ── Main Dashboard Content ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Theme Toggle Only (no heavy headers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SpkUpTheme.colors.surfaceCard)
                        .clickable {
                            scope.launch {
                                val nextMode = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
                                prefs.setThemeMode(nextMode)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = SpkUpTheme.colors.primaryAccent,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(themeIconRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Center Header: Signature 7-Bar Waveform Breathing
            SpkUpWaveform(
                modifier = Modifier.size(width = 140.dp, height = 64.dp),
                state = WaveformState.IDLE_BREATHING,
                color = SpkUpTheme.colors.primaryAccent,
                barThickness = 5.2.dp,
                barSpacing = 5.2.dp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── Master Toggle: Floating Bubble ───────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SpkUpTheme.colors.surfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Floating Bubble",
                            style = SpkUpTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = SpkUpTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.isFloatingBubbleEnabled) "Active on keyboard focus" else "Overlay disabled",
                            style = SpkUpTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = SpkUpTheme.colors.textSecondary
                        )
                    }

                    // Custom Switch
                    val isChecked = state.isFloatingBubbleEnabled
                    val switchWidth = 54.dp
                    val switchHeight = 32.dp
                    val thumbSize = 24.dp
                    val thumbOffset by animateDpAsState(
                        targetValue = if (isChecked) 26.dp else 4.dp,
                        animationSpec = tween(durationMillis = 200),
                        label = "thumb_offset"
                    )

                    Box(
                        modifier = Modifier
                            .size(width = switchWidth, height = switchHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isChecked) SpkUpTheme.colors.primaryAccent
                                else SpkUpTheme.colors.toggleInactiveTrack
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val context = view.context
                                scope.launch {
                                    val willEnable = !isChecked
                                    prefs.setFloatingBubbleEnabled(willEnable)
                                    if (willEnable) {
                                        if (PermissionManager.isOverlayGranted(context)) {
                                            FloatingBubbleService.start(context)
                                        }
                                    } else {
                                        FloatingBubbleService.stop(context)
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = thumbOffset)
                                .size(thumbSize)
                                .clip(CircleShape)
                                .background(
                                    if (isChecked) {
                                        if (isDark) Color.Black else Color.White
                                    } else {
                                        SpkUpTheme.colors.toggleInactiveThumb
                                    }
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── 3 Live Customization Sliders ─────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SpkUpTheme.colors.surfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // 1. Bubble Opacity
                    SpkUpSlider(
                        title = "Bubble Opacity",
                        value = state.bubbleOpacity,
                        valueRange = 0.20f..1.0f,
                        valueLabel = "${(state.bubbleOpacity * 100).toInt()}%",
                        onValueChange = { newVal ->
                            scope.launch { prefs.setBubbleOpacity(newVal) }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Bubble Size
                    SpkUpSlider(
                        title = "Bubble Size",
                        value = state.bubbleSizeDp.toFloat(),
                        valueRange = 40f..80f,
                        valueLabel = "${state.bubbleSizeDp} dp",
                        onValueChange = { newVal ->
                            scope.launch { prefs.setBubbleSizeDp(newVal.toInt()) }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Bubble Shape
                    SpkUpSlider(
                        title = "Bubble Shape",
                        value = state.bubbleShapePercent.toFloat(),
                        valueRange = 0f..50f,
                        valueLabel = "${state.bubbleShapePercent}%",
                        onValueChange = { newVal ->
                            scope.launch { prefs.setBubbleShapePercent(newVal.toInt()) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
