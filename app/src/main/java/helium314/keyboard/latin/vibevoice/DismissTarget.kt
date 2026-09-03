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
 * The X that a running session is dragged onto to end it.
 *
 * On screen only while [VoiceOverlay] is being dragged. Ending a session is the one action here that
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

    /** True while the mark is inside the catch radius. */
    private var armed = false
    private var grow = 0f

    fun setColors(disc: Int, mark: Int) {
        discColor = disc
        markColor = mark
    }

    fun arm(value: Boolean) {
        if (armed == value) return
        armed = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Eased rather than snapped, so crossing the boundary reads as the target reacting to the
        // mark rather than as a redraw.
        val target = if (armed) 1f else 0f
        grow += (target - grow) * 0.35f
        if (abs(grow - target) > 0.01f) postInvalidateOnAnimation()

        val cx = width / 2f
        val cy = height - TARGET_BOTTOM_DP * density
        val radius = (RADIUS_DP + GROW_DP * grow) * density

        // Armed inverts the two: the disc fills with the mark's own colour and the X is cut out of
        // it. Saying "let go now" by changing what the thing is, not by adding a label.
        discPaint.color = if (grow > 0.5f) markColor else discColor
        discPaint.alpha = (200 + 55 * grow).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, discPaint)

        crossPaint.color = if (grow > 0.5f) discColor else markColor
        crossPaint.strokeWidth = (2.4f + grow) * density
        val arm = radius * 0.34f
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, crossPaint)
        canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, crossPaint)
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    companion object {
        private const val RADIUS_DP = 28f
        private const val GROW_DP = 8f
        /** Must match VoiceOverlay's TARGET_BOTTOM_DP: the hit test is done against this position. */
        private const val TARGET_BOTTOM_DP = 104f
        private const val HEIGHT_DP = 220f

        private var current: DismissTarget? = null

        private fun windowManager(context: Context): WindowManager =
            context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        @JvmStatic
        fun show(context: Context, discColor: Int, markColor: Int) {
            if (current != null) return
            val app = context.applicationContext
            val view = DismissTarget(app)
            view.setColors(discColor, markColor)
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
        fun setArmed(armed: Boolean) {
            current?.arm(armed)
        }

        @JvmStatic
        fun hide(context: Context) {
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
