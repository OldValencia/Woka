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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
internal class FilmstripCells(file: File, durationMs: Long, codedSize: IntSize) {

    private val grid = SeekPreviewGrid(durationMs)
    private val source = SeekPreviewSource(file, true, codedSize.width, codedSize.height)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(::filmstripThread)
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
    fun stillAt(sourceMs: Long): ImageBitmap? {
        if (!grid.isUsable) return null
        var cell = grid.cellOf(sourceMs)
        while (cell > 0 && empty[cell] == true) cell--
        stills[cell]?.let { return it }
        if (asked.add(cell)) {
            executor.execute {
                val still = source.frameAt(grid.midpointOf(cell))
                if (still != null) stills[cell] = still else empty[cell] = true
            }
        }
        return null
    }

    fun close() {
        executor.execute { source.close() }
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

private fun filmstripThread(runnable: Runnable): Thread =
    Thread(runnable, "woka-filmstrip").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }
