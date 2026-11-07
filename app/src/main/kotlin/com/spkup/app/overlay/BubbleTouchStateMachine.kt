package com.spkup.app.overlay

import android.os.SystemClock

enum class BubbleUiState {
    /** Compact bubble resting in idle breathing animation. */
    COMPACT_IDLE,

    /** Active push-to-talk hold recording (finger pressed >= 250ms). */
    HOLD_RECORDING,

    /** Expanded pill modal [ ✕ | Waveform | ✓ ] active after single tap (< 250ms). */
    TAP_MODAL,

    /** Inference in flight: circular arc spinner rotating. */
    PROCESSING
}

enum class TouchGestureMode {
    IDLE,
    POSSIBLE_TAP_OR_HOLD,
    HOLDING,
    DRAGGING
}

sealed class GestureAction {
    object None : GestureAction()
    object StartPreRoll : GestureAction()
    object StartHoldRecording : GestureAction()
    object FinishHoldRecording : GestureAction()
    object OpenTapModal : GestureAction()
    object CancelGesture : GestureAction()
    data class DragMove(val deltaX: Float, val deltaY: Float) : GestureAction()
    data class DragEnd(val finalX: Int, val finalY: Int) : GestureAction()
}

/**
 * Deterministic Touch State Machine for Floating Bubble Overlay.
 *
 * Rules:
 * 1. ACTION_DOWN: Records start coords, starts hold evaluation timer (250ms), pre-warms audio recorder.
 * 2. Movement > 20dp: Transitions immediately to DRAGGING mode, cancelling any tap or hold triggers.
 * 3. Held >= 250ms (movement < 20dp): Transitions to HOLDING mode (Push-to-Talk).
 *    On ACTION_UP, commits audio capture with 450ms tail buffer.
 * 4. Released < 250ms (movement < 20dp): Treats as single click, transitions to TAP_MODAL mode.
 */
class BubbleTouchStateMachine(
    val holdThresholdMs: Long = HOLD_THRESHOLD_MS,
    val dragDistanceThresholdPx: Float = DRAG_THRESHOLD_PX
) {

    companion object {
        const val HOLD_THRESHOLD_MS = 250L
        const val DRAG_THRESHOLD_PX = 52f // ~20dp on typical xxhdpi screens
    }

    var gestureMode: TouchGestureMode = TouchGestureMode.IDLE
        private set

    var downRawX: Float = 0f
        private set

    var downRawY: Float = 0f
        private set

    var downTimeMs: Long = 0L
        private set

    var lastRawX: Float = 0f
        private set

    var lastRawY: Float = 0f
        private set

    /**
     * Handles ACTION_DOWN.
     */
    fun onTouchDown(rawX: Float, rawY: Float, nowMs: Long = SystemClock.uptimeMillis()): GestureAction {
        downRawX = rawX
        downRawY = rawY
        lastRawX = rawX
        lastRawY = rawY
        downTimeMs = nowMs
        gestureMode = TouchGestureMode.POSSIBLE_TAP_OR_HOLD
        return GestureAction.StartPreRoll
    }

    /**
     * Handles ACTION_MOVE.
     */
    fun onTouchMove(rawX: Float, rawY: Float): GestureAction {
        if (gestureMode == TouchGestureMode.IDLE) return GestureAction.None

        val deltaX = rawX - downRawX
        val deltaY = rawY - downRawY
        val distance = kotlin.math.hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()

        if (gestureMode == TouchGestureMode.POSSIBLE_TAP_OR_HOLD || gestureMode == TouchGestureMode.HOLDING) {
            if (distance > dragDistanceThresholdPx) {
                // Cancel tap or hold, switch to drag
                gestureMode = TouchGestureMode.DRAGGING
                val moveDx = rawX - lastRawX
                val moveDy = rawY - lastRawY
                lastRawX = rawX
                lastRawY = rawY
                return GestureAction.DragMove(moveDx, moveDy)
            }
        } else if (gestureMode == TouchGestureMode.DRAGGING) {
            val moveDx = rawX - lastRawX
            val moveDy = rawY - lastRawY
            lastRawX = rawX
            lastRawY = rawY
            return GestureAction.DragMove(moveDx, moveDy)
        }

        return GestureAction.None
    }

    /**
     * Called when hold timer (>= 250ms) fires while finger is still held.
     */
    fun onHoldTimerTriggered(nowMs: Long = SystemClock.uptimeMillis()): GestureAction {
        if (gestureMode == TouchGestureMode.POSSIBLE_TAP_OR_HOLD) {
            val duration = nowMs - downTimeMs
            if (duration >= holdThresholdMs) {
                gestureMode = TouchGestureMode.HOLDING
                return GestureAction.StartHoldRecording
            }
        }
        return GestureAction.None
    }

    /**
     * Handles ACTION_UP.
     */
    fun onTouchUp(rawX: Float, rawY: Float, nowMs: Long = SystemClock.uptimeMillis()): GestureAction {
        val action: GestureAction = when (gestureMode) {
            TouchGestureMode.POSSIBLE_TAP_OR_HOLD -> {
                val duration = nowMs - downTimeMs
                if (duration < holdThresholdMs) {
                    // Tap-to-Speak Mode: expand to pill modal
                    GestureAction.OpenTapModal
                } else {
                    // Hold release: commit recording with 450ms tail buffer
                    GestureAction.FinishHoldRecording
                }
            }

            TouchGestureMode.HOLDING -> {
                // Hold release: commit recording with 450ms tail buffer
                GestureAction.FinishHoldRecording
            }

            TouchGestureMode.DRAGGING -> {
                GestureAction.DragEnd(rawX.toInt(), rawY.toInt())
            }

            TouchGestureMode.IDLE -> {
                GestureAction.None
            }
        }

        gestureMode = TouchGestureMode.IDLE
        return action
    }

    /**
     * Handles ACTION_CANCEL.
     */
    fun onTouchCancel(): GestureAction {
        val wasHolding = gestureMode == TouchGestureMode.HOLDING
        gestureMode = TouchGestureMode.IDLE
        return if (wasHolding) GestureAction.CancelGesture else GestureAction.None
    }
}
