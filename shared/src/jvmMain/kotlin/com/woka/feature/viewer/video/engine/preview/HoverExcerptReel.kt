package com.woka.feature.viewer.video.engine.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.math.roundToInt

/** Five places in the film: the middles of its five fifths. */
private val POSITIONS = listOf(0.1, 0.3, 0.5, 0.7, 0.9)

/** One excerpt's length (SIDE-006, measured: see the ticket's table). */
private const val EXCERPT_SECONDS = 2.5

/** Wide enough for an L card at 125 % scaling; the decode cost barely moves with it. */
private const val OUT_WIDTH = 320

/** The seek preview's own budget: measured to keep 1.6× ahead of real time on a 2160p remux. */
private const val THREADS = "2"

/** One excerpt, decoded and ready to draw at [frameMs] apart. */
internal class Excerpt(
    val index: Int,
    val frames: List<ImageBitmap>,
    val frameMs: Long,
    val color: StillColorInfo
)

/**
 * The five excerpts a hovered video plays (SIDE-006), looping for as long as they
 * are collected.
 *
 * One grabber, opened the way `SeekPreviewSource` opens its own, and confined to one
 * thread at a time, because a grabber is not safe to touch from two. **The buffer of
 * one is the design** (2026-10-17, `HoverExcerptProbe`). A seek costs up to 0.8 s on a
 * 2160p file, where decoding the excerpt after it runs 1.6–2.7× faster than real
 * time. So the next excerpt is decoded while the current one draws, and only ever
 * one: a deeper buffer would hold seconds of 320-pixel frames for a card the pointer
 * may leave at any moment.
 *
 * Cancelling the collector closes the grabber on its own thread, in the `finally`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun hoverExcerpts(file: File): Flow<Excerpt> = flow {
    val grabber = openGrabber(file) ?: return@flow
    try {
        val fps = grabber.frameRate.takeIf { it.isFinite() && it > 1.0 } ?: 24.0
        val count = (EXCERPT_SECONDS * fps).roundToInt()
        val frameMs = (1000.0 / fps).roundToInt().toLong()
        val color = grabber.readStreamHdrInfo().let { StillColorInfo(it.transfer, it.isWideGamut) }
        val length = grabber.lengthInTime.coerceAtLeast(0L)
        while (true) {
            POSITIONS.forEachIndexed { index, at ->
                grabber.setTimestamp((length * at).toLong())
                val frames = generateSequence { grabber.grabImage()?.toComposeBitmap() }
                    .take(count)
                    .toList()
                if (frames.isEmpty()) return@flow
                emit(Excerpt(index, frames, frameMs, color))
            }
        }
    } finally {
        grabber.stop()
        grabber.release()
    }
}.buffer(1).flowOn(Dispatchers.IO.limitedParallelism(1))

/**
 * Opened twice: once to learn the picture's shape, then at [OUT_WIDTH] with that
 * shape. The output size is fixed before `start()`, and reading it back after
 * assigning one side returns the value written (`SeekPreviewSource.applyPreviewSize`).
 * The second open is cheap because the probe is capped.
 */
private fun openGrabber(file: File): FFmpegFrameGrabber? = try {
    FFmpegFrameGrabber(file).apply {
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        setVideoOption("threads", THREADS)
        pixelFormat = AV_PIX_FMT_RGBA
        audioStream = Int.MAX_VALUE
        start()
        val sourceWidth = imageWidth.coerceAtLeast(1)
        val sourceHeight = imageHeight
        stop()
        imageWidth = OUT_WIDTH
        val scaled = (OUT_WIDTH.toLong() * sourceHeight / sourceWidth).toInt()
        imageHeight = (scaled - scaled % 2).coerceAtLeast(2)
        start()
    }
} catch (_: Exception) {
    null
}
