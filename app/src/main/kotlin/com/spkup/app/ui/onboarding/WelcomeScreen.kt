package com.spkup.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spkup.app.data.PermissionManager
import com.spkup.app.ui.theme.SpkUpTheme

@Composable
fun WelcomeScreen(
    onContinueToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    view.isSoundEffectsEnabled = false

    val lifecycleOwner = LocalLifecycleOwner.current

    var isMicGranted by remember { mutableStateOf(PermissionManager.isMicrophoneGranted(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionManager.isOverlayGranted(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(PermissionManager.isAccessibilityGranted(context)) }
    var isBatteryIgnored by remember { mutableStateOf(PermissionManager.isBatteryOptimizationIgnored(context)) }

    fun refreshPermissions() {
        isMicGranted = PermissionManager.isMicrophoneGranted(context)
        isOverlayGranted = PermissionManager.isOverlayGranted(context)
        isAccessibilityGranted = PermissionManager.isAccessibilityGranted(context)
        isBatteryIgnored = PermissionManager.isBatteryOptimizationIgnored(context)
    }

    // Live watcher on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicGranted = granted
    }

    val allGranted = isMicGranted && isOverlayGranted && isAccessibilityGranted && isBatteryIgnored

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpkUpTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome",
            style = SpkUpTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp
            ),
            color = SpkUpTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To enable floating voice typing and smart transcription, please grant the 4 essential permissions below.",
            style = SpkUpTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp,
                fontSize = 15.sp
            ),
            color = SpkUpTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── 1. Microphone Access ─────────────────────────────────────────────
        PermissionCard(
            title = "1. Microphone Access",
            description = "Captures raw 16kHz audio with zero OS beep or sound ducking.",
            icon = Icons.Default.Mic,
            isGranted = isMicGranted,
            onClick = {
                if (!isMicGranted) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── 2. Display Over Other Apps ───────────────────────────────────────
        PermissionCard(
            title = "2. Display Over Other Apps",
            description = "Enables the floating bubble overlay across any active app.",
            icon = Icons.Default.Layers,
            isGranted = isOverlayGranted,
            onClick = {
                if (!isOverlayGranted) {
                    PermissionManager.openOverlaySettings(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── 3. Accessibility Service ─────────────────────────────────────────
        PermissionCard(
            title = "3. Accessibility Service",
            description = "Detects keyboard visibility and injects transcribed text directly.",
            icon = Icons.Default.AccessibilityNew,
            isGranted = isAccessibilityGranted,
            onClick = {
                if (!isAccessibilityGranted) {
                    PermissionManager.openAccessibilitySettings(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── 4. Battery Optimization ──────────────────────────────────────────
        PermissionCard(
            title = "4. Battery Optimization",
            description = "Prevents system background kills during active voice dictation.",
            icon = Icons.Default.BatteryChargingFull,
            isGranted = isBatteryIgnored,
            onClick = {
                if (!isBatteryIgnored) {
                    PermissionManager.openBatteryOptimizationSettings(context)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Strict Gating Rule: Continue button appears ONLY when all 4 granted ──
        AnimatedVisibility(
            visible = allGranted,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Button(
                onClick = onContinueToDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpkUpTheme.colors.primaryAccent,
                    contentColor = if (SpkUpTheme.colors.isDark) Color.Black else Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Continue to Dashboard",
                        style = SpkUpTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Continue",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val cardBg = SpkUpTheme.colors.surfaceCard
    val accent = SpkUpTheme.colors.primaryAccent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left circular icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) accent.copy(alpha = 0.16f)
                        else SpkUpTheme.colors.textSecondary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) accent else SpkUpTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = SpkUpTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = SpkUpTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = SpkUpTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    color = SpkUpTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Pill Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) accent.copy(alpha = 0.18f)
                        else accent
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGranted) "✓ Granted" else "Enable",
                    style = SpkUpTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = if (isGranted) accent
                    else if (SpkUpTheme.colors.isDark) Color.Black else Color.White
                )
            }
        }
    }
}
