package com.woka.feature.editor.shell

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter

/**
 * Blur, then push the blurred alpha back through a steep ramp — the whole of the "gooey"
 * effect, in the two steps the CSS examples use (`gooey examples`, 2026-08-29).
 *
 * ```
 * feGaussianBlur stdDeviation="10"
 * feColorMatrix  values="… 0 0 0 50 -20"
 * ```
 *
 * The blur spreads each shape's alpha into a soft halo; the ramp — `gain × a + bias`,
 * clamped — throws away everything below `bias / gain` and saturates everything above it.
 * Two shapes close enough for their halos to overlap therefore come back out joined, and
 * two that are not come back out as themselves. Nothing is interpolated and nothing is
 * animated here: the *shapes* move, and the neck between them is a consequence.
 *
 * **Skia's own filters rather than Compose's `BlurEffect`.** Compose can blur, and cannot
 * put a colour matrix after the blur — which is the half that does the work. `skiko` is
 * already on the classpath for every frame this app draws.
 *
 * The bias is expressed in the 0..1 alpha the matrix operates in, exactly as `feColorMatrix`
 * does; a value of `gain / 2.2` puts the cut a little under half-opacity, which is where two
 * halos meeting at their midpoint sit.
 */
internal fun gooEffect(blurRadius: Float): RenderEffect =
    ImageFilter.makeColorFilter(
        ColorFilter.makeMatrix(ColorMatrix(RAMP)),
        // `DECAL`, so the edges of the layer fade to nothing rather than smearing the
        // outermost pixels outward — a clamped blur would grow a rectangular halo around
        // the whole pair.
        ImageFilter.makeBlur(blurRadius, blurRadius, FilterTileMode.DECAL),
        null
    ).asComposeRenderEffect()

/** Identity on colour, [ALPHA_GAIN] and −[ALPHA_BIAS] on alpha. */
private val RAMP = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, ALPHA_GAIN, -ALPHA_BIAS
)

/** Steep enough that the ramp is effectively a threshold with one soft pixel on it. */
private const val ALPHA_GAIN = 22f

private const val ALPHA_BIAS = 10f
