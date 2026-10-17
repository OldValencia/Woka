package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.test.Test

/** About 2.5 s of a 24 fps film: one hover excerpt as SIDE-006 sketches it. */
private const val EXCERPT_FRAMES = 60

/** The preview's output width (`SeekPreviewSource.PREVIEW_WIDTH`). */
private const val OUT_WIDTH = 240

/**
 * What a hover excerpt costs — the measurement SIDE-006 asks for **before** the
 * excerpt length is fixed.
 *
 *     $env:WOKA_PROBE_VIDEOS = "D:\a.mkv;D:\b.mkv"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.HoverExcerptProbe" -i --no-daemon
 *
 * A seek-preview still is one seek and one frame (117–271 ms on 2160p HEVC,
 * `seek-preview-thumbnails.md`). An excerpt is that seek and then a run of
 * consecutive frames, so the number that decides whether it can play at all is the
 * **sustained** rate: below the film's own frame rate the excerpt cannot run in real
 * time, and the design falls back to a sequence of stills.
 *
 * The grabber is opened as `SeekPreviewSource` opens its own — capped probe, no
 * audio, RGBA, output scaled to [OUT_WIDTH] — so the numbers are the preview path's,
 * not a full decode's. Thread counts are compared because the preview keeps to two
 * so as not to starve playback, and an excerpt plays while nothing else does.
 *
 * Files come from the environment: the corpus tops out at 320×180, which is not a
 * question about 4K at all, and a 60 GB remux does not belong in the repository.
 * Asserts nothing; prints a table.
 */
class HoverExcerptProbe {

    @Test
    fun `a hover excerpt's sustained decode rate`() {
        val files = System.getenv("WOKA_PROBE_VIDEOS").orEmpty()
            .split(';').map(String::trim).filter(String::isNotEmpty).map(::File).filter(File::isFile)
        if (files.isEmpty()) {
            println("HoverExcerptProbe: set WOKA_PROBE_VIDEOS to one or more 2160p files; nothing measured")
            return
        }
        println("file | threads | open ms | seek+first ms | $EXCERPT_FRAMES frames ms | sustained fps | film fps")
        files.forEach { file -> listOf("2", "auto").forEach { threads -> measure(file, threads) } }
    }

    private fun measure(file: File, threads: String) {
        val openStart = System.nanoTime()
        val grabber = FFmpegFrameGrabber(file).apply {
            setOption("probesize", "2000000")
            setOption("analyzeduration", "1000000")
            setVideoOption("threads", threads)
            pixelFormat = AV_PIX_FMT_RGBA
            audioStream = Int.MAX_VALUE
            start()
        }
        val openMs = ms(openStart)
        val width = grabber.imageWidth
        val height = grabber.imageHeight
        grabber.stop()
        grabber.imageWidth = OUT_WIDTH
        grabber.imageHeight = (OUT_WIDTH.toLong() * height / width.coerceAtLeast(1)).toInt().let { it - it % 2 }
        grabber.start()
        val durationUs = grabber.lengthInTime
        val filmFps = grabber.frameRate
        try {
            // Five places spread over the runtime, away from the very ends.
            listOf(0.12, 0.31, 0.5, 0.69, 0.88).forEach { at ->
                val seekStart = System.nanoTime()
                grabber.setTimestamp((durationUs * at).toLong())
                grabber.grabImage()
                val seekMs = ms(seekStart)
                val runStart = System.nanoTime()
                var got = 0
                while (got < EXCERPT_FRAMES && grabber.grabImage() != null) got++
                val runMs = ms(runStart)
                val fps = if (runMs > 0) got * 1000.0 / runMs else 0.0
                println(
                    "${file.name.take(40)} | $threads | $openMs | $seekMs | $runMs | " +
                        "${"%.1f".format(fps)} | ${"%.3f".format(filmFps)}"
                )
            }
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000
}
