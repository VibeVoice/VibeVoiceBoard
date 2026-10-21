// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * The two targets a running session is dragged onto to end it: an X that drops what has not been
 * written anywhere yet, and a clipboard that keeps it.
 *
 * The window is added once, with the mark, and only made visible once a drag begins. Adding a window
 * is a round trip to the window manager, and doing that in the middle of a gesture cost the first
 * frames of the drag -- which is what made dropping onto it feel like it needed a second attempt.
 *
 * Visible only while [VoiceOverlay] is being dragged. Ending a session is the one action here that
 * cannot be taken back -- the words already spoken are committed, but nothing more is heard -- so
 * its target is absent at every moment when nobody is reaching for it, and it takes a deliberate
 * drag across the screen rather than a tap that a thumb can make by accident.
 *
 * It never takes touches of its own: the drag belongs to the mark, and this only has to be looked
 * at. That is why it is FLAG_NOT_TOUCHABLE, and why the hit test lives in [VoiceOverlay] against
 * this window's known position rather than here.
 */
class DismissTarget(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var discColor = Color.argb(235, 20, 20, 24)
    private var markColor = Color.rgb(0x9F, 0x00, 0xA1)

    /** Which target the mark is over: [TARGET_NONE], [TARGET_DISCARD] or [TARGET_CLIPBOARD]. */
    private var armed = TARGET_NONE
    private val grow = floatArrayOf(0f, 0f)

    fun setColors(disc: Int, mark: Int) {
        discColor = disc
        markColor = mark
    }

    fun arm(value: Int) {
        if (armed == value) return
        armed = value
        invalidate()
    }

    /**
     * Where the X actually is, in screen coordinates.
     *
     * Asked of the view rather than computed from DisplayMetrics. This window uses
     * FLAG_LAYOUT_NO_LIMITS and is anchored to the true bottom of the screen, while
     * `displayMetrics.heightPixels` stops above the navigation bar -- so a hit test built from the
     * metrics sat a navigation bar's height above the X that was drawn. Close enough to look
     * right, far enough that the drop missed.
     */
    fun centerOnScreen(target: Int, out: FloatArray) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val offset = (if (target == TARGET_CLIPBOARD) SPREAD_DP else -SPREAD_DP) * density
        out[0] = loc[0] + width / 2f + offset
        out[1] = loc[1] + (height - TARGET_BOTTOM_DP * density)
    }

    override fun onDraw(canvas: Canvas) {
        // Eased rather than snapped, so crossing the boundary reads as the target reacting to the
        // mark rather than as a redraw.
        var again = false
        for (i in grow.indices) {
            val target = if (armed == i) 1f else 0f
            grow[i] += (target - grow[i]) * 0.35f
            if (abs(grow[i] - target) > 0.01f) again = true
        }
        if (again) postInvalidateOnAnimation()

        val cy = height - TARGET_BOTTOM_DP * density
        drawTarget(canvas, width / 2f - SPREAD_DP * density, cy, grow[TARGET_DISCARD], cross = true)
        drawTarget(canvas, width / 2f + SPREAD_DP * density, cy, grow[TARGET_CLIPBOARD], cross = false)
    }

    /**
     * One target: a disc with a glyph. Armed inverts the two -- the disc fills with the mark's own
     * colour and the glyph is cut out of it. Saying "let go now" by changing what the thing is,
     * rather than by adding a label.
     */
    private fun drawTarget(canvas: Canvas, cx: Float, cy: Float, g: Float, cross: Boolean) {
        val radius = (RADIUS_DP + GROW_DP * g) * density
        discPaint.color = if (g > 0.5f) markColor else discColor
        discPaint.alpha = (200 + 55 * g).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, discPaint)

        crossPaint.color = if (g > 0.5f) discColor else markColor
        crossPaint.strokeWidth = (2.4f + g) * density
        val arm = radius * 0.34f
        if (cross) {
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, crossPaint)
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, crossPaint)
        } else {
            // A clipboard: the board, and the clip on top of it.
            val w = arm * 1.5f
            val h = arm * 1.9f
            board.set(cx - w / 2f, cy - h / 2f + arm * 0.18f, cx + w / 2f, cy + h / 2f)
            canvas.drawRoundRect(board, arm * 0.28f, arm * 0.28f, crossPaint)
            val clipW = w * 0.52f
            board.set(cx - clipW / 2f, cy - h / 2f - arm * 0.2f, cx + clipW / 2f, cy - h / 2f + arm * 0.36f)
            canvas.drawRoundRect(board, arm * 0.16f, arm * 0.16f, crossPaint)
        }
    }

    private val board = android.graphics.RectF()

    private fun abs(v: Float) = if (v < 0f) -v else v

    companion object {
        const val TARGET_NONE = -1
        const val TARGET_DISCARD = 0
        const val TARGET_CLIPBOARD = 1

        private const val RADIUS_DP = 28f
        /** How far each target sits from the middle. Two discs, a thumb's width apart. */
        private const val SPREAD_DP = 46f
        private const val GROW_DP = 8f
        /** Must match VoiceOverlay's TARGET_BOTTOM_DP: the hit test is done against this position. */
        private const val TARGET_BOTTOM_DP = 104f
        private const val HEIGHT_DP = 220f

        private var current: DismissTarget? = null

        private fun windowManager(context: Context): WindowManager =
            context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        /** A target's centre in screen coordinates, or false when there is no target window. */
        @JvmStatic
        fun centerOnScreen(target: Int, out: FloatArray): Boolean {
            val view = current ?: return false
            if (view.width == 0 || view.height == 0) return false
            view.centerOnScreen(target, out)
            return true
        }

        /** Makes the already-added window visible. Cheap: no window is created here. */
        @JvmStatic
        fun reveal() {
            current?.let { if (it.visibility != VISIBLE) it.visibility = VISIBLE }
        }

        @JvmStatic
        fun conceal() {
            current?.let {
                it.arm(TARGET_NONE)
                if (it.visibility != INVISIBLE) it.visibility = INVISIBLE
            }
        }

        @JvmStatic
        fun attach(context: Context, discColor: Int, markColor: Int) {
            if (current != null) return
            val app = context.applicationContext
            val view = DismissTarget(app)
            view.setColors(discColor, markColor)
            // Added now, shown later. The window exists for the whole session so that the drag
            // never has to wait for one to be created.
            view.visibility = INVISIBLE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (HEIGHT_DP * app.resources.displayMetrics.density).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            try {
                windowManager(app).addView(view, params)
                current = view
            } catch (e: Exception) {
                // Same permission as the mark itself, so this should not happen once that one is up,
                // but a missing target must never take the drag with it.
                VibeVoiceDebugLogger.log("Could not add the dismiss target: ${e.message}")
            }
        }

        @JvmStatic
        fun setArmed(armed: Int) {
            current?.arm(armed)
        }

        @JvmStatic
        fun detach(context: Context) {
            val view = current ?: return
            current = null
            try {
                windowManager(context).removeView(view)
            } catch (e: Exception) {
                VibeVoiceDebugLogger.log("Could not remove the dismiss target: ${e.message}")
            }
        }
    }
}
