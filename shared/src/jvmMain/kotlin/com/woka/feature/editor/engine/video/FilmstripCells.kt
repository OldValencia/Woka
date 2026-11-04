package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.video.engine.preview.SeekPreviewGrid
import com.woka.feature.viewer.video.engine.preview.SeekPreviewSource
import java.io.File
import kotlin.math.abs
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The filmstrip's stills — the seek preview's own cells, laid along the strip (ME-211).
 *
 * **Nothing new decodes** (`timeline.md` §3.4). A cell is `SeekPreviewSource`'s 240 px
 * keyframes-only still at `SeekPreviewGrid`'s midpoint, the exact pair the viewer's hover
 * preview uses, so a strip and a hover over the same file ask for the same frames.
 *
 * **One thread, lowest priority**, the seek preview's rule: the picture gets its quanta
 * first, and a strip that fills in over a second is better than a canvas that stutters
 * while it does. A cell is asked for at most once; what has landed is published through a
 * snapshot map so a tile recomposes when its own still arrives and not before.
 *
 * The grabber is released **on the decode thread**, behind any decode still running
 * there — releasing a grabber under a live `grabImage()` is a native crash, which is the
 * lesson `RememberSeekPreview` already records.
 */
@Stable
internal class FilmstripCells(file: File, private val durationMs: Long, codedSize: IntSize) {

    private val grid = SeekPreviewGrid(durationMs)
    private val source = SeekPreviewSource(file, true, codedSize.width, codedSize.height)
    /**
     * **Coarse first** (2026-11-04): every fourth cell is decoded before the ones between, so
     * the whole strip shows pictures early and the gaps shimmer until they land. One thread
     * still; a priority queue in front of it, ordered by pass and then by request.
     */
    private val executor =
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, PriorityBlockingQueue(), ::filmstripThread)
    private val requested = AtomicLong()
    private val stills = mutableStateMapOf<Int, ImageBitmap>()
    private val asked = HashSet<Int>()
    private val empty = mutableStateMapOf<Int, Boolean>()

    /**
     * The still covering source [sourceMs], or null until it has been decoded.
     *
     * **A cell that decodes to nothing borrows the nearest earlier one** (measured
     * 2026-11-02, reported by the author as a strip ending a second early). A
     * keyframes-only grab past the file's last keyframe returns null — on a 26 s film
     * with a keyframe a second, the cell at 25.5 s — and the last keyframe *is* the honest
     * picture for that stretch, since nothing later can be shown without a full decode.
     */
    fun stillAt(sourceMs: Long, spanMs: Long = 0L): ImageBitmap? {
        if (!grid.isUsable) return null
        var cell = grid.cellOf(sourceMs)
        while (cell > 0 && empty[cell] == true) cell--
        stills[cell]?.let { return it }
        nearby(cell, sourceMs, spanMs)?.let { return it }
        if (asked.add(cell)) {
            executor.execute(CellTask(passOf(cell), requested.getAndIncrement()) {
                val still = source.frameAt(grid.midpointOf(cell))
                if (still != null) stills[cell] = still else empty[cell] = true
            })
        }
        return null
    }

    /**
     * A still already decoded **inside the slot asking** — [spanMs] of source around
     * [sourceMs] — nearest first, so the slot is not decoded a second time (the author,
     * 2026-11-04: the resting strip has just decoded the frames Trim's wider slots want, a
     * cell or two away). Null when nothing in the slot has landed, and the cell is asked for.
     */
    private fun nearby(cell: Int, sourceMs: Long, spanMs: Long): ImageBitmap? {
        if (spanMs <= 0L) return null
        val from = grid.cellOf(sourceMs - spanMs / 2)
        val to = grid.cellOf(sourceMs + spanMs / 2)
        return (from..to).sortedBy { abs(it - cell) }.firstNotNullOfOrNull { stills[it] }
    }

    /**
     * Which pass decodes [cell]: the coarse one, the fill, or **last of all** for the film's
     * final cell. Past the last keyframe a keyframes-only grab reads to the end of the file —
     * measured 2026-11-04 at 10.5 s on a 7 GB `mkv`, for nothing — and with one thread every
     * cell queued behind it waited those ten seconds too.
     */
    private fun passOf(cell: Int): Int = when {
        cell >= lastCell -> 2
        cell % COARSE_STEP == 0 -> 0
        else -> 1
    }

    private val lastCell = grid.cellOf(durationMs)

    /**
     * Cells not yet started are dropped, then the source closes behind the one running —
     * still on the decode thread, and as a [CellTask] because the queue orders nothing else.
     */
    fun close() {
        executor.queue.clear()
        executor.execute(CellTask(-1, 0L) { source.close() })
        executor.shutdown()
    }
}

/** One set of cells per file and length, closed when the band leaves the screen. */
@Composable
internal fun rememberFilmstrip(file: File?, durationMs: Long, codedSize: IntSize): FilmstripCells? {
    if (file == null || durationMs <= 0L || codedSize == IntSize.Zero) return null
    val cells = remember(file, durationMs, codedSize) { FilmstripCells(file, durationMs, codedSize) }
    DisposableEffect(cells) { onDispose { cells.close() } }
    return cells
}

/** One cell's decode, ordered coarse pass first, then by when it was asked for. */
private class CellTask(val pass: Int, val order: Long, val work: () -> Unit) : Runnable, Comparable<CellTask> {
    override fun run() = work()
    override fun compareTo(other: CellTask): Int = compareValuesBy(this, other, { it.pass }, { it.order })
}

/** The coarse pass: one cell in this many is decoded before any of the rest. */
private const val COARSE_STEP = 4

private fun filmstripThread(runnable: Runnable): Thread =
    Thread(runnable, "woka-filmstrip").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }
