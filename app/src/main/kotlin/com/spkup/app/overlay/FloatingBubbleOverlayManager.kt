package com.spkup.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.spkup.app.audio.ZeroBeepAudioRecorder
import com.spkup.app.data.PermissionManager
import com.spkup.app.data.SpkUpPreferences
import com.spkup.app.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages the floating WindowManager overlay view, live slider bindings,
 * and Hold/Tap audio capture lifecycle.
 */
class FloatingBubbleOverlayManager(
    private val context: Context,
    private val prefs: SpkUpPreferences,
    private val audioRecorder: ZeroBeepAudioRecorder
) : LifecycleOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentUiState by mutableStateOf(BubbleUiState.COMPACT_IDLE)
    private var onAudioCapturedCallback: ((ShortArray) -> Unit)? = null

    val isShowing: Boolean get() = overlayView != null

    private val touchStateMachine = BubbleTouchStateMachine()
    private var holdRunnable: Runnable? = null

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Shows the floating overlay bubble if permissions are granted.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(onAudioCaptured: (ShortArray) -> Unit) {
        if (overlayView != null) return
        if (!PermissionManager.isOverlayGranted(context)) return

        onAudioCapturedCallback = onAudioCaptured

        val metrics = context.resources.displayMetrics
        val density = metrics.density

        val initialSizeDp = prefs.currentBubbleSizeDp
        val initialSizePx = (initialSizeDp * density).toInt()

        val savedX = prefs.bubbleLastX
        val savedY = prefs.bubbleLastY

        val defaultX = metrics.widthPixels - initialSizePx - (16 * density).toInt()
        val defaultY = (metrics.heightPixels * 0.45f).toInt()

        val startX = if (savedX >= 0) savedX else defaultX
        val startY = if (savedY >= 0) savedY else defaultY

        val params = WindowManager.LayoutParams(
            initialSizePx,
            initialSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        layoutParams = params

        val composeView = ComposeView(context).apply {
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            setViewTreeLifecycleOwner(this@FloatingBubbleOverlayManager)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleOverlayManager)

            setContent {
                val state by prefs.state.collectAsState()
                val liveAmplitude by audioRecorder.amplitudeFlow.collectAsState()

                val isDark = when (state.themeMode) {
                    ThemeMode.SYSTEM -> false
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

                BubbleOverlayContent(
                    uiState = currentUiState,
                    opacity = state.bubbleOpacity,
                    sizeDp = state.bubbleSizeDp,
                    shapePercent = state.bubbleShapePercent,
                    isDark = isDark,
                    audioAmplitude = liveAmplitude,
                    onConfirmTap = { handleConfirmTap() },
                    onCancelTap = { handleCancelTap() }
                )
            }

            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }

        overlayView = composeView
        try {
            windowManager.addView(composeView, params)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            overlayView = null
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        val view = overlayView ?: return false

        // Tap modal mode processes button taps directly; dragging is disabled in expanded modal
        if (currentUiState == BubbleUiState.TAP_MODAL) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val action = touchStateMachine.onTouchDown(event.rawX, event.rawY)
                if (action is GestureAction.StartPreRoll) {
                    audioRecorder.startPreRollListening()
                }

                // Schedule hold timer check for 250ms
                holdRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable {
                    val holdAction = touchStateMachine.onHoldTimerTriggered()
                    if (holdAction is GestureAction.StartHoldRecording) {
                        currentUiState = BubbleUiState.HOLD_RECORDING
                        audioRecorder.startActiveRecording()
                    }
                }
                holdRunnable = runnable
                handler.postDelayed(runnable, BubbleTouchStateMachine.HOLD_THRESHOLD_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val action = touchStateMachine.onTouchMove(event.rawX, event.rawY)
                if (action is GestureAction.DragMove) {
                    // Cancel hold timer if finger displaced > 20dp
                    holdRunnable?.let { handler.removeCallbacks(it) }

                    // If was holding and moved, cancel recording
                    if (currentUiState == BubbleUiState.HOLD_RECORDING) {
                        currentUiState = BubbleUiState.COMPACT_IDLE
                        audioRecorder.cancelImmediately()
                    }

                    params.x += action.deltaX.toInt()
                    params.y += action.deltaY.toInt()
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) {}
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                holdRunnable?.let { handler.removeCallbacks(it) }
                val action = touchStateMachine.onTouchUp(event.rawX, event.rawY)

                when (action) {
                    is GestureAction.OpenTapModal -> {
                        // Single tap detected (< 250ms): Expand to Pill Modal
                        expandToPillModal()
                    }

                    is GestureAction.FinishHoldRecording -> {
                        // Hold released: Finalize audio with 450ms tail buffer & run inference
                        finalizeSpeechCapture()
                    }

                    is GestureAction.DragEnd -> {
                        // Persist last bubble position
                        scope.launch(Dispatchers.IO) {
                            prefs.setBubblePosition(params.x, params.y)
                        }
                    }

                    else -> {}
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                holdRunnable?.let { handler.removeCallbacks(it) }
                val action = touchStateMachine.onTouchCancel()
                if (action is GestureAction.CancelGesture) {
                    currentUiState = BubbleUiState.COMPACT_IDLE
                    audioRecorder.cancelImmediately()
                }
                return true
            }
        }
        return false
    }

    private fun expandToPillModal() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val density = context.resources.displayMetrics.density

        currentUiState = BubbleUiState.TAP_MODAL
        audioRecorder.startActiveRecording()

        // Expand WindowManager dimensions to accommodate 208dp pill
        params.width = (216 * density).toInt()
        params.height = (56 * density).toInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun handleConfirmTap() {
        finalizeSpeechCapture()
    }

    private fun handleCancelTap() {
        audioRecorder.cancelImmediately()
        collapseToCompactBubble()
    }

    private fun collapseToCompactBubble() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val density = context.resources.displayMetrics.density

        currentUiState = BubbleUiState.COMPACT_IDLE

        val sizePx = (prefs.currentBubbleSizeDp * density).toInt()
        params.width = sizePx
        params.height = sizePx
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun finalizeSpeechCapture() {
        currentUiState = BubbleUiState.PROCESSING

        scope.launch {
            // Captures 450 ms trailing tail buffer so final word is never cut
            val pcmAudio = audioRecorder.stopAndFinalize()

            collapseToCompactBubble()

            if (pcmAudio.isNotEmpty()) {
                onAudioCapturedCallback?.invoke(pcmAudio)
            }
        }
    }

    /**
     * Hides the floating overlay window and releases lifecycle observers.
     */
    fun hideOverlay() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        audioRecorder.cancelImmediately()

        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        layoutParams = null
        currentUiState = BubbleUiState.COMPACT_IDLE

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
