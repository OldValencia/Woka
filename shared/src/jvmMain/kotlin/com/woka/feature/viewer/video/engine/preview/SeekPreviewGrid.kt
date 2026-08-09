package com.woka.feature.viewer.video.engine.preview

/** Cells a film is cut into, before the clamps below have their say. */
private const val TARGET_CELLS = 300

/** A ten-minute clip must not get 300 cells two seconds apart. */
private const val MIN_STEP_MS = 5_000L

/** A four-hour film must not get one frame every two minutes. */
private const val MAX_STEP_MS = 30_000L

/**
 * Quantises the timeline into the cells the coarse preview tier decodes.
 *
 * A fixed step behaves badly at both ends — 10s over a four-hour film is 1440 cells
 * and 187MB of stills, while 30s over a ten-minute clip is twenty frames for the
 * whole bar — so the step is derived from the duration and clamped. In practice
 * that lands at 5s for a short clip, 12s for an hour and 26s for a two-hour film.
 */
internal class SeekPreviewGrid(private val durationMs: Long) {

    /** False for a file whose duration is not known yet; nothing is decoded then. */
    val isUsable: Boolean get() = durationMs > 0L

    val stepMs: Long =
        if (durationMs <= 0L) MAX_STEP_MS
        else (durationMs / TARGET_CELLS).coerceIn(MIN_STEP_MS, MAX_STEP_MS)

    fun cellOf(positionMs: Long): Int = (clamp(positionMs) / stepMs).toInt()

    /**
     * What actually gets decoded for [cell]. The midpoint, not the raw pointer
     * position: sliding within one cell must not produce two different stills, and
     * the middle is the least wrong single answer for the whole span. The last cell
     * is short, so its end is the duration rather than a full step.
     */
    fun midpointOf(cell: Int): Long {
        val start = cell.toLong() * stepMs
        val end = minOf(start + stepMs, durationMs)
        return clamp((start + end) / 2)
    }

    private fun clamp(ms: Long): Long = ms.coerceIn(0L, (durationMs - 1).coerceAtLeast(0L))
}
