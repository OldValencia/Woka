package com.woka.probe

import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import com.woka.feature.viewer.video.engine.sync.originUs
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.io.File
import kotlin.test.Test

/** A folder of third-party video samples — not committed, so pointed at rather than shipped. */
private const val SAMPLES_ENV = "WOKA_VIDEO_SAMPLES"

/** The committed VF-001 fixtures, probed alongside the downloads. */
private val FIXTURES = listOf("dahua.dav", "raw.h264", "raw-10bit.h264", "blank.bik", "realvideo.rm", "pal.gxf")

/**
 * What the player's own grabber setup does with each VF-001 container — the
 * measurement behind the new `VIDEO_EXTENSIONS`. Run by hand over the downloads
 * in the feature doc §2:
 *
 *     $env:WOKA_VIDEO_SAMPLES = "D:\video-samples"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.VideoFormatProbe" -i
 *
 * Per file, through the app's own helpers: `lengthInTime` against the length the
 * app shows (`lengthMs`) and the time counting it took, the container's origin,
 * the first frame, how many frames decode to EOF and the last one's media time,
 * where `grabImageAt` lands for a target at 50 %, and the audio codec with where
 * an audio seek to 50 % lands. Skips without the variable; asserts nothing.
 */
class VideoFormatProbe {

    @Test
    fun `what the player grabber does with each video sample`() {
        val folder = System.getenv(SAMPLES_ENV)?.let(::File) ?: return
        val media = File(javaClass.getResource("/media/README.md")!!.toURI()).parentFile
        println("file | lengthInTime → shown ms (count ms) | origin s | first | frames to EOF, last ms | 50 % → landed | audio")
        (FIXTURES.map { File(media, it) } + folder.listFiles().orEmpty().sortedBy { it.name })
            .forEach { println(row(it)) }
    }

    private fun row(file: File): String = runCatching {
        val video = videoGrabber(file)
        val declared = video.lengthInTime / 1000
        val countStart = System.nanoTime()
        val shown = video.lengthMs(file)
        val countMs = (System.nanoTime() - countStart) / 1_000_000
        val origin = video.formatContext.originUs / 1_000_000
        val first = video.grabImage()?.let { "${it.imageWidth}×${it.imageHeight} @${video.mediaTimeUs / 1000}" }
        var frames = if (first == null) 0 else 1
        val early = mutableListOf(video.mediaTimeUs / 1000)
        while (video.grabImage() != null) {
            frames++
            if (early.size < 4) early += video.mediaTimeUs / 1000
        }
        val lastMs = video.mediaTimeUs / 1000
        video.close()
        val target = shown / 2
        val landed = runCatching { landing(file, target) }.getOrElse { "FAILED: ${it.message?.take(50)}" }
        val audio = runCatching { audio(file, target) }.getOrElse { "none (${it.message?.take(30)})" }
        val truth = runCatching { matchesDecodeFromStart(file, target) }.getOrElse { "FAILED" }
        "${file.name} | $declared → $shown ($countMs) | $origin | $first $early | $frames, $lastMs | " +
            "$target → $landed, same picture as decoding there: $truth | $audio"
    }.getOrElse { "${file.name} | FAILED: ${it::class.simpleName}: ${it.message?.take(100)}" }

    private fun videoGrabber(file: File) = FFmpegFrameGrabber(file).apply {
        pixelFormat = AV_PIX_FMT_RGBA
        start()
    }

    private fun landing(file: File, targetMs: Long): String {
        val grabber = videoGrabber(file)
        try {
            grabber.grabImageAt(targetMs * 1000) ?: return "nothing"
            return "${grabber.mediaTimeUs / 1000}"
        } finally {
            grabber.close()
        }
    }

    /**
     * Whether `grabImageAt` shows the picture that decoding from the start to the
     * same media time shows. A demuxer whose times are frame counters can report
     * the target after a seek that landed somewhere else entirely.
     */
    private fun matchesDecodeFromStart(file: File, targetMs: Long): Boolean? {
        val seeking = videoGrabber(file)
        val walking = videoGrabber(file)
        try {
            val seeked = seeking.grabImageAt(targetMs * 1000) ?: return null
            while (true) {
                val frame = walking.grabImage() ?: return null
                if (walking.mediaTimeUs >= seeking.mediaTimeUs) return digest(frame) == digest(seeked)
            }
        } finally {
            seeking.close()
            walking.close()
        }
    }

    private fun digest(frame: Frame): Int {
        val buffer = frame.image[0] as ByteBuffer
        val bytes = ByteArray(buffer.remaining())
        buffer.duplicate().get(bytes)
        return bytes.contentHashCode()
    }

    /** The audio grabber the player opens: stereo S16, video disabled. */
    private fun audio(file: File, targetMs: Long): String {
        val grabber = FFmpegFrameGrabber(file)
        grabber.audioChannels = 2
        grabber.sampleFormat = AV_SAMPLE_FMT_S16
        grabber.videoStream = Int.MAX_VALUE
        grabber.start()
        try {
            val codec = grabber.audioCodecName ?: "none"
            val yields = grabber.grabSamples()?.samples != null
            val seek = runCatching { grabber.setTimestamp(targetMs * 1000) }.exceptionOrNull()?.let { "throws" } ?: "ok"
            val afterSeek = grabber.mediaTimeUs / 1000
            val next = grabber.grabSamples()?.let { "${grabber.mediaTimeUs / 1000}" } ?: "EOF"
            return "$codec, samples $yields, seek $seek at $afterSeek, next $next"
        } finally {
            grabber.close()
        }
    }
}
