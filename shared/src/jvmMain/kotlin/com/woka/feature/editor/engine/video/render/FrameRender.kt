package com.woka.feature.editor.engine.video.render

import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File

/**
 * The encode path of a render (ME-292): every clip decoded, drawn through its picture when
 * there is pixel work, and encoded once into a fresh H.264 + AAC stream.
 *
 * **libopenh264**, because ME-290 measured it: the LGPL build has no x264, NVENC needs a
 * newer driver than the machine has, QSV refuses the session, and of the two that open,
 * openh264 lands on the bitrate it is given (3998 kbps for 4M) where Media Foundation
 * overshoots by 14 %. No CRF exists here, so the quality target is a **bitrate measured off
 * the source** (`render.md` §3.1): the same density, so a light edit comes out the same
 * size per second and therefore no larger than the source by exactly what was cut.
 *
 * Timestamps are the timeline's: each clip's frames are placed at where the clip starts
 * in the result, and audio is appended in order, so the two stay level clip by clip.
 */
internal fun encodeClips(
    source: File,
    target: File,
    format: String,
    clips: List<TrimClip>,
    /** Each clip's picture (ME-215) — one frame size for all, since geometry is the film's. */
    pictureOf: (TrimClip) -> FramePicture?,
    alive: () -> Boolean,
    progress: (Float) -> Unit,
    /** The edit's sound (ME-240), or null for sound as recorded. */
    sound: RenderSound? = null
) {
    val grabber = FreshFrameGrabber(source).apply {
        pixelFormat = AV_PIX_FMT_RGBA
        // Interleaved 16-bit, so a gain is one multiplication a sample (`RenderSound`).
        sampleFormat = AV_SAMPLE_FMT_S16
    }
    try {
        grabber.start()
        val picture = clips.firstNotNullOfOrNull(pictureOf)
        val size = picture?.outputSize ?: evenSize(grabber.imageWidth, grabber.imageHeight)
        val recorder = encodeRecorder(grabber, target, format, size, sourceBitrate(grabber, source))
        try {
            recordClips(grabber, recorder, clips, pictureOf, alive, progress, sound)
        } finally {
            recorder.stop()
            recorder.release()
        }
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

/** How far past a cut the clip's sound is looked for once its picture has passed it. */
private const val SOUND_TAIL_US = 2_000_000L

private fun recordClips(
    grabber: FFmpegFrameGrabber,
    recorder: FFmpegFrameRecorder,
    clips: List<TrimClip>,
    pictureOf: (TrimClip) -> FramePicture?,
    alive: () -> Boolean,
    progress: (Float) -> Unit,
    sound: RenderSound?
) {
    val start = containerStartUs(grabber)
    val totalUs = clips.sumOf { it.lengthMs } * 1000L
    val pace = RenderPace(recorder)
    val audio = RenderAudio(recorder, grabber.sampleRate, grabber.audioChannels)
    var baseUs = 0L
    for (clip in clips) {
        val picture = pictureOf(clip)
        if (clip.isFreeze) {
            audio.padTo(baseUs)
            recordFreeze(grabber, recorder, pace, picture, clip, baseUs, start)
            baseUs += clip.lengthMs * 1000L
            audio.padTo(baseUs)
            continue
        }
        val fromUs = clip.fromMs * 1000L
        val untilUs = clip.toMs * 1000L
        // A clip's speed retimes its picture and its sound (ME-216, `RenderPace`).
        val tempo = clipTempo(grabber, clip.speed)
        grabber.setTimestamp(fromUs)
        // The clip's sound is read to its own end even once the picture is past the cut:
        // a muxer interleaves up to a second, and that tail was lost (1.0.6 review).
        var pictureDone = false
        var soundDone = !audio.present
        var heard = false
        while (alive() && !(pictureDone && soundDone)) {
            val frame = grabber.grab() ?: break
            val atUs = frame.timestamp - start
            // A source whose sound stops before its picture: not read to the end of the file.
            if (pictureDone && atUs >= untilUs + SOUND_TAIL_US) break
            if (frame.image != null) {
                if (atUs >= untilUs) pictureDone = true
                if (atUs < fromUs || atUs >= untilUs) continue
                val outUs = baseUs + ((atUs - fromUs) / clip.speed.toDouble()).toLong()
                pace.place(picture?.draw(frame, outUs / 1000L) ?: frame, outUs) { recorder.record(it, AV_PIX_FMT_RGBA) }
                if (totalUs > 0) progress(outUs.toFloat() / totalUs)
            } else if (frame.samples != null) {
                if (atUs >= untilUs) soundDone = true
                val kept = audio.within(frame, atUs, fromUs, untilUs) ?: continue
                val intoMs = (maxOf(atUs, fromUs) - fromUs) / 1000.0 / clip.speed
                if (!heard) audio.padTo(baseUs + (intoMs * 1000).toLong())
                heard = true
                sound?.apply(kept, clip, baseUs / 1000.0 + intoMs, intoMs, grabber.sampleRate, grabber.audioChannels)
                audio.record(tempo, kept)
            }
        }
        audio.drain(tempo)
        if (!alive()) return
        baseUs += clip.lengthMs * 1000L
        audio.padTo(baseUs)
    }
}

