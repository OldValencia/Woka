package com.woka.ui.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.data.ScalePreset
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The zoom scale, expressed as a percentage of the media's **native 1:1 pixels** —
 * the base every other viewer uses (macOS Preview, Photoshop, browsers). Fit, Fill
 * and Actual are not a separate mode: each of them is a specific percentage on this
 * same scale, which is what makes them snap points instead of a mode switch.
 */
const val ZOOM_STEP_RATIO = 1.1f

/** Relative tolerance within which a percentage counts as "sitting on" a preset. */
private const val SNAP_TOLERANCE = 0.025f

/** Percentage at which the media exactly fits inside [container] (letterboxed). */
fun fitPercent(container: IntSize, native: IntSize): Int {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return 100
    val scale = min(
        container.width.toFloat() / native.width,
        container.height.toFloat() / native.height
    )
    return (scale * 100f).roundToInt().coerceAtLeast(1)
}

/** Percentage at which the media covers [container] (overflow cropped). */
fun fillPercent(container: IntSize, native: IntSize): Int {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return 100
    val scale = max(
        container.width.toFloat() / native.width,
        container.height.toFloat() / native.height
    )
    return (scale * 100f).roundToInt().coerceAtLeast(1)
}

/** The percentage a preset currently resolves to. Actual is panel-independent. */
fun presetPercent(preset: ScalePreset, container: IntSize, native: IntSize): Int = when (preset) {
    ScalePreset.Fit -> fitPercent(container, native)
    ScalePreset.Fill -> fillPercent(container, native)
    ScalePreset.Original -> 100
}

/** The percentage [mode] currently resolves to, preset or free zoom alike. */
fun SizeMode.percentIn(container: IntSize, native: IntSize): Int = when (this) {
    is SizeMode.Zoom -> percent
    is SizeMode.Preset -> presetPercent(preset, container, native)
}

/**
 * Reachable zoom range: an order of magnitude either side of the fitted size, and
 * always wide enough to contain Fit, Fill and Actual — a snap point outside the
 * bounds would be unreachable by slider or wheel.
 */
fun zoomBounds(container: IntSize, native: IntSize): IntRange {
    val fit = fitPercent(container, native)
    val fill = fillPercent(container, native)
    var lo = max(1, fit / 10)
    var hi = min(6400, max(fit * 10, 800))
    lo = min(lo, min(100, min(fit, fill)))
    hi = max(hi, max(100, max(fit, fill)))
    return lo..hi
}

fun clampPercent(percent: Int, bounds: IntRange): Int = percent.coerceIn(bounds.first, bounds.last)

/**
 * Geometric step: at 40% one tick is 4 points, at 400% it is 40. A fixed step in
 * percentage points feels wildly different at the two ends of the scale.
 */
fun stepUp(percent: Int, bounds: IntRange): Int =
    clampPercent(max(percent + 1, (percent * ZOOM_STEP_RATIO).roundToInt()), bounds)

fun stepDown(percent: Int, bounds: IntRange): Int =
    clampPercent(min(percent - 1, (percent / ZOOM_STEP_RATIO).roundToInt()), bounds)

/** The preset [percent] currently sits on, or null when it sits between them. */
fun snapToPreset(percent: Int, container: IntSize, native: IntSize): ScalePreset? {
    val order = listOf(ScalePreset.Original, ScalePreset.Fit, ScalePreset.Fill)
    return order.firstOrNull { preset ->
        val target = presetPercent(preset, container, native)
        abs(percent - target) <= max(1f, target * SNAP_TOLERANCE)
    }
}

/** Resolves a raw percentage to a snapped preset when it lands on one. */
fun sizeModeFor(percent: Int, container: IntSize, native: IntSize): SizeMode {
    val preset = snapToPreset(percent, container, native)
    return if (preset != null) SizeMode.Preset(preset) else SizeMode.Zoom(percent)
}

/** Log mapping: a linear track would spend a tenth of its travel below 100%. */
fun sliderFraction(percent: Int, bounds: IntRange): Float {
    val lo = bounds.first.toFloat()
    val hi = bounds.last.toFloat()
    if (hi <= lo) return 0f
    return (ln(percent / lo) / ln(hi / lo)).coerceIn(0f, 1f)
}

fun percentFromFraction(fraction: Float, bounds: IntRange): Int {
    val lo = bounds.first.toFloat()
    val hi = bounds.last.toFloat()
    if (hi <= lo) return bounds.first
    val raw = lo * exp(fraction.coerceIn(0f, 1f) * ln(hi / lo))
    return clampPercent(raw.roundToInt(), bounds)
}

/**
 * Keeps the content point under [anchor] in place while the scale changes from
 * [fromScale] to [toScale] (both graphics-layer scales), returning the new offset.
 */
fun zoomAround(
    anchor: Offset,
    container: IntSize,
    fromScale: Float,
    toScale: Float,
    offsetX: Float,
    offsetY: Float
): Pair<Float, Float> {
    val ratio = if (fromScale != 0f) toScale / fromScale else 1f
    val centerX = container.width / 2f
    val centerY = container.height / 2f
    return Pair(
        (anchor.x - centerX) * (1f - ratio) + ratio * offsetX,
        (anchor.y - centerY) * (1f - ratio) + ratio * offsetY
    )
}
