package com.woka.feature.editor.engine.video.render

import com.woka.feature.viewer.video.engine.audio.atempoChain
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import kotlin.math.roundToLong

/**
 * A clip's speed in a render (ME-216): where its frames land, and what its sound becomes.
 *
 * **Picture.** A frame of a clip at speed *s* is placed at `base + offset / s`. Faster, two
 * frames can round to one slot of the output's frame rate, and the later is dropped — an
 * encoder refuses a timestamp that does not move forward. Slower, slots are skipped, and
 * the player holds the frame before: no frame is invented.
 *
 * **Sound** goes through the preview's own `atempo` chain, so the render and the preview
 * change a clip's sound by the same filter.
 */
internal class RenderPace(private val recorder: FFmpegFrameRecorder) {

    private var lastSlot = -1L

    /** Records [frame] at [atUs] on the output, unless that slot is already taken. */
    fun place(frame: Frame, atUs: Long, record: (Frame) -> Unit) {
        val slot = (atUs * recorder.frameRate / 1_000_000.0).roundToLong()
        if (slot <= lastSlot) return
        lastSlot = slot
        recorder.timestamp = atUs
        record(frame)
    }
}

/** The tempo filter for one clip at [speed], or null at 1× or with no sound to change. */
internal fun clipTempo(grabber: FFmpegFrameGrabber, speed: Float): FFmpegFrameFilter? {
    if (speed == 1f || grabber.audioChannels <= 0) return null
    return runCatching {
        FFmpegFrameFilter(atempoChain(speed), grabber.audioChannels).apply {
            sampleFormat = grabber.sampleFormat
            sampleRate = grabber.sampleRate
            start()
        }
    }.getOrNull()
}

/** [samples] through [tempo] into [recorder]; straight in when there is no tempo. */
internal fun recordSound(recorder: FFmpegFrameRecorder, tempo: FFmpegFrameFilter?, samples: Frame) {
    if (tempo == null) return recorder.record(samples)
    tempo.push(samples)
    while (true) recorder.record(tempo.pullSamples() ?: break)
}
