package com.spkup.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spkup.app.MainActivity
import com.spkup.app.audio.ZeroBeepAudioRecorder
import com.spkup.app.data.SpkUpPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Foreground Service hosting the floating bubble overlay across other apps.
 */
class FloatingBubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "spkup_overlay_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var prefsJob: Job? = null

    private lateinit var prefs: SpkUpPreferences
    private lateinit var audioRecorder: ZeroBeepAudioRecorder
    private lateinit var overlayManager: FloatingBubbleOverlayManager

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        prefs = SpkUpPreferences.getInstance(this)
        audioRecorder = ZeroBeepAudioRecorder(this)
        overlayManager = FloatingBubbleOverlayManager(this, prefs, audioRecorder)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        overlayManager.showOverlay { pcmAudio ->
            // Audio captured losslessly with 450ms tail buffer
            com.spkup.app.service.VoiceAccessibilityService.instance?.processAndInjectAudio(pcmAudio)
        }

        // Watch preference toggle
        prefsJob = scope.launch {
            prefs.isFloatingBubbleEnabledFlow.collectLatest { enabled ->
                if (!enabled) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SpkUP Floating Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active voice typing overlay"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SpkUP Voice Typing")
            .setContentText("Tap or hold the floating bubble to dictate")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isServiceRunning = false
        prefsJob?.cancel()
        overlayManager.hideOverlay()
        audioRecorder.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
