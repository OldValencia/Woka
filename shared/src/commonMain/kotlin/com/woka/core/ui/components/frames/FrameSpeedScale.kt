package com.woka.core.ui.components.frames

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * An animation's speed track (RD-501, the author 2026-11-27): 0.25–5×, read in octaves, with
 * 1× at the centre and the clip speed row's detents (`snapClipSpeed`) plus 4× and 5×.
 *
 * **Two logarithmic halves**, 0.25–1× on the left and 1–5× on the right: a single log scale over
 * 0.25–5× puts its centre at 1.12×, and the normal speed belongs where the knob rests.
 * The film's own slider stays 0.5–2× (`PlaybackSpeed`); this one is only for looking.
 */
object FrameSpeedScale {
    const val MIN = 0.25f
    const val MAX = 5f
    val DETENTS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f)

    /** Within this many octaves of a detent the speed lands on it. */
    private const val PULL = 0.06f

    /** Where [speed] sits on the track, 0 to 1. */
    fun fractionOf(speed: Float): Float {
        val s = speed.coerceIn(MIN, MAX)
        return if (s <= 1f) 0.5f * (log2(s) - log2(MIN)) / -log2(MIN)
        else 0.5f + 0.5f * log2(s) / log2(MAX)
    }

    /** The speed at [fraction] of the track, snapped: onto a detent nearby, else to 0.05. */
    fun speedAt(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        val raw = if (f <= 0.5f) MIN * (1f / MIN).pow(f * 2f) else MAX.pow(f * 2f - 1f)
        DETENTS.firstOrNull { abs(log2(it) - log2(raw)) < PULL }?.let { return it }
        return ((raw * 20f).roundToInt() / 20f).coerceIn(MIN, MAX)
    }
}
