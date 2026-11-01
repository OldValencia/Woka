package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.DeinterlaceGraph
import com.woka.support.combScore
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVFrame.AV_FRAME_FLAG_INTERLACED
import org.bytedeco.ffmpeg.avutil.AVFrame.AV_FRAME_FLAG_TOP_FIELD_FIRST
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import kotlin.test.Test

/** A folder of third-party video samples — not committed, so pointed at rather than shipped. */
private const val SAMPLES_ENV = "WOKA_VIDEO_SAMPLES"

/**
 * VF-102 step 1: whether a decoded frame's interlaced/TFF flags reach the RGBA
 * frame the player grabs (`Frame.opaque`), and what [DeinterlaceGraph] does on the
 * grabber's RAW path — its timestamps and the comb it leaves. Run by hand:
 *
 *     $env:WOKA_VIDEO_SAMPLES = "D:\video-samples"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.DeinterlaceProbe" -i
 *
 * The committed `interlaced.ts` is probed with or without the variable. Asserts nothing.
 */
class DeinterlaceProbe {

    @Test
    fun `interlaced flags on the RGBA path and bwdif through DeinterlaceGraph`() {
        val media = File(javaClass.getResource("/media/README.md")!!.toURI()).parentFile
        val samples = System.getenv(SAMPLES_ENV)?.let(::File)?.listFiles().orEmpty().sortedBy { it.name }
        println("file | RGBA frames flagged interlaced / TFF of first 200")
        (listOf(File(media, "interlaced.ts"), File(media, "chapters.mkv")) + samples)
            .forEach { println("${it.name} | ${runCatching { flags(it) }.getOrElse { "FAILED: ${it.message}" }}") }
        println(filtered(File(media, "interlaced.ts")))
    }

    private fun flags(file: File): String {
        val grabber = FFmpegFrameGrabber(file).apply {
            pixelFormat = AV_PIX_FMT_RGBA
            start()
        }
        var frames = 0
        var interlaced = 0
        var tff = 0
        var rawComb = 0.0
        try {
            while (frames < 200) {
                val frame = grabber.grabImage() ?: break
                frames++
                val flags = (frame.opaque as? AVFrame)?.flags() ?: 0
                if (frames == 13) rawComb = combScore(frame)
                if (flags and AV_FRAME_FLAG_INTERLACED != 0) interlaced++
                if (flags and AV_FRAME_FLAG_TOP_FIELD_FIRST != 0) tff++
            }
        } finally {
            grabber.close()
        }
        return "$interlaced/$frames, TFF $tff, raw comb #12 $rawComb"
    }

    private fun filtered(file: File): String {
        val grabber = FFmpegFrameGrabber(file).apply {
            imageMode = FrameGrabber.ImageMode.RAW
            start()
        }
        val timeBase = grabber.formatContext.streams(grabber.videoStream).time_base()
        val graph = DeinterlaceGraph(timeBase, 0, 0)
        val lines = mutableListOf("RAW, ${grabber.imageWidth}×${grabber.imageHeight}")
        try {
            var index = 0
            while (true) {
                val frame = grabber.grabImage() ?: break
                val decoded = frame.opaque as AVFrame
                val pushed = frame.timestamp
                val out = graph.process(decoded)
                if (index < 4 || index == 12) {
                    lines += "#$index pushed $pushed → pulled ${out?.timestamp} " +
                        "${out?.imageWidth}×${out?.imageHeight}" + (out?.let { ", comb ${combScore(it)}" } ?: "")
                }
                index++
            }
            lines += "drain → ${graph.drain()?.timestamp}"
        } finally {
            graph.close()
            grabber.close()
        }
        return lines.joinToString("\n")
    }
}
