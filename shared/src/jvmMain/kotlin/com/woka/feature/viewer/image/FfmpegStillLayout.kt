package com.woka.feature.viewer.image

import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * FFmpeg refuses a container declaring more streams than this and returns
 * `AVERROR(ENOMEM)` — the bare `-12` a tiled HEIC used to fail the viewer with.
 * The default is 1000 and a real 6016×6016 macOS wallpaper declares **1296**;
 * one 264 MB file in the same folder declares **5760**. The guard exists so a
 * malicious file cannot exhaust memory, so it is raised rather than removed.
 */
internal const val MAX_STREAMS_OPTION = "65536"

/**
 * One picture inside a container — reached one of two entirely different ways.
 *
 * [size] is carried rather than looked up later so that a caller asking only
 * "how big is this?" — which the viewer does on every file change, to keep the
 * zoom presets honest while a preview is on screen — costs nothing beyond the
 * layout read it already needed.
 */
internal sealed interface FfmpegPicture {

    val size: IntSize

    /** A frame of a stream: a burst item, or frame *n* of an animation. */
    data class Frame(
        val stream: Int,
        val frameInStream: Int,
        override val size: IntSize
    ) : FfmpegPicture

    /** A grid of separately coded tiles, assembled by [assembleTileGrid]. */
    data class TileGrid(val group: Int, override val size: IntSize) : FfmpegPicture
}

/**
 * What one still container holds, read from the demuxer without decoding.
 *
 * [frameDurationMs] is non-null only in the animation case.
 */
internal data class FfmpegStillLayout(
    val pictures: List<FfmpegPicture>,
    val frameDurationMs: Int?
)

/**
 * Reads the stream table. **Three container shapes, and telling them apart is
 * the whole job** — each was measured, and each broke a model that looked
 * obviously right before it:
 *
 * - a **burst** is N streams of one frame each (`burst.heic`: 5);
 * - an **animation** is a one-frame cover stream *plus* a multi-frame stream
 *   (`anim.avif`: streams of 1 and 8 frames, both tagged `default`, so only the
 *   frame count separates them);
 * - a **tile grid** is hundreds of `AV_DISPOSITION_DEPENDENT` 512×512 tiles that
 *   are not pictures individually. Four real-world HEICs measured, every one of
 *   them tiled and **none** carrying a single independent stream.
 *
 * Grids come first in [FfmpegStillLayout.pictures] because they are the file's
 * real content; any independent streams beside them are thumbnails or
 * auxiliary items. Nothing measured has both, so the order is a judgement, not
 * a finding.
 *
 * Raw `avformat` rather than `FFmpegFrameGrabber`: nothing here decodes, and
 * the grabber's `start()` opens a decoder and probes for one. On a 1296-stream
 * Catalina that difference was ~430 ms against ~260 ms, paid on every open.
 */
internal fun readStillLayout(file: File): FfmpegStillLayout = cachedLayout(file) {
    onFormatContext(file) { format ->
        val grids = tileGridGroups(format).map { group ->
            val grid = readTileGrid(format, group)
            FfmpegPicture.TileGrid(group, IntSize(grid.width, grid.height))
        }
        val independent = independentVideoStreams(format)
        val animated = independent.firstOrNull { frameCount(format, it) > 1 }
        if (animated == null) {
            FfmpegStillLayout(
                pictures = grids + independent.map {
                    FfmpegPicture.Frame(it, 0, streamSize(format, it))
                },
                frameDurationMs = null
            )
        } else {
            val size = streamSize(format, animated)
            val frames = (0 until frameCount(format, animated))
                .map { FfmpegPicture.Frame(animated, it, size) }
            FfmpegStillLayout(
                pictures = grids + frames,
                frameDurationMs = frameDurationMs(format, animated)
            )
        }
    }
}
