package com.spkup.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.spkup.app.ai.ActionCommand
import com.spkup.app.ai.CactusWhistleAsrEngine
import com.spkup.app.ai.ProcessedSpeechResult
import com.spkup.app.ai.SmolCommandRouter
import com.spkup.app.data.SpkUpPreferences
import com.spkup.app.overlay.FloatingBubbleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Universal Keyboard-Bound Accessibility Service & 3-Tier Text Injection Engine.
 *
 * Implements:
 * 1. Keyboard-Bound Lifecycle: Displays the floating bubble overlay strictly when the
 *    software keyboard is active or an editable text field is focused; hides it when dismissed.
 * 2. 3-Tier Universal Text Injection:
 *    - Tier 1: Direct ACTION_SET_TEXT on AccessibilityNodeInfo
 *    - Tier 2: Accessibility ACTION_PASTE fallback
 *    - Tier 3: Clipboard PrimaryClip pre-staging & hardware paste fallback
 * 3. Voice Action Command Routing:
 *    - CLEAR: Empties target input field
 *    - INSERT: Contextually inserts words before or after target phrases
 *    - SEND: Injects text, waits 80ms settling delay, traverses node tree, and clicks Send button
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "VoiceAccessibility"
        const val CLIP_LABEL = "SpkUP_Payload"
        const val SETTLING_DELAY_MS = 80L

        @Volatile
        var instance: VoiceAccessibilityService? = null
            internal set

        val isServiceRunning: Boolean get() = instance != null

        private val SEND_KEYWORDS = listOf(
            "send", "Send", "SEND",
            "send message", "Send message",
            "send text", "Send text",
            "submit", "Submit",
            "post", "Post",
            "reply", "Reply",
            "bhejo", "Bhejo", "भेजें"
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: SpkUpPreferences
    private lateinit var asrEngine: CactusWhistleAsrEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = SpkUpPreferences.getInstance(this)
        asrEngine = CactusWhistleAsrEngine(this)
        Log.i(TAG, "VoiceAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                evaluateKeyboardVisibility()
            }
        }
    }

    /**
     * Monitors active windows and displays the floating bubble only when the software
     * keyboard (TYPE_INPUT_METHOD) is open or an editable text field is focused.
     */
    fun evaluateKeyboardVisibility() {
        if (!prefs.isFloatingBubbleEnabled) {
            return
        }

        var isKeyboardVisible = false
        var hasEditableFocus = false

        try {
            val windowList = windows
            for (window in windowList) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    isKeyboardVisible = true
                    break
                }
            }

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable) {
                    hasEditableFocus = true
                }
            }
        } catch (_: Exception) {}

        val shouldShow = isKeyboardVisible || hasEditableFocus

        if (shouldShow) {
            if (!FloatingBubbleService.isServiceRunning) {
                FloatingBubbleService.start(this)
            }
        }
    }

    /**
     * Executes audio transcription, command parsing, and text injection from the floating overlay.
     */
    fun processAndInjectAudio(pcmAudio: ShortArray) {
        scope.launch(Dispatchers.Default) {
            val rawTranscript = asrEngine.transcribe(pcmAudio)
            if (rawTranscript.isBlank()) return@launch

            val styleMode = prefs.currentStyleMode
            val result = SmolCommandRouter.process(rawTranscript, styleMode)

            scope.launch(Dispatchers.Main) {
                val root = rootInActiveWindow
                val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                executeParsedCommand(result, focused)
            }
        }
    }

    /**
     * Executes parsed commands and text injection.
     */
    fun executeParsedCommand(result: ProcessedSpeechResult, targetNode: AccessibilityNodeInfo?) {
        val node = targetNode ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        when (result.action) {
            ActionCommand.CLEAR -> {
                if (node != null && node.isEditable) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }

            ActionCommand.INSERT -> {
                if (node != null && node.isEditable) {
                    val existing = node.text?.toString() ?: ""
                    val word = result.insertWord ?: ""
                    val target = result.insertTarget ?: ""
                    val position = result.insertPosition ?: "after"

                    val updatedText = if (position == "before") {
                        if (existing.contains(target, ignoreCase = true)) {
                            existing.replace(target, "$word $target", ignoreCase = true)
                        } else {
                            "$existing $word"
                        }
                    } else {
                        if (existing.contains(target, ignoreCase = true)) {
                            existing.replace(target, "$target $word", ignoreCase = true)
                        } else {
                            "$existing $word"
                        }
                    }
                    injectText(node, updatedText)
                }
            }

            ActionCommand.SEND -> {
                // Step 1: Inject text first
                if (result.cleanedText.isNotBlank()) {
                    injectText(node, result.cleanedText)
                }

                // Step 2: Trigger send button after 80ms settling delay
                handler.postDelayed({
                    findAndClickSendButton(node)
                }, SETTLING_DELAY_MS)
            }

            ActionCommand.NONE -> {
                if (result.cleanedText.isNotBlank()) {
                    injectText(node, result.cleanedText)
                }
            }
        }
    }

    /**
     * Universal 3-Tier Universal Text Injection.
     */
    fun injectText(targetNode: AccessibilityNodeInfo?, finalSpeechText: String) {
        val rootNode = rootInActiveWindow
        val node = targetNode ?: rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return

        if (!node.isEditable) return

        // Tier 0: System Clipboard Pre-staging
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(CLIP_LABEL, finalSpeechText)
        clipboard.setPrimaryClip(clip)

        // Tier 1: Direct Set Text (Fastest, zero UI flicker)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalSpeechText)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // Tier 2: Accessibility Paste Fallback (for browser inputs / protected fields)
        if (!success) {
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            // Tier 3: Synthetic hardware paste fallback
            if (!pasted) {
                try {
                    val pasteArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, finalSpeechText.length)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, pasteArgs)
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Traverses accessibility hierarchy to find and click the app's Send button.
     */
    fun findAndClickSendButton(targetNode: AccessibilityNodeInfo?): Boolean {
        val root = rootInActiveWindow ?: targetNode ?: return false

        // 1. Traverse node tree searching for Send keywords
        val sendNode = findSendNodeRecursive(root)
        if (sendNode != null) {
            val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "Successfully clicked Send button: ${sendNode.viewIdResourceName}")
                return true
            }
        }

        return false
    }

    private fun findSendNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()
        val resId = node.viewIdResourceName

        if (desc != null && SEND_KEYWORDS.any { desc.contains(it, ignoreCase = true) }) {
            if (node.isClickable) return node
        }

        if (text != null && SEND_KEYWORDS.any { text.equals(it, ignoreCase = true) }) {
            if (node.isClickable) return node
        }

        if (resId != null && (resId.endsWith("send", ignoreCase = true) || resId.contains("btn_send", ignoreCase = true))) {
            if (node.isClickable) return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendNodeRecursive(child)
            if (found != null) return found
        }

        return null
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceAccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
