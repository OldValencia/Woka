package com.woka.probe

import com.woka.feature.viewer.image.FfmpegStillDecoder
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.Test

/** A candidate: the extension, the muxer the bundled FFmpeg writes it with, the codec. */
private class Candidate(val ext: String, val format: String, val codec: Int, val pixel: Int, val still: Boolean)

/**
 * Which formats beyond the listed ones the bundled FFmpeg can actually read — the
 * measurement behind widening `MediaKind` (2026-10-17). Run by hand:
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.FormatUnlockProbe" -i
 *
 * Each candidate is written with the bundled FFmpeg and read back the way the app
 * reads it: a still through `FfmpegStillDecoder`, a video through a grabber's first
 * frame. A candidate the bundle cannot even write is reported as unmeasured, not as
 * unreadable — it says nothing either way. Asserts nothing; prints a table.
 */
class FormatUnlockProbe {

    private val candidates = listOf(
        Candidate("tga", "image2", avcodec.AV_CODEC_ID_TARGA, avutil.AV_PIX_FMT_BGRA, true),
        Candidate("qoi", "image2", avcodec.AV_CODEC_ID_QOI, avutil.AV_PIX_FMT_RGBA, true),
        Candidate("jp2", "image2", avcodec.AV_CODEC_ID_JPEG2000, avutil.AV_PIX_FMT_RGB24, true),
        Candidate("pcx", "image2", avcodec.AV_CODEC_ID_PCX, avutil.AV_PIX_FMT_RGB24, true),
        Candidate("ppm", "image2", avcodec.AV_CODEC_ID_PPM, avutil.AV_PIX_FMT_RGB24, true),
        Candidate("m4v", "ipod", avcodec.AV_CODEC_ID_MPEG4, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("3gp", "3gp", avcodec.AV_CODEC_ID_MPEG4, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("mpg", "mpeg", avcodec.AV_CODEC_ID_MPEG2VIDEO, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("ts", "mpegts", avcodec.AV_CODEC_ID_MPEG2VIDEO, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("m2ts", "mpegts", avcodec.AV_CODEC_ID_MPEG2VIDEO, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("wmv", "asf", avcodec.AV_CODEC_ID_WMV2, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("flv", "flv", avcodec.AV_CODEC_ID_FLV1, avutil.AV_PIX_FMT_YUV420P, false),
        Candidate("ogv", "ogg", avcodec.AV_CODEC_ID_THEORA, avutil.AV_PIX_FMT_YUV420P, false)
    )

    @Test
    fun `which candidate formats the bundled FFmpeg reads back`() {
        val dir = Files.createTempDirectory("woka-formats").toFile()
        try {
            println("ext | written | read back")
            candidates.forEach { c ->
                val file = File(dir, "sample.${c.ext}")
                val written = runCatching { write(file, c) }.exceptionOrNull()
                val verdict = if (written != null) "unmeasured" else read(file, c)
                println("${c.ext} | ${written?.let { "no: ${it.message?.take(60)}" } ?: "yes"} | $verdict")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun write(file: File, c: Candidate) {
        FFmpegFrameRecorder(file, 64, 48).apply {
            format = c.format
            videoCodec = c.codec
            pixelFormat = c.pixel
            frameRate = 25.0
            if (!c.still) videoBitrate = 200_000
            start()
            repeat(if (c.still) 1 else 25) { record(frame(it)) }
            stop()
            release()
        }
    }

    private fun read(file: File, c: Candidate): String = runCatching {
        if (c.still) {
            val bitmap = FfmpegStillDecoder.frame(file, 0)
            "yes, ${bitmap.width}×${bitmap.height}"
        } else {
            val grabber = FFmpegFrameGrabber(file).apply { start() }
            try {
                val image = grabber.grabImage()
                if (image?.image != null) "yes, ${grabber.imageWidth}×${grabber.imageHeight}" else "no frame"
            } finally {
                grabber.stop()
                grabber.release()
            }
        }
    }.getOrElse { "no: ${it.message?.take(60)}" }

    /** A gradient that moves, so an encoder has something to code and a decoder to return. */
    private fun frame(step: Int): Frame = Frame(64, 48, Frame.DEPTH_UBYTE, 3).also { f ->
        val buffer = f.image[0] as ByteBuffer
        for (y in 0 until 48) for (x in 0 until 64) {
            val at = y * f.imageStride + x * 3
            buffer.put(at, (x * 4).toByte())
            buffer.put(at + 1, (y * 5).toByte())
            buffer.put(at + 2, (step * 9).toByte())
        }
    }
}
