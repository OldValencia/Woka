package com.woka.feature.editor.shell

import com.woka.feature.editor.core.FloatParam
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * Where a value sits along a scrub row, and the value at a point of it (RD-302) — linear, or
 * in octaves for a ratio (`FloatParam.octaves`), where ½× and 2× lie as far from 1×.
 *
 * Pure, so the clip speed's centred 1× is asserted without a window (`ScrubScaleTest`).
 */

/** [value]'s share of the track, 0 at the range's start and 1 at its end. */
internal fun trackFraction(spec: FloatParam, value: Float): Float {
    val (from, to) = ends(spec)
    val at = position(spec, value.coerceIn(spec.range))
    return if (to == from) 0f else ((at - from) / (to - from)).coerceIn(0f, 1f)
}

/** The value at [fraction] of the track. */
internal fun valueAtFraction(spec: FloatParam, fraction: Float): Float {
    val (from, to) = ends(spec)
    val at = from + fraction.coerceIn(0f, 1f) * (to - from)
    val value = if (spec.octaves) 2f.pow(at) else at
    return value.coerceIn(spec.range)
}

/** How close, as a share of the track, a value must come to its default to stick there. */
private const val SNAP_SHARE = 0.015f

/** The default the prototype's detent holds: a value passing near it lands exactly on it. */
internal fun snapToDetent(spec: FloatParam, value: Float): Float =
    if (abs(trackFraction(spec, value) - trackFraction(spec, spec.detent)) <= SNAP_SHARE) spec.detent else value

private fun ends(spec: FloatParam): Pair<Float, Float> =
    position(spec, spec.range.start) to position(spec, spec.range.endInclusive)

private fun position(spec: FloatParam, value: Float): Float = if (spec.octaves) log2(value) else value
