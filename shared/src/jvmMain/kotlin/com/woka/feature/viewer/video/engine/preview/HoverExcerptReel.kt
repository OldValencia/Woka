package com.woka.feature.viewer.video.engine.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.StillColorInfo
import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.lastPicture
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.probe.toColour
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.feature.viewer.video.engine.probe.clockwiseTurns
import com.woka.feature.viewer.video.engine.probe.rotatedClockwise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
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
 * [kept] is a cut film's ranges (ME-284); empty plays the file as shot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun hoverExcerpts(file: File, kept: List<SourceRange> = emptyList()): Flow<Excerpt> = flow {
    val grabber = openGrabber(file) ?: return@flow
    try {
        val fps = grabber.frameRate.takeIf { it.isFinite() && it > 1.0 } ?: 24.0
        val count = (EXCERPT_SECONDS * fps).roundToInt()
        val frameMs = (1000.0 / fps).roundToInt().toLong()
        var hdr = grabber.readStreamHdrInfo()
        val spans = excerptSpans(grabber.pictureLengthUs(file), kept)
        val turns = grabber.clockwiseTurns // upright, as the player shows it (VF-105)
        while (true) {
            spans.forEachIndexed { index, span ->
                // Converted at once: the grabber hands back the same `Frame` every time.
                fun Frame.inSpan() = takeIf { it.timestamp < span.last }?.toComposeBitmap()?.rotatedClockwise(turns)
                val first = grabber.grabImageAt(span.first)?.inSpan()
                hdr = hdr.readFrom(grabber.lastPicture) // the film's white point, once (ME-301)
                val rest = generateSequence { grabber.grabImage()?.inSpan() }
                    .take(count - 1)
                    .toList()
                val frames = listOfNotNull(first) + rest
                if (frames.isEmpty()) return@flow
                emit(Excerpt(index, frames, frameMs, hdr.toColour()))
            }
        }
    } finally {
        grabber.stop()
        grabber.release()
    }
}.buffer(1).flowOn(Dispatchers.IO.limitedParallelism(1))

/**
 * Where each excerpt starts and the source time it must stop before, in µs. An unedited
 * film runs on past a position; a cut one (ME-284) samples the **result** and stops at the
 * end of the kept range it landed in, so the reel never shows what was cut away.
 */
private fun excerptSpans(lengthUs: Long, kept: List<SourceRange>): List<LongRange> {
    if (kept.isEmpty()) return POSITIONS.map { at -> (lengthUs * at).toLong()..Long.MAX_VALUE }
    val clock = EditedClock(kept)
    return POSITIONS.map { at ->
        val (index, sourceMs) = clock.toSource((clock.totalMs * at).toLong())
        // A freeze's one frame still has to be grabbed: a span of one frame, not none (ME-217).
        sourceMs * 1000L..maxOf(kept[index].toMs, kept[index].fromMs + 40L) * 1000L
    }
}

/**
 * The **picture's** length, the stream the reel plays: a container's own duration is the
 * longest of its streams, and an `avi` muxed with AAC declared 26 s of sound over 10 s of
 * video (2026-11-04) — so excerpts three to five fell past the last frame and the reel
 * stopped after two. The container's answer is the fallback when the stream states none.
 */
private fun FFmpegFrameGrabber.pictureLengthUs(file: File): Long {
    val stream = formatContext?.takeIf { videoStream in 0 until it.nb_streams() }?.streams(videoStream)
    val base = stream?.time_base()
    val own = if (stream != null && base != null && base.den() > 0 && stream.duration() > 0) {
        stream.duration() * 1_000_000L * base.num() / base.den()
    } else {
        0L
    }
    return own.takeIf { it > 0 } ?: (lengthMs(file) * 1000L)
}

/**
 * Opened twice: once to learn the picture's shape, then at [OUT_WIDTH] with that
 * shape. The output size is fixed before `start()`, and reading it back after
 * assigning one side returns the value written (`SeekPreviewSource.applyPreviewSize`).
 * The second open is cheap because the probe is capped.
 */
private fun openGrabber(file: File): FFmpegFrameGrabber? = try {
    FreshFrameGrabber(file).apply {
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
