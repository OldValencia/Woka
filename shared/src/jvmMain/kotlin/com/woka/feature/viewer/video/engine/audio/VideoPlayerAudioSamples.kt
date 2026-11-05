package com.woka.feature.viewer.video.engine.audio

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.Buffer
import java.nio.ShortBuffer

/**
 * PCM handoff between the audio decoder and the sound line, split out of
 * [startAudioDecodeLoop] per the §2 file-size mandate. Shared by the plain path
 * and the `atempo` path (§6), which both end up writing the same byte layout.
 */

/**
 * One decoded frame onto the sound line, through `atempo` when the speed is off 1x.
 * Returns the frame's media time in ms — unaffected by `atempo`, so the loop's sync
 * comparisons stay in the same units at every speed — or null for a frame with no
 * samples. Moved out of the loop for the file-size mandate when AP-003 had it report
 * [fedUnder] for the audio-only clock.
 */
internal suspend fun VideoPlayerState.deliverAudioFrame(
    grabber: FFmpegFrameGrabber,
    frame: Frame,
    tempo: AudioTempoFilter?,
    audioChannel: Channel<ByteArray>,
    fedUnder: Int
): Long? {
    val samples = frame.samples ?: return null
    // Lazy self-heal: if the eager init was skipped (channel count came back 0 on
    // open) but audio is actually decoding, bring the sound line up now.
    if (soundLineInstance == null) initAudio(grabber, audioChannel)
    val atMs = grabber.mediaTimeUs / 1000L
    val chunks = tempo?.process(frame)
    if (chunks != null) {
        chunks.forEach { audioChannel.send(it) }
    } else {
        sendAudioSamples(samples, audioChannel)
    }
    generation.markAudioFed(fedUnder)
    return atMs
}

/** A frame through the edit's splice when the film is cut (ME-233), else the plain path. */
internal suspend fun VideoPlayerState.deliverEditedOrNot(
    grabber: FFmpegFrameGrabber,
    frame: Frame,
    tempo: AudioTempoFilter?,
    audioChannel: Channel<ByteArray>,
    fedUnder: Int
): AudioStep {
    if (edit.joins.active) return edit.audio.deliver(this, grabber, frame, tempo, audioChannel)
    return AudioStep(tempo, deliverAudioFrame(grabber, frame, tempo, audioChannel, fedUnder))
}

internal suspend fun sendAudioSamples(samples: Array<Buffer>, audioChannel: Channel<ByteArray>) {
    audioChannel.send((samples[0] as ShortBuffer).toPcmBytes())
}

/**
 * Interleaved S16 samples to the little-endian byte layout the sound line takes.
 *
 * Sized by `remaining()`, not `capacity()`: JavaCV reuses one buffer per stream, so
 * a frame shorter than the largest before it leaves `limit` below `capacity` —
 * `.thp` 2128 of 2156, `.amv` 3674 of 3676, `.rpl` 2912 of 2954 (VF-001,
 * 2026-11-01) — and reading to capacity threw `BufferUnderflowException`.
 */
internal fun ShortBuffer.toPcmBytes(): ByteArray {
    val shortArray = ShortArray(remaining())
    get(shortArray)
    val byteArray = ByteArray(shortArray.size * 2)
    for (i in shortArray.indices) {
        byteArray[i * 2] = shortArray[i].toByte()
        byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
    }
    return byteArray
}
