// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.cos
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
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // The ordinary mark, not the purple one. Its colour comes from the glow behind it now, and
    // the launcher asset is the one that is kept in step with the brand.
    private val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
    /** The mark's own silhouette, blurred. Built once per session, never per frame. */
    private var glowBitmap: Bitmap? = null
    private val glowMargin = IntArray(1)
    private var glowBox = 0

    private var levelSource: WeakReference<VibeVoiceClient>? = null
    private var level = 0f
    private var pulse = 0f
    private var running = false

    /**
     * The geometry, read once per session. Expressed from the inside out: the V decides the size,
     * the padding decides the disc around it, the bars grow outward from that. Changing one does
     * not rescale the others, which is the point of tuning them separately.
     */
    private var iconPx = Defaults.PREF_OVERLAY_ICON * density
    private var discRadiusPx = 0f
    private var barLengthPx = 0f
    private var barWidthPx = Defaults.PREF_OVERLAY_BAR_WIDTH * density
    private var barCount = Defaults.PREF_OVERLAY_BAR_COUNT.toInt()
    private var restHeight = Defaults.PREF_OVERLAY_REST

    /** Per-bar heights, smoothed between frames so a bar falls back rather than snapping. */
    private var bars = FloatArray(Defaults.PREF_OVERLAY_BAR_COUNT.toInt() / 2 + 1)

    private fun readGeometry(context: Context) {
        val p = context.prefs()
        iconPx = p.getFloat(Settings.PREF_OVERLAY_ICON, Defaults.PREF_OVERLAY_ICON) * density
        val padding = p.getFloat(Settings.PREF_OVERLAY_PADDING, Defaults.PREF_OVERLAY_PADDING) * density
        discRadiusPx = iconPx / 2f + padding
        barLengthPx = p.getFloat(Settings.PREF_OVERLAY_BARS, Defaults.PREF_OVERLAY_BARS) * density
        barWidthPx = p.getFloat(Settings.PREF_OVERLAY_BAR_WIDTH, Defaults.PREF_OVERLAY_BAR_WIDTH) * density
        barCount = p.getFloat(Settings.PREF_OVERLAY_BAR_COUNT, Defaults.PREF_OVERLAY_BAR_COUNT)
            .toInt().coerceIn(8, 96)
        restHeight = p.getFloat(Settings.PREF_OVERLAY_REST, Defaults.PREF_OVERLAY_REST)
        bars = FloatArray(barCount / 2 + 1)
    }

    /** The window this needs, given the geometry above. */
    private fun requiredSizePx(): Int =
        ((discRadiusPx + BAR_GAP_DP * density + barLengthPx) * 2f + barWidthPx).toInt()

    /** Both from the keyboard's own theme, so the mark reads as part of it. */
    var discColor = Color.argb(235, 20, 20, 24)
        private set
    var barColor = Color.rgb(0x62, 0x9D, 0xF6)
        private set

    private fun readThemeColors() {
        try {
            val colors = Settings.getValues().mColors
            discColor = colors.get(ColorType.MAIN_BACKGROUND)
            barColor = colors.get(ColorType.GESTURE_TRAIL)
        } catch (e: Exception) {
            // Settings not loaded; the defaults above stand in for one session.
            VibeVoiceDebugLogger.log("Overlay could not read the theme: ${e.message}")
        }
    }

    /** Set by [show]; running it ends the session. Reached by dropping on the target, never by a tap. */
    private var onDismiss: Runnable? = null

    private fun start(client: VibeVoiceClient, onDismiss: Runnable) {
        this.levelSource = WeakReference(client)
        this.onDismiss = onDismiss
        readThemeColors()
        bars.fill(0f)
        running = true
        invalidate()
    }

    private fun stopAnimating() {
        stopFling()
        sampleSize = 0
        glowBitmap?.recycle()
        glowBitmap = null
        glowBox = 0
        running = false
        levelSource = null
        onDismiss = null
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return
        val raw = levelSource?.get()?.currentLevel ?: 0f
        // Same asymmetry as the keyboard waves: jump on a syllable, ease back down. A mark that
        // tracked the level exactly would look like it was flickering rather than listening.
        level += (raw - level) * (if (raw > level) ATTACK else RELEASE)
        pulse += PULSE_STEP
        // Speech reaches full excursion sooner, so the top of the range is used rather than being
        // somewhere the voice only theoretically gets to.
        val drive = (level * LEVEL_DRIVE).coerceIn(0f, 1f)

        val cx = width / 2f
        val cy = height / 2f
        val base = width / 2f
        val discRadius = discRadiusPx

        // The disc first, in the keyboard's own background colour, so the bars stand on the same
        // ground the keys do.
        discPaint.color = discColor
        canvas.drawCircle(cx, cy, discRadius, discPaint)

        // A ring of bars around it, the way an audio visualiser draws a spectrum: each bar is a
        // slice of the same composite the keyboard's waves are drawn from, so the two read as one
        // thing. Only half are computed and the ring is mirrored, which is what stops it looking
        // like noise -- an asymmetric ring reads as random, a symmetric one reads as a waveform.
        val half = bars.size - 1
        if (half <= 0) return
        for (i in bars.indices) {
            val t = i / half.toFloat()
            // The profile falls from the top of the ring towards the bottom, so the ring has a
            // shape of its own even before the voice moves it.
            val profile = PROFILE_FLOOR + (1f - PROFILE_FLOOR) * (0.5f + 0.5f * cos(t * Math.PI.toFloat()))
            val wave = sin(t * SPAN + pulse) +
                    sin(t * SPAN * SECOND_RATIO - pulse * 0.7f) * 0.45f +
                    cos(t * SPAN * THIRD_RATIO + pulse * 0.6f) * 0.25f
            // The per-bar term used to keep every bar above 55% of the envelope, which is why the
            // ring moved as one piece rather than as a waveform. At 18% a quiet bar is genuinely
            // short next to a loud one, which is the whole difference between a level meter and a
            // spectrum.
            val target = (profile * (restHeight + drive * (1f - restHeight)) *
                    (0.18f + 0.82f * abs(wave) / 1.7f)).coerceIn(0f, 1f)
            // Asymmetric smoothing, as everywhere else here: rise with the syllable, fall behind it.
            bars[i] += (target - bars[i]) * (if (target > bars[i]) BAR_ATTACK else BAR_RELEASE)
        }

        barPaint.strokeWidth = barWidthPx
        val inner = discRadius + BAR_GAP_DP * density
        val maxOut = base - barWidthPx * 0.5f
        for (b in 0 until barCount) {
            // Mirrored: index 0 at the top, walking down both sides.
            val idx = if (b <= half) b else barCount - b
            val h = bars[idx.coerceIn(0, half)]
            val angle = (-Math.PI / 2 + b * 2.0 * Math.PI / barCount).toFloat()
            val ca = cos(angle)
            val sa = sin(angle)
            val outer = inner + (maxOut - inner) * h
            barPaint.color = Color.argb(
                (110 + 145 * h).toInt().coerceIn(0, 255),
                Color.red(barColor), Color.green(barColor), Color.blue(barColor)
            )
            canvas.drawLine(cx + ca * inner, cy + sa * inner, cx + ca * outer, cy + sa * outer, barPaint)
        }

        drawable?.let { d ->
            // Placed by its ink, not by its viewport. A vector's drawing does not fill its
            // viewBox evenly -- this one sits up and to the left of centre -- so centring the
            // viewport put the artwork off centre, and it touched the disc at the top left first
            // as the padding came down. The ink's own box is measured once and centred instead,
            // which also makes "mark size" mean the size of what is actually visible.
            val ink = VoiceGlow.inkBounds(d)
            val span = maxOf(ink.width(), ink.height()).coerceAtLeast(0.01f)
            val box = iconPx / span
            val left = cx - box * (ink.left + ink.width() / 2f)
            val top = cy - box * (ink.top + ink.height() / 2f)

            // The light has the mark's shape, so it shows in the few pixels around the strokes and
            // nowhere else. Static: the ring of bars already carries the level, and two things
            // pulsing at once compete instead of agreeing.
            // Keyed on the size alone, not on the bitmap being null. Rendering can fail -- it
            // allocates several bitmaps and blurs one -- and a null result used to mean "try
            // again", which at thirty frames a second is a failing allocation retried thirty times
            // a second for as long as the session lasts. One attempt per size; a failure means no
            // glow, which is a mark without a halo rather than a burning battery.
            if (glowBox != box.toInt()) {
                glowBitmap?.recycle()
                glowBox = box.toInt()
                val p = context.prefs()
                glowBitmap = VoiceGlow.render(
                    d, glowBox, barColor,
                    p.getFloat(Settings.PREF_OVERLAY_GLOW_SIZE, Defaults.PREF_OVERLAY_GLOW_SIZE),
                    p.getFloat(Settings.PREF_OVERLAY_GLOW_GAIN, Defaults.PREF_OVERLAY_GLOW_GAIN),
                    glowMargin
                )
            }
            glowBitmap?.let { glow ->
                // The bitmap already carries the colour and the density, so it is drawn plainly.
                // Offset by the margin the blur needed, which puts the mark's own box back where
                // the drawable is about to go.
                canvas.drawBitmap(glow, left - glowMargin[0], top - glowMargin[0], null)
            }

            d.setBounds(left.toInt(), top.toInt(), (left + box).toInt(), (top + box).toInt())
            d.draw(canvas)
        }

        postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false
    /**
     * The tail of the drag, as (time, x, y) triples in a ring.
     *
     * Kept instead of a VelocityTracker because that one weights the very end of the gesture: a
     * thumb that wobbles as it lifts can hand back a velocity pointing the other way from the
     * movement it just made. Reading across a window instead averages the wobble out, and the
     * direction that survives is the one the hand was actually going.
     */
    private val sampleT = LongArray(SAMPLE_COUNT)
    private val sampleX = FloatArray(SAMPLE_COUNT)
    private val sampleY = FloatArray(SAMPLE_COUNT)
    private var sampleHead = 0
    private var sampleSize = 0

    /** Pixels per millisecond, carried after the finger lets go. */
    private var flingVx = 0f
    private var flingVy = 0f
    private var flinging = false
    private val flingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flingStep = Runnable { stepFling() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParams as? WindowManager.LayoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling()
                sampleSize = 0
                sampleHead = 0
                addSample(event)
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragged = false
                armedNow = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                addSample(event)
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragged && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragged = true
                    // Only made visible here; the window itself was added with the mark. The target
                    // is off screen at every moment when nobody is reaching for it, without a
                    // window round trip landing in the middle of the gesture.
                    DismissTarget.reveal()
                }
                if (dragged) {
                    val armed = isOverTarget(startX + dx, startY + dy)
                    if (armed && DismissTarget.centerOnScreen(targetCentre)) {
                        // Held on the target once inside its radius. Letting go is the moment that
                        // matters, and asking someone to keep a fingertip steady over a circle
                        // while talking is what made this need two or three attempts.
                        params.x = (targetCentre[0] - width / 2f).toInt()
                        params.y = (targetCentre[1] - height / 2f).toInt()
                    } else {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                    }
                    if (params.x != lastX || params.y != lastY) {
                        lastX = params.x
                        lastY = params.y
                        try {
                            windowManager(context).updateViewLayout(this, params)
                        } catch (e: Exception) {
                            VibeVoiceDebugLogger.log("Could not move the overlay: ${e.message}")
                        }
                    }
                    DismissTarget.setArmed(armed)
                    armedNow = armed
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // The armed state from the last move, not a fresh hit test: once the mark has
                // snapped to the target its own position is the target's, and re-testing would be
                // asking a question already answered.
                val onTarget = dragged && event.actionMasked == MotionEvent.ACTION_UP && armedNow
                addSample(event)
                val v = windowVelocity()
                val vx = v[0]
                val vy = v[1]
                // A tap does nothing on purpose. Getting the mark out of the way and ending the
                // session are both done in a hurry, and a tap that ended it would keep costing
                // transcripts to a slip of the thumb.
                if (onTarget) {
                    dismiss()
                } else if (!dragged) {
                    DismissTarget.conceal()
                    performClick()
                } else if (event.actionMasked == MotionEvent.ACTION_UP &&
                        (abs(vx) > MIN_FLING_PX_MS || abs(vy) > MIN_FLING_PX_MS)) {
                    // Thrown rather than placed: it carries on and slows down, the way anything
                    // dragged across a screen is expected to. The target stays up for the ride, so
                    // a throw aimed at it can land on it.
                    flingVx = vx
                    flingVy = vy
                    flinging = true
                    flingHandler.post(flingStep)
                } else {
                    DismissTarget.conceal()
                    rememberPosition()
                }
                dragged = false
                armedNow = false
                return true
            }
        }
        return false
    }

    /**
     * Ends the session, and takes the mark off screen first.
     *
     * The order is the whole of this. Stopping waits for the last result from the server, a second
     * or so, and hiding at the end of that made a drop feel like it had not registered. Nothing
     * about ending the session needs the mark to still be on screen.
     */
    private fun dismiss() {
        val run = onDismiss
        visibility = GONE
        DismissTarget.conceal()
        stopFling()
        VibeVoiceDebugLogger.log("Overlay dismissed")
        // Posted: this runs inside the view's own touch dispatch, and taking it out of the window
        // from in there is asking for trouble.
        flingHandler.post {
            hide(context)
            run?.run()
        }
    }

    private fun stopFling() {
        flinging = false
        flingHandler.removeCallbacks(flingStep)
    }

    /** Keeps where it was left, so the next session finds it in the same corner. */
    private fun rememberPosition() {
        val params = layoutParams as? WindowManager.LayoutParams ?: return
        try {
            context.prefs().edit()
                .putInt(Settings.PREF_OVERLAY_X, params.x)
                .putInt(Settings.PREF_OVERLAY_Y, params.y)
                .apply()
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not store the overlay position: ${e.message}")
        }
    }

    private fun addSample(event: MotionEvent) {
        sampleT[sampleHead] = event.eventTime
        sampleX[sampleHead] = event.rawX
        sampleY[sampleHead] = event.rawY
        sampleHead = (sampleHead + 1) % SAMPLE_COUNT
        if (sampleSize < SAMPLE_COUNT) sampleSize++
    }

    /**
     * Velocity in pixels per millisecond, measured across the whole window rather than between the
     * last two points.
     *
     * Straight across: the oldest sample still inside the window against the newest. A least
     * squares fit would weight the middle of the window more, which is the opposite of what is
     * wanted here -- the point is that no single sample, least of all the last one, can decide the
     * direction on its own.
     */
    private fun windowVelocity(): FloatArray {
        result[0] = 0f
        result[1] = 0f
        if (sampleSize < 2) return result
        val newest = (sampleHead - 1 + SAMPLE_COUNT) % SAMPLE_COUNT
        val tNewest = sampleT[newest]
        var oldest = newest
        for (i in 1 until sampleSize) {
            val idx = (newest - i + SAMPLE_COUNT) % SAMPLE_COUNT
            if (tNewest - sampleT[idx] > VELOCITY_WINDOW_MS) break
            oldest = idx
        }
        val dt = (tNewest - sampleT[oldest]).toFloat()
        if (dt <= 0f) return result
        result[0] = (sampleX[newest] - sampleX[oldest]) / dt
        result[1] = (sampleY[newest] - sampleY[oldest]) / dt
        return result
    }

    private val result = FloatArray(2)

    /**
     * One frame of the glide. Friction per frame at a fixed 16ms step, which keeps it predictable
     * and is the same thing as per-second friction at that rate.
     */
    private fun stepFling() {
        if (!flinging || !running) return
        val params = layoutParams as? WindowManager.LayoutParams ?: return
        val metrics = context.resources.displayMetrics
        var x = params.x + flingVx * FLING_STEP_MS
        var y = params.y + flingVy * FLING_STEP_MS
        // Clamped at the edges, and whichever component hit the edge is spent. A bounce would be
        // playful; this is a thing you park somewhere.
        val maxX = (metrics.widthPixels - width).toFloat()
        val maxY = (metrics.heightPixels - height).toFloat()
        if (x < 0f) { x = 0f; flingVx = 0f }
        if (x > maxX) { x = maxX; flingVx = 0f }
        if (y < 0f) { y = 0f; flingVy = 0f }
        if (y > maxY) { y = maxY; flingVy = 0f }
        params.x = x.toInt()
        params.y = y.toInt()
        try {
            windowManager(context).updateViewLayout(this, params)
        } catch (e: Exception) {
            stopFling()
            return
        }
        val armed = isOverTarget(x, y)
        DismissTarget.setArmed(armed)
        if (armed) {
            // Thrown onto the target counts as dropped on it.
            dismiss()
            return
        }
        flingVx *= FLING_FRICTION
        flingVy *= FLING_FRICTION
        if (abs(flingVx) < MIN_FLING_PX_MS && abs(flingVy) < MIN_FLING_PX_MS) {
            stopFling()
            DismissTarget.conceal()
            rememberPosition()
            return
        }
        flingHandler.postDelayed(flingStep, FLING_STEP_MS.toLong())
    }

    private val targetCentre = FloatArray(2)
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var armedNow = false

    /**
     * Whether the mark's centre is inside the target's catch radius.
     *
     * The target's position is asked of the target's own window. Deriving it from DisplayMetrics
     * put the catch zone a navigation bar's height above the X that was on screen: near enough to
     * look like the drop should have worked, far enough that it did not.
     */
    private fun isOverTarget(left: Float, top: Float): Boolean {
        if (!DismissTarget.centerOnScreen(targetCentre)) return false
        val size = width.toFloat()
        val dx = left + size / 2f - targetCentre[0]
        val dy = top + size / 2f - targetCentre[1]
        return dx * dx + dy * dy <= (CATCH_RADIUS_DP * density) * (CATCH_RADIUS_DP * density)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    companion object {
        private const val FRAME_INTERVAL_MS = 33L
        /** How hard the measured level is pushed before it drives the bars. */
        private const val LEVEL_DRIVE = 1.35f

        private const val ATTACK = 0.6f
        private const val RELEASE = 0.18f
        private const val PULSE_STEP = 0.12f

        private const val BAR_GAP_DP = 3f
        private const val BAR_ATTACK = 0.55f
        private const val BAR_RELEASE = 0.16f
        /** Radians across half the ring. Under two periods, the same choice as the keyboard waves. */
        private const val SPAN = 5.2f
        private const val SECOND_RATIO = 1.92f
        private const val THIRD_RATIO = 0.75f
        private const val PROFILE_FLOOR = 0.45f
        private const val CATCH_RADIUS_DP = 88f
        private const val FLING_STEP_MS = 16f
        /** Per 16ms step. Enough travel to feel thrown, little enough not to wander off. */
        private const val FLING_FRICTION = 0.922f
        private const val MIN_FLING_PX_MS = 0.06f
        /** How far back the release velocity is read. Long enough to outvote a wobble. */
        private const val VELOCITY_WINDOW_MS = 110L
        private const val SAMPLE_COUNT = 16

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
        fun show(context: Context, client: VibeVoiceClient, onDismiss: Runnable) {
            if (current != null) return
            if (!isAllowed(context)) {
                VibeVoiceDebugLogger.log("Overlay not shown: drawing over other apps is not allowed")
                return
            }
            val app = context.applicationContext
            val overlay = VoiceOverlay(app)
            overlay.readGeometry(app)
            val size = overlay.requiredSizePx()
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
            // Where it was left. Dragging it into a corner and having it come back somewhere else
            // is the sort of thing that makes a floating control feel like it is not yours.
            val metrics = app.resources.displayMetrics
            val prefs = app.prefs()
            val storedX = prefs.getInt(Settings.PREF_OVERLAY_X, Defaults.PREF_OVERLAY_X)
            val storedY = prefs.getInt(Settings.PREF_OVERLAY_Y, Defaults.PREF_OVERLAY_Y)
            // Clamped rather than trusted: the screen it was parked on may have been the other way
            // round, and a corner remembered off the side of a rotated screen is a mark nobody can
            // reach.
            params.x = if (storedX >= 0) storedX.coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
                else (metrics.widthPixels - size * 1.4f).toInt()
            params.y = if (storedY >= 0) storedY.coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
                else (metrics.heightPixels * 0.62f).toInt()
            try {
                windowManager(app).addView(overlay, params)
            } catch (e: Exception) {
                // The permission can be revoked between the check and here, and some OEM builds
                // refuse the window anyway. The session is unaffected.
                VibeVoiceDebugLogger.log("Could not add the overlay: ${e.message}")
                return
            }
            overlay.start(client, onDismiss)
            DismissTarget.attach(app, overlay.discColor, overlay.barColor)
            current = overlay
            VibeVoiceDebugLogger.log("Overlay shown")
        }

        /** Takes it off screen. Safe to call when there is none. */
        @JvmStatic
        fun hide(context: Context) {
            DismissTarget.detach(context)
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
