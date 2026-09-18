// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView

/**
 * A toolbar carousel that only scrolls while there is somewhere to scroll to.
 *
 * The keys are shrunk until the whole set fits (see SuggestionStripView.fitToolbarKeys), so most
 * people never have anything hidden. For them a scrollbar is worse than no affordance at all: it
 * promises more tools somewhere off the edge, and dragging it delivers nothing. Android only draws
 * a bar when the content overflows, but overflow of a few pixels -- a stray padding, a rounding --
 * counts, and that is exactly the case where the bar lies.
 *
 * So scrolling is off by default and switched on only by the fit pass, which is the one place that
 * knows whether anything is actually out of view. Off means off: no bar, and no drag either, since
 * a toolbar that slides a few pixels under the finger and springs back reads as broken.
 */
class ToolbarScrollView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {

    var isScrollingEnabled = false
        set(value) {
            if (field == value) return
            field = value
            isHorizontalScrollBarEnabled = value
            if (!value && scrollX != 0) scrollTo(0, 0)
        }

    init {
        // The XML declares the bar so its size and thumb colour are configured; whether it is drawn
        // is this class's business, and it starts silent.
        isHorizontalScrollBarEnabled = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent) = isScrollingEnabled && super.onInterceptTouchEvent(ev)

    @SuppressLint("ClickableViewAccessibility") // the keys handle their own clicks, this only ever declines to scroll
    override fun onTouchEvent(ev: MotionEvent) = isScrollingEnabled && super.onTouchEvent(ev)
}
