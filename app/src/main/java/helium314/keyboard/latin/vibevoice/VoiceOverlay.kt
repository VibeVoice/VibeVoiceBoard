// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.sin

/**
 * The floating mark that takes the keyboard's place while a session keeps running without it.
 *
 * When dictation survives the keyboard being dismissed there is nothing on screen to say so. The
 * notification says it, but it says it behind a swipe, which is no use to someone who is mid
 * sentence. This is the same statement at a glance: the VibeVoice mark, pulsing with what the
 * microphone is actually hearing, draggable out of the way, and a tap ends the session.
 *
 * It is not what keeps the recording alive -- [VoiceSessionService] does that, and does it whether
 * this is on screen or not. This is only the indicator, which is why it is allowed to be absent:
 * drawing over other apps is a permission the user grants in the system settings, and without it
 * everything still works with the notification alone.
 */
class VoiceOverlay(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val drawable = ContextCompat.getDrawable(context, R.drawable.ic_vibevoice_active)

    private var levelSource: WeakReference<VibeVoiceClient>? = null
    private var level = 0f
    private var pulse = 0f
    private var running = false

    /** Set by [show]; a tap runs it. */
    private var onTap: Runnable? = null

    private fun start(client: VibeVoiceClient, onTap: Runnable) {
        this.levelSource = WeakReference(client)
        this.onTap = onTap
        running = true
        invalidate()
    }

    private fun stopAnimating() {
        running = false
        levelSource = null
        onTap = null
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return
        val raw = levelSource?.get()?.currentLevel ?: 0f
        // Same asymmetry as the keyboard waves: jump on a syllable, ease back down. A mark that
        // tracked the level exactly would look like it was flickering rather than listening.
        level += (raw - level) * (if (raw > level) ATTACK else RELEASE)
        pulse += PULSE_STEP

        val cx = width / 2f
        val cy = height / 2f
        val base = SIZE_DP * density / 2f

        // The resting breath keeps it visibly alive during a pause, so a silence does not look like
        // a session that has died.
        val breath = 1f + BREATH * sin(pulse)
        val ringRadius = base * (0.72f + level * 0.5f) * breath
        ringPaint.strokeWidth = 2f * density
        ringPaint.color = Color.argb((90 + 140 * level).toInt().coerceIn(0, 255), 0x9F, 0x00, 0xA1)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        markPaint.color = Color.argb(235, 20, 20, 24)
        canvas.drawCircle(cx, cy, base * 0.68f, markPaint)

        drawable?.let {
            val half = (base * 0.4f).toInt()
            it.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
            it.draw(canvas)
        }

        postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParams as? WindowManager.LayoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragged = true
                if (dragged) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    try {
                        windowManager(context).updateViewLayout(this, params)
                    } catch (e: Exception) {
                        VibeVoiceDebugLogger.log("Could not move the overlay: ${e.message}")
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // A drag must not stop the session. Getting it out of the way and ending it are
                // both things people will do in a hurry, and confusing them costs a transcript.
                if (!dragged) {
                    performClick()
                    onTap?.run()
                }
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    companion object {
        private const val SIZE_DP = 56f
        private const val FRAME_INTERVAL_MS = 33L
        private const val ATTACK = 0.6f
        private const val RELEASE = 0.18f
        private const val PULSE_STEP = 0.12f
        private const val BREATH = 0.05f

        private var current: VoiceOverlay? = null

        private fun windowManager(context: Context): WindowManager =
            context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        /**
         * Whether the system will let us draw over other apps. False is a normal state, not an
         * error: the user has to grant this in the system settings, and nothing else depends on it.
         */
        @JvmStatic
        fun isAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(context)

        /** Puts the mark on screen. Does nothing, quietly, when the permission is not granted. */
        @JvmStatic
        fun show(context: Context, client: VibeVoiceClient, onTap: Runnable) {
            if (current != null) return
            if (!isAllowed(context)) {
                VibeVoiceDebugLogger.log("Overlay not shown: drawing over other apps is not allowed")
                return
            }
            val app = context.applicationContext
            val overlay = VoiceOverlay(app)
            val size = (SIZE_DP * app.resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                size, size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // NOT_FOCUSABLE so it never takes input away from the app underneath, which would
                // be a keyboard stealing focus from the field it is meant to be typing into.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = (app.resources.displayMetrics.widthPixels - size * 1.4f).toInt()
            params.y = (app.resources.displayMetrics.heightPixels * 0.62f).toInt()
            try {
                windowManager(app).addView(overlay, params)
            } catch (e: Exception) {
                // The permission can be revoked between the check and here, and some OEM builds
                // refuse the window anyway. The session is unaffected.
                VibeVoiceDebugLogger.log("Could not add the overlay: ${e.message}")
                return
            }
            overlay.start(client, onTap)
            current = overlay
            VibeVoiceDebugLogger.log("Overlay shown")
        }

        /** Takes it off screen. Safe to call when there is none. */
        @JvmStatic
        fun hide(context: Context) {
            val overlay = current ?: return
            current = null
            overlay.stopAnimating()
            try {
                windowManager(context).removeView(overlay)
                VibeVoiceDebugLogger.log("Overlay hidden")
            } catch (e: Exception) {
                VibeVoiceDebugLogger.log("Could not remove the overlay: ${e.message}")
            }
        }

        @JvmStatic
        fun isShowing(): Boolean = current != null
    }
}
