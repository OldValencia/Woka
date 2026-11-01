package com.woka.probe

import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import com.woka.feature.viewer.video.engine.sync.originUs
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.test.Test

/** A folder of third-party audio samples — not committed, so pointed at rather than shipped. */
private const val SAMPLES_ENV = "WOKA_AUDIO_SAMPLES"

/** How many pictures the video grabber is allowed to pull before the row stops counting. */
private const val PICTURE_LIMIT = 5

/**
 * What the player's own grabber setup does with each candidate audio format — the
 * measurement behind `AUDIO_EXTENSIONS` (AP-001). Run by hand:
 *
 *     $env:WOKA_AUDIO_SAMPLES = "D:\audio-samples"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.AudioFormatProbe" -i
 *
 * The committed `media/audio/` fixtures are always probed; the downloads only with
 * the variable set. Per file: the demuxer and the audio stream; the length the app
 * would show and the container's origin; what the **video** grabber opened as
 * `openVideoGrabber` opens it sees — the `attached_pic` question, i.e. whether an
 * embedded cover arrives as a video stream; samples decoded to EOF on the **audio**
 * grabber opened as `openAudioGrabber` opens it, and the last one's media time;
 * where a seek to 50 % lands; the tags. Asserts nothing.
 */
class AudioFormatProbe {

    @Test
    fun `what the player grabbers do with each audio sample`() {
        val fixtures = File(javaClass.getResource("/media/audio")!!.toURI()).listFiles().orEmpty()
        val downloads = System.getenv(SAMPLES_ENV)?.let(::File)?.listFiles().orEmpty()
        println("file | demuxer | audio | declared → shown ms | origin ms | video grabber | to EOF | 50 % → landed | tags")
        (fixtures.sortedBy { it.name } + downloads.sortedBy { it.name }).forEach { println(row(it)) }
    }

    private fun row(file: File): String = runCatching {
        "${file.name} | ${audioSide(file)}"
    }.getOrElse { "${file.name} | FAILED: ${it::class.simpleName}: ${it.message?.take(100)}" }

    /** The grabber the player opens first: RGBA, no stream chosen. */
    private fun videoSide(file: File): String {
        val grabber = FFmpegFrameGrabber(file)
        grabber.pixelFormat = AV_PIX_FMT_RGBA
        grabber.start()
        try {
            if (grabber.videoCodecName == null) return "no video stream"
            var pictures = 0
            var size = "-"
            while (pictures < PICTURE_LIMIT) {
                val frame = grabber.grabImage() ?: break
                if (pictures == 0) size = "${frame.imageWidth}×${frame.imageHeight}"
                pictures++
            }
            val more = if (pictures == PICTURE_LIMIT) "+" else ""
            return "stream ${grabber.videoStream} ${grabber.videoCodecName} $size, $pictures$more pictures"
        } finally {
            grabber.close()
        }
    }

    /** The grabber the player decodes sound on: stereo S16, video disabled. */
    private fun audioGrabber(file: File) = FFmpegFrameGrabber(file).apply {
        audioChannels = 2
        sampleFormat = AV_SAMPLE_FMT_S16
        videoStream = Int.MAX_VALUE
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        start()
    }

    private fun audioSide(file: File): String {
        val grabber = audioGrabber(file)
        try {
            val demuxer = grabber.formatContext?.iformat()?.name()?.string ?: "?"
            val codec = grabber.audioCodecName ?: return "$demuxer | no audio stream"
            val stream = "$demuxer | $codec ${grabber.audioChannels}ch ${grabber.sampleRate} Hz"
            val declared = grabber.lengthInTime / 1000
            val shown = grabber.lengthMs(file)
            val origin = (grabber.formatContext?.originUs ?: 0L) / 1000
            var chunks = 0
            var lastMs = -1L
            while (grabber.grabSamples() != null) {
                chunks++
                lastMs = grabber.mediaTimeUs / 1000
            }
            val tags = tags(grabber)
            val landed = runCatching { landing(file, shown / 2) }.getOrElse { "FAILED: ${it.message?.take(40)}" }
            return "$stream | $declared → $shown | $origin | ${videoSide(file)} | " +
                "$chunks chunks, last $lastMs | ${shown / 2} → $landed | $tags"
        } finally {
            grabber.close()
        }
    }

    private fun landing(file: File, targetMs: Long): String {
        val grabber = audioGrabber(file)
        try {
            val thrown = runCatching { grabber.setTimestamp(targetMs * 1000) }.exceptionOrNull()
            if (thrown != null) return "seek throws (${thrown::class.simpleName})"
            val next = grabber.grabSamples() ?: return "EOF"
            return "${grabber.mediaTimeUs / 1000}${if (next.samples == null) " (no samples)" else ""}"
        } finally {
            grabber.close()
        }
    }

    /** Container tags, then the audio stream's own — Ogg and Opus keep theirs on the stream. */
    private fun tags(grabber: FFmpegFrameGrabber): String {
        val all = grabber.metadata.orEmpty() + grabber.audioMetadata.orEmpty()
        val wanted = all.filterKeys { it.lowercase() in setOf("title", "artist", "album") }
        return wanted.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "-" }
    }
}
