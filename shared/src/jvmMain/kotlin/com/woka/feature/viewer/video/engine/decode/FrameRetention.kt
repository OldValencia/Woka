package com.woka.feature.viewer.video.engine.decode

import org.jetbrains.skia.Bitmap

/**
 * How far behind the newest *drawn* frame a bitmap must fall before it is closed.
 *
 * It covers the one case where two scenes can be drawing different frames at the
 * same moment — the handful of frames while the fullscreen window takes over from
 * the main one, which closes itself immediately afterwards
 * (`MediaViewerScreen.kt:124`). Nothing else needs covering: a scene that stalls
 * or stops drawing stops moving the mark with it.
 */
private const val CLOSE_MARGIN_FRAMES = 16

/**
 * Past this many tracked frames the oldest reference is dropped rather than
 * closed. Nothing is drawing in that case — a minimised window, a scene not yet
 * composed — and holding strong references would stop the collector from doing
 * what it did before this class existed. Dropping hands the frame back to the GC
 * path, so the worst case here is exactly the old behaviour and never worse.
 */
private const val MAX_TRACKED = 64

/**
 * Frees a decoded frame's pixels the moment nothing can draw them any more.
 *
 * A frame is ~15MB of native memory behind a Java wrapper of a few dozen bytes, so
 * the collector sees no pressure and will not free it for tens of seconds —
 * measured at seven gigabytes accumulated between collections. Closing the bitmap
 * is instant instead. The catch is that closing one still on screen is a null
 * dereference inside Skia rather than an exception, so the rule has to be one that
 * cannot be wrong.
 *
 * The rule: **a bitmap is closed only once a strictly newer one has been drawn**,
 * by [CLOSE_MARGIN_FRAMES] frames. Every site that draws the picture reports what
 * it drew via [acknowledge]; the decoder sweeps as it goes past in [register].
 *
 * The failure mode is deliberately one-sided. If the UI stalls, or a new draw site
 * is added and forgets to report, the mark stops advancing and nothing is closed —
 * memory grows, and the GC path still in place behind this catches it. There is no
 * arrangement of stalls or missed reports that frees a frame early.
 */
internal class FrameRetention {

    /** Written from draw scopes, read by the decoder thread. */
    @Volatile
    private var lastDrawnId = 0L

    /** Touched from the decoder thread only. The UI writes [lastDrawnId], nothing else. */
    private val live = ArrayDeque<TrackedFrame>()

    private class TrackedFrame(val id: Long, val bitmap: Bitmap)

    /**
     * Reports the frame a draw scope has just finished drawing. Racing writers are
     * harmless: both are publishing a high-water mark, and a lost update only means
     * one frame is closed a frame later than it could have been.
     */
    fun acknowledge(frameId: Long) {
        if (frameId > lastDrawnId) lastDrawnId = frameId
    }

    /** Takes ownership of a freshly produced bitmap, then frees what is now unreachable. */
    fun register(frameId: Long, bitmap: Bitmap) {
        live.addLast(TrackedFrame(frameId, bitmap))
        val closeBelow = lastDrawnId - CLOSE_MARGIN_FRAMES
        while (live.isNotEmpty() && live.first().id <= closeBelow) {
            live.removeFirst().bitmap.close()
        }
        while (live.size > MAX_TRACKED) {
            live.removeFirst()
        }
    }
}
