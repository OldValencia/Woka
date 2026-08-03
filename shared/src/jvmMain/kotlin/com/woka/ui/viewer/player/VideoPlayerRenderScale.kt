package com.woka.ui.viewer.player

import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.math.abs

// How far the wanted decode width must sit from the applied one before a re-open
// is worth its spinner.
private const val RESCALE_RATIO = 0.25f

// How long the surface must hold still before acting, so dragging a window edge
// does not queue a re-open per frame of the drag.
private const val SETTLE_MS = 700L

/**
 * Decode size policy (§6): produce frames at the size actually shown rather than
 * the stream's own.
 *
 * The RGBA conversion is per-pixel and measured at ~31ms a frame on 4K against
 * ~10ms for the decode itself, so it — not the codec — is what caps playback. A
 * 4K stream drawn into a 1200px-wide window has three quarters of those pixels
 * thrown away at draw time; asking swscale for the smaller size skips the work.
 *
 * It never upscales: when the surface is larger than the stream the grabber keeps
 * its native output, so this can only remove work, never add it. The cost is that
 * zoom past 100% has no extra detail to draw on.
 */
internal class RenderScale {
    @Volatile
    var nativeWidth = 0

    @Volatile
    var nativeHeight = 0

    // Applied to the running grabber. 0 means it is producing native frames.
    @Volatile
    var decodeWidth = 0
        private set

    @Volatile
    private var decodeHeight = 0

    @Volatile
    private var surfaceWidth = 0

    @Volatile
    private var surfaceHeight = 0

    @Volatile
    private var changedAtMs = 0L

    init {
        // Seeded from the last viewer so the second file onwards opens already at
        // the right size; only the first one in a session pays for a re-open.
        surfaceWidth = lastSurfaceWidth
        surfaceHeight = lastSurfaceHeight
    }

    /** Reports the drawing surface in pixels; called from the UI on every layout. */
    fun setSurface(width: Int, height: Int) {
        if (width == surfaceWidth && height == surfaceHeight) return
        surfaceWidth = width
        surfaceHeight = height
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        changedAtMs = System.currentTimeMillis()
    }

    /** Decode width for the current surface, or 0 to leave the native output alone. */
    fun wantedWidth(): Int {
        if (nativeWidth <= 0 || nativeHeight <= 0) return 0
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return 0
        val fit = minOf(
            surfaceWidth.toFloat() / nativeWidth,
            surfaceHeight.toFloat() / nativeHeight
        )
        if (fit >= 1f) return 0
        return even((nativeWidth * fit).toInt())
    }

    /** True when the grabber's output size is stale and the surface has settled. */
    fun needsReopen(): Boolean {
        val wanted = wantedWidth()
        if (wanted == decodeWidth) return false
        if (System.currentTimeMillis() - changedAtMs < SETTLE_MS) return false
        if (wanted == 0 || decodeWidth == 0) return true
        return abs(wanted - decodeWidth).toFloat() / decodeWidth > RESCALE_RATIO
    }

    /** Fixes the current surface as [grabber]'s output size, before it is started. */
    fun applyTo(grabber: FFmpegFrameGrabber) {
        val wanted = wantedWidth()
        decodeWidth = wanted
        decodeHeight = if (wanted == 0) 0 else even(nativeHeight * wanted / nativeWidth)
        if (wanted == 0) return
        grabber.imageWidth = decodeWidth
        grabber.imageHeight = decodeHeight
    }

    /** Takes the stream's own dimensions from the track being decoded. */
    fun useNativeOf(track: MediaTrack?) {
        nativeWidth = track?.width ?: 0
        nativeHeight = track?.height ?: 0
    }

    private fun even(value: Int): Int = (value - value % 2).coerceAtLeast(2)

    private companion object {
        @Volatile
        private var lastSurfaceWidth = 0

        @Volatile
        private var lastSurfaceHeight = 0
    }
}

/**
 * Queues the in-place re-open that changes the grabber's output size, if the
 * surface has settled somewhere the current size no longer suits. Output size is
 * fixed before `start()`, so this costs the same swap as a track change — spinner
 * included. Returns true when one was queued.
 */
internal fun VideoPlayerState.requestRescaleIfNeeded(fallbackVideoStream: Int): Boolean {
    if (!renderScale.needsReopen()) return false
    // The first adjustment lands before anything is on screen — the probe only
    // reveals the native size once the grabber is up — so it needs no spinner.
    isReloadingVideo = currentPositionMs > 0
    pendingVideoReopenIndex = tracks.selectedVideoIndex ?: fallbackVideoStream
    return true
}
