// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min

/**
 * The drifting colour blobs from the landing page, behind the setup wizard.
 *
 * A port of `website_react/src/components/ui/AnimatedPageBackground.css`, arrangement `c-v` --
 * the one the site's own hero uses. Everything here is a value read out of that file rather than
 * chosen: the three colours in both themes, the 140vmin diameter, the two opacities with their two
 * blend modes, the three periods, and the keyframe tracks.
 *
 * WHY A CANVAS AND NOT A WEBVIEW
 *
 * The brief was "the header of the landing page, for recognition". That header is a wordmark, two
 * lines of text, a logo and these three gradients. None of it needs a DOM, and a WebView would have
 * meant a second rendering system, a cold start on the one screen where the first impression is
 * made, and an asset bundle to keep in step with a site that is deployed separately. Drawn here it
 * also costs nothing to put the same background behind every other wizard page, which a bundled
 * HTML file could never have done.
 *
 * WHY THERE IS NO BLUR
 *
 * The CSS carries a long comment on exactly this, and it is worth not relearning: the gradient runs
 * to transparent before it reaches its own edge, so the blur was smoothing banding rather than
 * shaping anything -- and it was the most expensive thing on the landing page. If banding ever does
 * show, the answer is a longer ramp, not a filter over a screen-sized element.
 *
 * WHY THE GROUND IS PAINTED HERE
 *
 * [BlendMode.Screen] and [BlendMode.Multiply] blend against what is already in the layer. Drawing
 * the blobs straight into a composable that sits over a Surface would blend them against whatever
 * that layer happens to hold, which is transparent black as often as not, and the multiply path
 * would then paint solid black. So this draws [background] first and blends over its own paint.
 */
@Composable
fun BrandBackground(background: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // The Android equivalent of prefers-reduced-motion, checked the same way VoiceWaveView checks
    // it. Off means the blobs are drawn once, at their resting keyframe, and never move.
    val animate = remember {
        try {
            AndroidSettings.Global.getFloat(ctx.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (_: Exception) {
            true
        }
    }

    val transition = rememberInfiniteTransition(label = "blobs")
    val still = remember { mutableFloatStateOf(0f) }

    @Composable fun phase(periodMs: Int, label: String): State<Float> =
        if (!animate) still
        else transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMs, easing = FastOutSlowInEasing),
                // `alternate` in the CSS shorthand. The tracks already return to their start at
                // 100%, so reversing turns each period into a six-stop round trip rather than a
                // jump back to the beginning.
                repeatMode = RepeatMode.Reverse
            ),
            label = label
        )

    val purple by phase(PURPLE_PERIOD_MS, "purple")
    val green by phase(GREEN_PERIOD_MS, "green")
    val blue by phase(BLUE_PERIOD_MS, "blue")

    val alpha = if (dark) ALPHA_DARK else ALPHA_LIGHT
    val blend = if (dark) BlendMode.Screen else BlendMode.Multiply

    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        val d = BLOB_VMIN * min(size.width, size.height)

        // Positions from `.bg-set-c-v`, expressed as the centre of a d x d box. CSS places the box
        // by its edges: `left: -30vw` is the left edge, `bottom: -40vh` is 40vh *below* the bottom.
        blob(
            colour = if (dark) PURPLE_DARK else PURPLE_LIGHT,
            centre = Offset(-0.30f * size.width + d / 2f, size.height + 0.40f * size.height - d / 2f),
            diameter = d, alpha = alpha, blend = blend, t = purple, track = PURPLE_TRACK
        )
        blob(
            colour = if (dark) GREEN_DARK else GREEN_LIGHT,
            centre = Offset(size.width + 0.30f * size.width - d / 2f, 0.30f * size.height - d / 2f),
            diameter = d, alpha = alpha, blend = blend, t = green, track = GREEN_TRACK
        )
        blob(
            colour = if (dark) BLUE_DARK else BLUE_LIGHT,
            centre = Offset(0.35f * size.width + d / 2f, 0.70f * size.height - d / 2f),
            diameter = d, alpha = alpha, blend = blend, t = blue, track = BLUE_TRACK
        )
    }
}

/**
 * One blob, at its position on [track] at time [t].
 *
 * The gradient is `radial-gradient(ellipse, colour 0%, transparent 70%)` with the default
 * `farthest-corner`, which -- see the CSS -- puts the transparent stop at 99% of the box edge on
 * both axes. Half the diameter is close enough to that to be the same picture, and it is the radius
 * the scale below stretches.
 */
private fun DrawScope.blob(
    colour: Color,
    centre: Offset,
    diameter: Float,
    alpha: Float,
    blend: BlendMode,
    t: Float,
    track: FloatArray
) {
    val at = sample(track, t)
    val moved = Offset(centre.x + at.dx * diameter, centre.y + at.dy * diameter)
    withTransform({ scale(at.sx, at.sy, pivot = moved) }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colour, Color.Transparent),
                center = moved,
                radius = diameter / 2f
            ),
            radius = diameter / 2f,
            center = moved,
            alpha = alpha,
            blendMode = blend
        )
    }
}

private data class Sample(val dx: Float, val dy: Float, val sx: Float, val sy: Float)

/**
 * Read a keyframe track at [t] in 0..1.
 *
 * A track is four stops of four values, at 0%, 33%, 66% and 100% -- the percentages the CSS uses.
 * Between stops it is linear; the easing that makes the drift feel unhurried is on the driving
 * animation, not here, which is also how the CSS splits it.
 */
private fun sample(track: FloatArray, t: Float): Sample {
    val stops = track.size / 4 - 1               // 3 segments
    val scaled = (t.coerceIn(0f, 1f) * stops)
    val i = scaled.toInt().coerceAtMost(stops - 1)
    val f = scaled - i
    val a = i * 4
    val b = a + 4
    fun at(k: Int) = track[a + k] + (track[b + k] - track[a + k]) * f
    return Sample(at(0), at(1), at(2), at(3))
}

// Colours: AnimatedPageBackground.css, `.blob-*` and their `.tw-dark` overrides.
private val PURPLE_LIGHT = Color(0xFF9333EA)
private val PURPLE_DARK = Color(0xFFA855F7)
private val GREEN_LIGHT = Color(0xFF16A34A)
private val GREEN_DARK = Color(0xFF22C55E)
private val BLUE_LIGHT = Color(0xFF2563EB)
private val BLUE_DARK = Color(0xFF3B82F6)

/** `width: 140vmin` -- vmin being the shorter side of the container. */
private const val BLOB_VMIN = 1.40f

/** `opacity: 0.12; mix-blend-mode: multiply` and its dark counterpart, both measured in the CSS. */
private const val ALPHA_LIGHT = 0.12f
private const val ALPHA_DARK = 0.165f

private const val PURPLE_PERIOD_MS = 25_000
private const val GREEN_PERIOD_MS = 30_000
private const val BLUE_PERIOD_MS = 35_000

// @keyframes move-purple-fy / move-green-fy / move-blue-fy: translate x, translate y, scale x,
// scale y at 0%, 33%, 66%, 100%. Translations are a fraction of the blob's own size, as `translate`
// with percentages is in CSS.
private val PURPLE_TRACK = floatArrayOf(
    0f, 0f, 1f, 1f,
    0.20f, -0.15f, 1.3f, 0.8f,
    0.05f, -0.30f, 0.8f, 1.2f,
    0f, 0f, 1f, 1f
)
private val GREEN_TRACK = floatArrayOf(
    0f, 0f, 1f, 1f,
    -0.15f, 0.20f, 0.9f, 1.4f,
    -0.30f, 0.05f, 1.4f, 0.9f,
    0f, 0f, 1f, 1f
)
private val BLUE_TRACK = floatArrayOf(
    0f, 0f, 1f, 1f,
    -0.20f, -0.15f, 1.5f, 0.7f,
    -0.05f, 0.30f, 0.7f, 1.3f,
    0f, 0f, 1f, 1f
)
