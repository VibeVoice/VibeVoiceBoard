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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
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

    /** Set once the user has asked to stop, while the last result is still on its way. */
    private var finishing = false

    private val notificationHandler = Handler(Looper.getMainLooper())
    private var lastPostedAt = 0L
    private val postRunnable = Runnable { postNotification() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VibeVoiceDebugLogger.log("Stop requested from the notification")
            // Say so before asking, and drop the button. Stopping waits for the last result, which
            // can take a second or two; an unchanged notification reads as a tap that did nothing,
            // and the second tap forces the session to finish and throws that result away.
            finishing = true
            postNotification()
            // Routed back to LatinIME rather than stopping the client here: stopping a session is a
            // state transition (mIsStoppingVoice, the pending final, the composing text) and that
            // state lives there. Reaching around it would leave the keyboard believing it is still
            // recording.
            onStopRequested?.run()
            return START_NOT_STICKY
        }
        // Whatever attach() left behind is claimed here rather than in onCreate, because this runs
        // for an instance that already exists as well as for a fresh one.
        claimPending(this)
        startForegroundNotification()
        // START_NOT_STICKY: a session that died with the process should stay dead. Restarting the
        // service without the client it was holding would show a notification for a session that
        // does not exist.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notificationHandler.removeCallbacks(postRunnable)
        instance = null
        client = null
        onStopRequested = null
        // Explicitly, rather than trusting the notification to go with the service. An ongoing
        // notification that outlives its service is not merely untidy: its stop button reaches a
        // service with nothing to stop, so it says a session is running that is not.
        try {
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not clear the session notification: ${e.message}")
        }
        super.onDestroy()
    }

    /**
     * The type has to be passed here as well as declared in the manifest from Android 10 on, and
     * from Android 14 a mismatch between the two is a crash rather than a warning. ServiceCompat
     * ignores the type on the versions that predate it.
     */
    private fun startForegroundNotification() {
        createChannel()
        try {
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            lastPostedAt = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException from Android 12, or a SecurityException if
            // RECORD_AUDIO went away between the start and here. A started service that never
            // reaches the foreground is worse than none: it would be killed for it. The session
            // itself carries on and simply does not survive the keyboard being dismissed.
            VibeVoiceDebugLogger.log("startForeground refused: ${e.message}")
            stopSelf()
        }
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
        if (!finishing) {
            builder.addAction(0, getString(R.string.vibevoice_session_stop), stopPending)
        }
        // The live transcript is the whole point of showing anything: it is the only way to tell a
        // session that is hearing you from one that is recording silence. VISIBILITY_SECRET above
        // keeps it off the lock screen, because it is the user's words.
        if (finishing) {
            builder.setContentText(getString(R.string.vibevoice_session_finishing))
        } else if (transcript.isNotBlank()) {
            builder.setContentText(transcript)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(transcript))
        } else {
            builder.setContentText(getString(R.string.vibevoice_session_listening))
        }
        return builder.build()
    }

    /**
     * Rate-limits the posting. Partial results arrive several times a second while someone is
     * speaking, and one Binder call to the notification service per partial is both wasted work and
     * enough to hit the framework's own "posting too frequently" limiter, which drops updates -- so
     * posting less often actually shows more. The trailing post matters: the last partial before a
     * pause is the one left on screen.
     */
    private fun refreshNotification() {
        if (instance == null) return
        val since = SystemClock.uptimeMillis() - lastPostedAt
        notificationHandler.removeCallbacks(postRunnable)
        if (since >= MIN_POST_INTERVAL_MS) {
            postNotification()
        } else {
            notificationHandler.postDelayed(postRunnable, MIN_POST_INTERVAL_MS - since)
        }
    }

    private fun postNotification() {
        if (instance == null) return
        // From Android 13 posting is a runtime permission. Denied only costs the display:
        // startForeground is unaffected, so the service keeps running and the microphone stays open
        // with no notification to show for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            lastPostedAt = SystemClock.uptimeMillis()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
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
            // Always left for onStartCommand to pick up, never written into `instance` here.
            // Between a detach and this call the old service can be alive but already scheduled for
            // destruction: writing into it would hand the new session to an instance whose
            // onDestroy is still queued, and that onDestroy would then null it out again. The next
            // onStartCommand -- on whichever instance the platform gives us -- claims it instead.
            pendingClient = session
            pendingOnStop = onStop
            pendingTranscript = true
        }

        /** Ends the foreground state. The session itself is stopped by its owner, not here. */
        @JvmStatic
        fun detach(context: Context) {
            pendingClient = null
            pendingOnStop = null
            pendingTranscript = false
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

        private const val MIN_POST_INTERVAL_MS = 400L

        @Volatile private var pendingClient: VibeVoiceClient? = null
        @Volatile private var pendingOnStop: Runnable? = null
        @Volatile private var pendingTranscript = false

        internal fun claimPending(service: VoiceSessionService) {
            if (pendingClient == null && !pendingTranscript) return
            service.client = pendingClient
            service.onStopRequested = pendingOnStop
            if (pendingTranscript) {
                service.transcript = ""
                service.finishing = false
            }
            pendingClient = null
            pendingOnStop = null
            pendingTranscript = false
        }
    }
}
