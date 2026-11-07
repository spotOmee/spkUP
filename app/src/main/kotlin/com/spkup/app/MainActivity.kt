package com.spkup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.spkup.app.data.PermissionManager
import com.spkup.app.data.SpkUpPreferences
import com.spkup.app.data.ThemeMode
import com.spkup.app.ui.dashboard.DashboardScreen
import com.spkup.app.ui.onboarding.WelcomeScreen
import com.spkup.app.ui.theme.SpkUpTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    WELCOME,
    DASHBOARD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val view = LocalView.current
            view.isSoundEffectsEnabled = false

            val prefs = remember { SpkUpPreferences.getInstance(context) }
            val state by prefs.state.collectAsState()
            val scope = rememberCoroutineScope()

            val isDarkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Determine initial screen based on permissions & onboarding status
            val initialScreen = remember {
                if (PermissionManager.areAllPermissionsGranted(context) && state.isOnboardingCompleted) {
                    AppScreen.DASHBOARD
                } else {
                    AppScreen.WELCOME
                }
            }

            var currentScreen by remember { mutableStateOf(initialScreen) }

            SpkUpTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SpkUpTheme.colors.background
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == AppScreen.DASHBOARD) {
                                // Welcome -> Dashboard (Forward slide to left)
                                (slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(380)
                                ) + fadeIn(animationSpec = tween(380))).togetherWith(
                                    slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                        animationSpec = tween(380)
                                    ) + fadeOut(animationSpec = tween(380))
                                )
                            } else {
                                // Dashboard -> Welcome (Backward slide to right from top 30% swipe)
                                (slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(380)
                                ) + fadeIn(animationSpec = tween(380))).togetherWith(
                                    slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = tween(380)
                                    ) + fadeOut(animationSpec = tween(380))
                                )
                            }
                        },
                        label = "screen_transition"
                    ) { target ->
                        when (target) {
                            AppScreen.WELCOME -> {
                                WelcomeScreen(
                                    onContinueToDashboard = {
                                        scope.launch {
                                            prefs.setOnboardingCompleted(true)
                                            currentScreen = AppScreen.DASHBOARD
                                        }
                                    }
                                )
                            }
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    prefs = prefs,
                                    onNavigateBackToWelcome = {
                                        currentScreen = AppScreen.WELCOME
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
