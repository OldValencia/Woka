package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.test.Test

/** Places a join may land, as `JoinDecodeProbe` takes them. */
private val AUDIO_JOIN_AT = listOf(0.13, 0.32, 0.51, 0.67, 0.86)

/**
 * What a seek costs the **audio** grabber — the question behind ME-233: the audio loop
 * decodes ~200 ms ahead of the picture and the line holds ~100 ms more, so a seek well
 * under that slack needs no pre-rolled audio lane, and one over it does.
 *
 *     $env:WOKA_PROBE_VIDEOS = "D:\a.mkv"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.AudioJoinProbe" -i --no-daemon
 *
 * The grabber is opened as `openAudioGrabber` opens the player's: video disabled,
 * stereo S16, a 2 MB probe. Seek, then decode to the target. Asserts nothing.
 */
class AudioJoinProbe {

    @Test
    fun `an audio seek's cost`() {
        val files = System.getenv("WOKA_PROBE_VIDEOS").orEmpty()
            .split(';').map(String::trim).filter(String::isNotEmpty).map(::File).filter(File::isFile)
        if (files.isEmpty()) {
            println("AudioJoinProbe: set WOKA_PROBE_VIDEOS to one or more films; nothing measured")
            return
        }
        files.forEach(::measure)
    }

    private fun measure(file: File) {
        val grabber = FFmpegFrameGrabber(file).apply {
            audioChannels = 2
            sampleFormat = AV_SAMPLE_FMT_S16
            videoStream = Int.MAX_VALUE
            setOption("probesize", "2000000")
            setOption("analyzeduration", "1000000")
        }
        val openStart = System.nanoTime()
        grabber.start()
        println("PROBE ${file.name.take(28)} | ${grabber.audioCodecName} ${grabber.sampleRate} Hz | open ${ms(openStart)} ms")
        try {
            AUDIO_JOIN_AT.forEach { at ->
                val targetUs = (grabber.lengthInTime * at).toLong()
                val start = System.nanoTime()
                grabber.setTimestamp(targetUs)
                var landedUs = -1L
                while (true) {
                    val frame = grabber.grabSamples() ?: break
                    if (frame.samples == null) continue
                    landedUs = grabber.timestamp
                    if (landedUs >= targetUs - 100_000L) break
                }
                println("PROBE   seek @${(at * 100).toInt()}% | ${ms(start)} ms | landed ${(landedUs - targetUs) / 1000} ms from target")
            }
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000
}
