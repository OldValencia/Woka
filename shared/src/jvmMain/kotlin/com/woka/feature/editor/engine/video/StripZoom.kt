package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow

/** The closest the strip goes: five seconds of film across its whole width. */
private const val TIGHTEST_MS = 5_000f

/** One wheel notch zooms by this much — in, or out by its inverse. */
internal const val ZOOM_NOTCH = 1.25f

/** A layout of the strip at a scale (null: the whole film) and a scroll. */
internal typealias StripLayout = (Float?, Float) -> StripGeometry

/**
 * How far the strip is zoomed and where it is scrolled (ME-218). Reported by the author
 * 2026-11-04: on an 81-minute film a two-second freeze is under a pixel wide — it can be
 * neither selected nor dragged, and its edges sit on its neighbours'.
 *
 * Held by the band rather than the strip, so the zoom pill beside the strip reads the same
 * value. [scale] is pixels per millisecond, or null for the whole film in the strip's width —
 * **absolute, not a multiple of the fit**, so a clip growing under a drag does not rescale
 * every other clip with it (reported the same day: the strip jumped on release).
 */
@Stable
internal class StripZoom {

    var scale by mutableStateOf<Float?>(null)
        private set
    var scrollPx by mutableFloatStateOf(0f)
        private set

    val zoomed: Boolean get() = scale != null

    /** How many times the fit the strip is — the pill's number. */
    val zoom: Float
        get() {
            val fit = layoutNow?.invoke(null, 0f)?.fitPxPerMs?.takeIf { it > 0f } ?: return 1f
            return (scale ?: fit) / fit
        }

    /** Zooms by [factor] around [x], keeping the moment under it where it was. */
    fun zoomAround(x: Float, factor: Float, layout: StripLayout) {
        val before = layout(scale, scrollPx)
        val at = before.timelineAt(x)
        val fit = before.fitPxPerMs
        val next = (before.pxPerMs * factor).coerceIn(fit, maxOf(fit, before.viewPx / TIGHTEST_MS))
        scale = if (next <= fit * 1.001f) null else next
        val after = layout(scale, 0f)
        scrollPx = clamp(after.xOf(at) - x, after)
    }

    /** Moves the window by [dx] pixels of content. */
    fun pan(dx: Float, layout: StripLayout) {
        scrollPx = clamp(scrollPx + dx, layout(scale, 0f))
    }

    /** Keeps [x], the play head on screen, inside the strip while it plays. */
    fun follow(x: Float, layout: StripLayout) {
        val view = layout(scale, 0f).viewPx
        if (x in view * 0.05f..view * 0.92f) return
        pan(x - view * 0.1f, layout)
    }

    /**
     * The strip as it is laid out now, and where its play head is — written by the strip each
     * composition, read by what zooms or pans it from outside: the overview and the band.
     */
    var layoutNow: StripLayout? = null
    var playHeadMsNow: () -> Long = { 0L }

    /** The wheel anywhere on the band but the strip: zoom around the play head (2026-11-04). */
    fun wheelAtPlayHead(notches: Float) {
        val layout = layoutNow ?: return
        zoomAround(layout(scale, scrollPx).xOf(playHeadMsNow()), ZOOM_NOTCH.pow(-notches), layout)
    }

    /** The overview: a press centres the window on [x] of the strip's width. */
    fun centreOn(x: Float) {
        val whole = layoutNow?.invoke(scale, 0f) ?: return
        if (whole.viewPx <= 0f) return
        scrollPx = clamp(x / whole.viewPx * whole.contentPx - whole.viewPx / 2f, whole)
    }

    /** The overview: a drag of [dx] there moves the window by the same share of the film. */
    fun panOverview(dx: Float) {
        val layout = layoutNow ?: return
        val whole = layout(scale, 0f)
        if (whole.viewPx <= 0f) return
        pan(dx * whole.contentPx / whole.viewPx, layout)
    }

    /** Back to the whole film — the pill's click. */
    fun fit() {
        scale = null
        scrollPx = 0f
    }

    /** Re-clamps after the film's length changed under a fixed scale — a hold, a delete. */
    fun settle(layout: StripLayout) {
        scrollPx = clamp(scrollPx, layout(scale, 0f))
    }

    private fun clamp(scroll: Float, unscrolled: StripGeometry): Float =
        scroll.coerceIn(0f, (unscrolled.contentPx - unscrolled.viewPx).coerceAtLeast(0f))
}
