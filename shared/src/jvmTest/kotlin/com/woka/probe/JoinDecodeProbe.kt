package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test

/** Places a join may land, spread over the runtime and away from the ends. */
private val JOIN_AT = listOf(0.13, 0.32, 0.51, 0.67, 0.86)

/** What a pre-rolling decoder queues before the hand-off (ME-232: "two or three"). */
private const val PRE_ROLL_FRAMES = 3

/** Frames each decoder runs when both decode at once. */
private const val RUN_FRAMES = 120

/**
 * Two decoders, one file — the gate on `video-editor/playback/` (ME-230).
 *
 *     $env:WOKA_PROBE_VIDEOS = "D:\a.mkv;D:\b.mkv"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.JoinDecodeProbe" -i --no-daemon
 *
 * Three rows per file and output width, every grabber opened as the player opens its
 * own (`openVideoGrabber`: threads auto, RGBA, no audio, output at the drawn size):
 *
 * - **join today** — one decoder seeks to the next clip and decodes its first frame.
 *   That is the stall a cut costs now (`ClipWalk`, `PlayEditedRanges`);
 * - **pre-roll under play** — decoder A decodes flat out while B opens, seeks and queues
 *   [PRE_ROLL_FRAMES]; A's rate meanwhile says whether playback would stutter;
 * - **both run** — A and B each decode [RUN_FRAMES] at once, the cost of a hand-off
 *   whose second decoder keeps going.
 *
 * Files come from the environment: the corpus tops out at 320×180. Asserts nothing.
 */
class JoinDecodeProbe {

    @Test
    fun `two decoders on one file`() {
        val files = System.getenv("WOKA_PROBE_VIDEOS").orEmpty()
            .split(';').map(String::trim).filter(String::isNotEmpty).map(::File).filter(File::isFile)
        if (files.isEmpty()) {
            println("JoinDecodeProbe: set WOKA_PROBE_VIDEOS to one or more 2160p files; nothing measured")
            return
        }
        files.forEach { file -> listOf(0, 1920).forEach { width -> measure(file, width) } }
    }

    private fun measure(file: File, width: Int) {
        val name = file.name.take(28)
        val a = open(file, width)
        val fps = a.frameRate
        val size = "${a.imageWidth}x${a.imageHeight}"
        println("PROBE $name | $size | film ${"%.3f".format(fps)} fps")
        try {
            JOIN_AT.forEach { at ->
                val start = System.nanoTime()
                a.setTimestamp((a.lengthInTime * at).toLong())
                a.grabImage()
                println("PROBE   join today @${pct(at)} | seek+first ${ms(start)} ms")
            }
            a.setTimestamp((a.lengthInTime * 0.05).toLong())
            preRollUnderPlay(file, width, a, fps)
            bothRun(file, width, a)
        } finally {
            a.stop()
            a.release()
        }
    }

    private fun preRollUnderPlay(file: File, width: Int, a: FFmpegFrameGrabber, fps: Double) {
        val playing = AtomicBoolean(true)
        val decoded = AtomicInteger()
        val player = thread { while (playing.get() && a.grabImage() != null) decoded.incrementAndGet() }
        Thread.sleep(500)
        val openStart = System.nanoTime()
        val b = open(file, width)
        println("PROBE   second open ${ms(openStart)} ms (A playing)")
        try {
            JOIN_AT.forEach { at ->
                val before = decoded.get()
                val start = System.nanoTime()
                b.setTimestamp((b.lengthInTime * at).toLong())
                repeat(PRE_ROLL_FRAMES) { b.grabImage() }
                val took = ms(start)
                val aFps = if (took > 0) (decoded.get() - before) * 1000.0 / took else 0.0
                val verdict = if (aFps >= fps) "keeps up" else "STUTTERS"
                println("PROBE   pre-roll @${pct(at)} | B seek+$PRE_ROLL_FRAMES ${took} ms | A ${"%.1f".format(aFps)} fps $verdict")
            }
        } finally {
            playing.set(false)
            player.join()
            b.stop()
            b.release()
        }
    }

    private fun bothRun(file: File, width: Int, a: FFmpegFrameGrabber) {
        val b = open(file, width)
        try {
            b.setTimestamp((b.lengthInTime * 0.5).toLong())
            var aMs = 0L
            var bMs = 0L
            val second = thread { bMs = run(b) }
            aMs = run(a)
            second.join()
            println("PROBE   both run | A ${rate(aMs)} fps | B ${rate(bMs)} fps")
        } finally {
            b.stop()
            b.release()
        }
    }

    private fun run(grabber: FFmpegFrameGrabber): Long {
        val start = System.nanoTime()
        repeat(RUN_FRAMES) { grabber.grabImage() }
        return ms(start)
    }

    /** As `openVideoGrabber` and `VideoPlayerRenderScale` open the player's: [width] 0 is native. */
    private fun open(file: File, width: Int): FFmpegFrameGrabber {
        val grabber = FFmpegFrameGrabber(file).apply {
            setVideoOption("threads", "auto")
            pixelFormat = AV_PIX_FMT_RGBA
            audioStream = Int.MAX_VALUE
            start()
        }
        if (width == 0 || width >= grabber.imageWidth) return grabber
        val height = (width.toLong() * grabber.imageHeight / grabber.imageWidth).toInt().let { it - it % 2 }
        grabber.stop()
        grabber.imageWidth = width
        grabber.imageHeight = height
        grabber.start()
        return grabber
    }

    private fun rate(tookMs: Long) = "%.1f".format(if (tookMs > 0) RUN_FRAMES * 1000.0 / tookMs else 0.0)

    private fun pct(at: Double) = "${(at * 100).toInt()}%"

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000
}
