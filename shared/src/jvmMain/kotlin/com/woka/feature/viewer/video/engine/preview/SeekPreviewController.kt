package com.woka.feature.viewer.video.engine.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds

/** How long the pointer must hold still before the exact frame is worth decoding. */
private const val REFINE_DEBOUNCE_MS = 120L

/** Exact requests are cached at this granularity; finer would never hit. */
private const val REFINE_BUCKET_MS = 250L

/**
 * Drives the seek bar's preview: a coarse grid that carries a moving pointer, and an
 * exact frame that refines it once the pointer settles.
 *
 * **The coarse tier has no debounce**, and that is the whole point. A debounce is a
 * delay, and a pointer sweeping the bar to find a scene wants an answer now. What
 * makes it cheap instead is quantisation: a cached cell renders with no latency at
 * all, and a miss queues exactly one decode. The rate limiter is the decode itself,
 * about five a second, not a clock.
 *
 * Only the refine is debounced, and nothing waits on it — the timestamp is on screen
 * from the first frame either way.
 *
 * Both tiers run on one thread and every decode is gated on [canDecode], so previews
 * can never outrank playback.
 */
internal class SeekPreviewController(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val grid: SeekPreviewGrid,
    private val coarse: SeekPreviewSource,
    private val exact: SeekPreviewSource,
    private val canDecode: () -> Boolean
) {
    /** The still to draw right now, or null before the first one lands. */
    var shown by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * True once anything has been shown. The pill reserves its frame box from then
     * on, so it grows once and never jumps again while the pointer moves.
     */
    var hasShown by mutableStateOf(false)
        private set

    private val cache = SeekPreviewCache()

    // Conflated: a newer request replaces the pending one instead of queueing behind
    // it, so a fast sweep skips the cells it flew over rather than decoding them all.
    private val coarseRequests = Channel<Int>(Channel.CONFLATED)
    private val refineRequests = MutableStateFlow<Long?>(null)

    @Volatile
    private var wantedMs: Long? = null

    @Volatile
    private var direction = 0

    private var lastCell: Int? = null

    init {
        scope.launch(dispatcher) { runCoarse() }
        scope.launch(dispatcher) { runRefine() }
    }

    /** The hovered or dragged position; null once the pointer leaves the bar. */
    fun request(positionMs: Long?) {
        wantedMs = positionMs
        refineRequests.value = positionMs
        if (positionMs == null || !grid.isUsable) return
        val cell = grid.cellOf(positionMs)
        lastCell?.let { direction = (cell - it).sign }
        lastCell = cell
        val cached = cache.cell(cell)
        if (cached != null) publish(cached) else coarseRequests.trySend(cell)
    }

    private suspend fun runCoarse() {
        for (cell in coarseRequests) {
            val bitmap = decodeCell(cell) ?: continue
            // Only if the pointer is still here. Mid-sweep it usually is not, and
            // publishing a cell it has already left would fight the newer request.
            val caughtUp = wantedMs?.let { grid.cellOf(it) == cell } == true
            if (!caughtUp) continue
            publish(bitmap)
            // Prefetch the next cell along, but only now that we are level with the
            // pointer: if it had moved on there would be a real request waiting and
            // this would delay it.
            if (direction != 0) decodeCell(cell + direction)
        }
    }

    private fun decodeCell(cell: Int): ImageBitmap? {
        if (cell < 0) return null
        cache.cell(cell)?.let { return it }
        if (!canDecode()) return null
        val bitmap = coarse.frameAt(grid.midpointOf(cell)) ?: return null
        cache.putCell(cell, bitmap)
        return bitmap
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun runRefine() {
        refineRequests
            .debounce(REFINE_DEBOUNCE_MS.milliseconds)
            .mapLatest { positionMs -> refine(positionMs) }
            .collect {}
    }

    private fun refine(positionMs: Long?) {
        if (positionMs == null) return
        val key = positionMs / REFINE_BUCKET_MS
        cache.exact(key)?.let { return publishIfStill(key, it) }
        if (!canDecode()) return
        val bitmap = exact.frameAt(positionMs) ?: return
        cache.putExact(key, bitmap)
        publishIfStill(key, bitmap)
    }

    private fun publishIfStill(key: Long, bitmap: ImageBitmap) {
        if (wantedMs?.let { it / REFINE_BUCKET_MS } == key) publish(bitmap)
    }

    private fun publish(bitmap: ImageBitmap) {
        shown = bitmap
        hasShown = true
    }
}
