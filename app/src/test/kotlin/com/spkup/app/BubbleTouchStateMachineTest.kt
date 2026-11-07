package com.spkup.app

import com.spkup.app.overlay.BubbleTouchStateMachine
import com.spkup.app.overlay.BubbleUiState
import com.spkup.app.overlay.GestureAction
import com.spkup.app.overlay.TouchGestureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleTouchStateMachineTest {

    @Test
    fun testActionDownStartsPreRoll() {
        val sm = BubbleTouchStateMachine()
        val action = sm.onTouchDown(100f, 200f, nowMs = 1000L)

        assertEquals(GestureAction.StartPreRoll, action)
        assertEquals(TouchGestureMode.POSSIBLE_TAP_OR_HOLD, sm.gestureMode)
        assertEquals(100f, sm.downRawX, 0.001f)
        assertEquals(200f, sm.downRawY, 0.001f)
    }

    @Test
    fun testTapUnder250msExpandsPillModal() {
        val sm = BubbleTouchStateMachine(holdThresholdMs = 250L, dragDistanceThresholdPx = 50f)
        sm.onTouchDown(100f, 200f, nowMs = 1000L)

        // Slight movement (within 10px < 50px threshold)
        sm.onTouchMove(105f, 202f)

        // Release at 1150L (duration = 150ms < 250ms)
        val action = sm.onTouchUp(105f, 202f, nowMs = 1150L)

        assertEquals(GestureAction.OpenTapModal, action)
        assertEquals(TouchGestureMode.IDLE, sm.gestureMode)
    }

    @Test
    fun testHoldOver250msEntersHoldRecording() {
        val sm = BubbleTouchStateMachine(holdThresholdMs = 250L, dragDistanceThresholdPx = 50f)
        sm.onTouchDown(100f, 200f, nowMs = 1000L)

        // Hold timer fires at 1260L (duration 260ms >= 250ms)
        val holdAction = sm.onHoldTimerTriggered(nowMs = 1260L)
        assertEquals(GestureAction.StartHoldRecording, holdAction)
        assertEquals(TouchGestureMode.HOLDING, sm.gestureMode)

        // User speaks for 2 seconds and releases
        val upAction = sm.onTouchUp(102f, 201f, nowMs = 3260L)
        assertEquals(GestureAction.FinishHoldRecording, upAction)
        assertEquals(TouchGestureMode.IDLE, sm.gestureMode)
    }

    @Test
    fun testDragOver20dpEngagesDraggingAndCancelsHold() {
        val sm = BubbleTouchStateMachine(holdThresholdMs = 250L, dragDistanceThresholdPx = 50f)
        sm.onTouchDown(100f, 200f, nowMs = 1000L)

        // Finger moves 80px horizontally (> 50px threshold)
        val moveAction = sm.onTouchMove(180f, 200f)

        assertTrue(moveAction is GestureAction.DragMove)
        assertEquals(TouchGestureMode.DRAGGING, sm.gestureMode)

        val upAction = sm.onTouchUp(180f, 200f, nowMs = 1500L)
        assertTrue(upAction is GestureAction.DragEnd)
        assertEquals(TouchGestureMode.IDLE, sm.gestureMode)
    }

    @Test
    fun testBubbleUiStateEnum() {
        val states = BubbleUiState.entries
        assertEquals(4, states.size)
        assertTrue(states.contains(BubbleUiState.COMPACT_IDLE))
        assertTrue(states.contains(BubbleUiState.HOLD_RECORDING))
        assertTrue(states.contains(BubbleUiState.TAP_MODAL))
        assertTrue(states.contains(BubbleUiState.PROCESSING))
    }
}
