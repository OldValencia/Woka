package com.woka.probe

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_DEFAULT
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_NONREF
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File
import kotlin.test.Test

/** Places a join may land, as `JoinDecodeProbe` takes them. */
private val PREROLL_AT = listOf(0.13, 0.32, 0.51, 0.67, 0.86)

/** How near the target the cheap variants go back to decoding every frame in full. */
private const val FULL_DECODE_MARGIN_US = 300_000L

/**
 * What it costs to reach a frame, three ways — the measurement behind ME-235 re-cut as a
 * cheaper pre-roll rather than frames held in memory.
 *
 *     $env:WOKA_PROBE_VIDEOS = "D:\a.mkv"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.PrerollProbe" -i --no-daemon
 *
 * - **today** — `landAt` as written: every frame from the keyframe on is converted to RGBA
 *   and cloned, though only the last is used;
 * - **raw** — frames before the last [FULL_DECODE_MARGIN_US] are only decoded, never
 *   converted or cloned;
 * - **raw+nonref** — as raw, and while far from the target the decoder skips frames nothing
 *   refers to (`skip_frame = nonref`, through JavaCV's private `video_c`).
 *
 * Each at four and two decoder threads, native size, as a lane opens. The landed frame's
 * time is printed so a faster variant that lands elsewhere is visible. Asserts nothing.
 */
class PrerollProbe {

    @Test
    fun `three ways to reach a frame`() {
        val files = System.getenv("WOKA_PROBE_VIDEOS").orEmpty()
            .split(';').map(String::trim).filter(String::isNotEmpty).map(::File).filter(File::isFile)
        if (files.isEmpty()) {
            println("PrerollProbe: set WOKA_PROBE_VIDEOS to one or more films; nothing measured")
            return
        }
        for (file in files) for (threads in listOf("4", "2")) for (variant in listOf("today", "raw", "raw+nonref")) {
            measure(file, threads, variant)
        }
    }

    private fun measure(file: File, threads: String, variant: String) {
        val grabber = FFmpegFrameGrabber(file).apply {
            setVideoOption("threads", threads)
            pixelFormat = AV_PIX_FMT_RGBA
            audioStream = Int.MAX_VALUE
            start()
        }
        val context = codecContext(grabber)
        try {
            val rows = PREROLL_AT.map { at ->
                val targetUs = (grabber.lengthInTime * at).toLong()
                val start = System.nanoTime()
                val landedUs = when (variant) {
                    "today" -> today(grabber, targetUs)
                    else -> cheap(grabber, targetUs, if (variant == "raw+nonref") context else null)
                }
                "${ms(start)} ms @${(landedUs - targetUs) / 1000}"
            }
            println("PROBE ${file.name.take(20)} | t$threads | $variant | ${rows.joinToString(" · ")}")
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    /** `landAt`'s loop: convert and clone every frame, keep the last. */
    private fun today(grabber: FFmpegFrameGrabber, targetUs: Long): Long {
        grabber.setTimestamp(targetUs)
        var last: Frame? = null
        var landed = -1L
        while (true) {
            val frame = grabber.grabFrame(false, true, true, false, false) ?: break
            if (frame.image == null) continue
            last?.close()
            last = frame.clone()
            landed = grabber.timestamp
            if (landed >= targetUs) break
        }
        last?.close()
        return landed
    }

    /** Decode only until near the target — skipping non-reference frames when [context] is given. */
    private fun cheap(grabber: FFmpegFrameGrabber, targetUs: Long, context: AVCodecContext?): Long {
        // JavaCV's setTimestamp decodes forward to its target itself, unconverted; stopping
        // it a margin short is where skipping non-reference frames can pay.
        context?.skip_frame(AVDISCARD_NONREF)
        grabber.setTimestamp((targetUs - FULL_DECODE_MARGIN_US).coerceAtLeast(0L))
        context?.skip_frame(AVDISCARD_DEFAULT)
        var landed = -1L
        while (true) {
            val near = grabber.timestamp >= targetUs - 50_000L
            val frame = grabber.grabFrame(false, true, near, false, false) ?: break
            landed = grabber.timestamp
            if (near && frame.image != null && landed >= targetUs) {
                frame.clone().close()
                break
            }
        }
        return landed
    }

    private fun codecContext(grabber: FFmpegFrameGrabber): AVCodecContext? = runCatching {
        val field = FFmpegFrameGrabber::class.java.getDeclaredField("video_c")
        field.isAccessible = true
        field.get(grabber) as AVCodecContext
    }.getOrNull()

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000
}
