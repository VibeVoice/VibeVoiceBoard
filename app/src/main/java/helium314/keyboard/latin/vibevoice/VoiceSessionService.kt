// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R

/**
 * Keeps a dictation session alive while the keyboard is not on screen.
 *
 * This exists for one platform reason. From Android 11 a process with no visible window may not use
 * the microphone, and it is not told so: [android.media.AudioRecord] simply returns zeroed buffers.
 * An input method counts as visible only while its input view is up, so the moment the keyboard is
 * dismissed a session that keeps reading gets silence, notices two seconds later and goes into a
 * recovery that cannot succeed. A foreground service with `foregroundServiceType="microphone"` is
 * the documented way out, and the ongoing notification is not decoration -- the system requires it.
 *
 * The service deliberately does **not** own the session's logic. [VibeVoiceClient] is still created
 * and driven by [helium314.keyboard.latin.LatinIME], which holds the state machine that turns
 * results into composing text; this class only holds a reference for as long as the session runs,
 * so the process keeps a foreground component while it does. Splitting it that way keeps the
 * coupling between recording state and what is shown in one place, which is where this fork has
 * repeatedly broken it.
 *
 * See `docs/background_dictation.md`.
 */
class VoiceSessionService : Service() {

    private var client: VibeVoiceClient? = null
    private var onStopRequested: Runnable? = null
    private var transcript: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // attach() may have run before this instance existed -- startForegroundService only queues
        // the start. Whatever it left behind is picked up here.
        claimPending(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VibeVoiceDebugLogger.log("Stop requested from the notification")
            // Routed back to LatinIME rather than stopping the client here: stopping a session is a
            // state transition (mIsStoppingVoice, the pending final, the composing text) and that
            // state lives there. Reaching around it would leave the keyboard believing it is still
            // recording.
            onStopRequested?.run()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        // START_NOT_STICKY: a session that died with the process should stay dead. Restarting the
        // service without the client it was holding would show a notification for a session that
        // does not exist.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        client = null
        onStopRequested = null
        super.onDestroy()
    }

    /**
     * The type has to be passed here as well as declared in the manifest from Android 10 on, and
     * from Android 14 a mismatch between the two is a crash rather than a warning. ServiceCompat
     * ignores the type on the versions that predate it.
     */
    private fun startForegroundNotification() {
        createChannel()
        androidx.core.app.ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // The Class overload of getSystemService is API 23 and this app still supports 21.
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW: it must be visible for as long as the microphone is open, and it must
        // never make a sound while the user is dictating into it.
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.vibevoice_session_channel), NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, VoiceSessionService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vibevoice_active)
            .setContentTitle(getString(R.string.vibevoice_session_title))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, getString(R.string.vibevoice_session_stop), stopPending)
        // The live transcript is the whole point of showing anything: it is the only way to tell a
        // session that is hearing you from one that is recording silence. VISIBILITY_SECRET above
        // keeps it off the lock screen, because it is the user's words.
        if (transcript.isNotBlank()) {
            builder.setContentText(transcript)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(transcript))
        } else {
            builder.setContentText(getString(R.string.vibevoice_session_listening))
        }
        return builder.build()
    }

    private fun refreshNotification() {
        if (instance == null) return
        // From Android 13 posting is a runtime permission, and an input method cannot ask for one
        // itself. Denied only costs the display: startForeground is unaffected, so the service
        // keeps running and the microphone stays open with no notification to show for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Posting can fail when notifications are denied (Android 13+). The service keeps
            // running and the microphone stays open; only the display is lost.
            VibeVoiceDebugLogger.log("Could not update the session notification: ${e.message}")
        }
    }

    companion object {
        private const val CHANNEL_ID = "vibevoice_session"
        private const val NOTIFICATION_ID = 0x71B3
        private const val ACTION_STOP = "org.vibevoice.board.STOP_SESSION"

        @Volatile
        private var instance: VoiceSessionService? = null

        /** True while a session is being held open by the service. */
        @JvmStatic
        fun isRunning(): Boolean = instance?.client != null

        /**
         * Starts the foreground service and hands it the running session to hold.
         *
         * Must be called while the keyboard is on screen: a microphone-typed foreground service may
         * not be started from the background. That is the normal path -- a session begins with a
         * key press -- but it is the reason this is called at session start and not lazily when the
         * window hides, which would be too late.
         */
        @JvmStatic
        fun attach(context: Context, session: VibeVoiceClient, onStop: Runnable) {
            val intent = Intent(context.applicationContext, VoiceSessionService::class.java)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                // A failed start must not take the session with it: without the service the
                // recording simply does not survive the keyboard being dismissed, which is the
                // behaviour we had before this existed.
                VibeVoiceDebugLogger.log("Could not start the session service: ${e.message}")
                return
            }
            // onStartCommand runs on the main thread, and so does this, so by the time a later
            // message runs the instance is set. Assigning through the companion covers the case
            // where it is already up from a previous session in the same process.
            instance?.let {
                it.client = session
                it.onStopRequested = onStop
                it.transcript = ""
            } ?: run {
                pendingClient = session
                pendingOnStop = onStop
            }
        }

        /** Ends the foreground state. The session itself is stopped by its owner, not here. */
        @JvmStatic
        fun detach(context: Context) {
            pendingClient = null
            pendingOnStop = null
            val service = instance
            if (service != null) {
                service.client = null
                service.onStopRequested = null
            }
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, VoiceSessionService::class.java)
                )
            } catch (e: Exception) {
                VibeVoiceDebugLogger.log("Could not stop the session service: ${e.message}")
            }
        }

        /** Puts the latest transcript in the notification, so it shows what is being heard. */
        @JvmStatic
        fun showTranscript(text: String) {
            val service = instance ?: return
            if (service.transcript == text) return
            service.transcript = text
            service.refreshNotification()
        }

        @Volatile private var pendingClient: VibeVoiceClient? = null
        @Volatile private var pendingOnStop: Runnable? = null

        internal fun claimPending(service: VoiceSessionService) {
            service.client = pendingClient
            service.onStopRequested = pendingOnStop
            pendingClient = null
            pendingOnStop = null
        }
    }
}
