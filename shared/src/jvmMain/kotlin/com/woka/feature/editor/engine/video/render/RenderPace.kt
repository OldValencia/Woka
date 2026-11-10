package com.woka.feature.editor.engine.video.render

import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.audio.atempoChain
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
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

/**
 * A freeze in a render (ME-217): the frame at the clip's start, drawn through its look and
 * placed on every slot of the hold. The silence under it is `RenderAudio`'s, made up to the
 * hold's end by the caller, so it is counted with the rest of the sound.
 */
internal fun recordFreeze(
    grabber: FFmpegFrameGrabber,
    recorder: FFmpegFrameRecorder,
    pace: RenderPace,
    picture: FramePicture?,
    clip: TrimClip,
    baseUs: Long,
    startUs: Long
) {
    val fromUs = clip.fromMs * 1000L
    grabber.setTimestamp(fromUs)
    var still: Frame? = null
    while (true) {
        val frame = grabber.grabImage() ?: break
        still = frame
        if (frame.timestamp - startUs >= fromUs) break
    }
    val held = still ?: return
    var shown = picture?.draw(held, baseUs / 1000L) ?: held
    val stepUs = (1_000_000.0 / recorder.frameRate).toLong().coerceAtLeast(1L)
    var atUs = baseUs
    while (atUs < baseUs + clip.holdMs * 1000L) {
        // Drawn again per slot only when the look moves with time, so a caption fades over a
        // freeze as it does on the canvas (ME-263); otherwise the one drawing serves the hold.
        if (picture != null && picture.moves && atUs > baseUs) shown = picture.draw(held, atUs / 1000L)
        pace.place(shown, atUs) { recorder.record(it, AV_PIX_FMT_RGBA) }
        atUs += stepUs
    }
}
