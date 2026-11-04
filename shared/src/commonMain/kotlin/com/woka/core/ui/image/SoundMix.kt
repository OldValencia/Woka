package com.woka.core.ui.image

import androidx.compose.runtime.Immutable

/**
 * What an edit does to a film's sound (ME-240): mute, a gain, and two linear fades in
 * milliseconds — `audio.md` §3.2's four settings, as plain data, so the viewer and the
 * render read what the editor wrote without naming the tool.
 *
 * One applies to the whole film (All Clips), and its fades sit at the film's two ends; one
 * may belong to a clip (This Clip), and its fades sit at that clip's. The two multiply.
 */
@Immutable
data class SoundMix(
    val mute: Boolean = false,
    /** 1 is the sound as recorded; 2 is twice as loud. */
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    /** The gain [intoMs] into a stretch [lengthMs] long — the film's, or a clip's. */
    fun gainAt(intoMs: Long, lengthMs: Long): Float {
        if (mute) return 0f
        var gain = volume
        if (fadeInMs > 0L && intoMs < fadeInMs) gain *= (intoMs.toFloat() / fadeInMs).coerceIn(0f, 1f)
        val left = lengthMs - intoMs
        if (fadeOutMs > 0L && left < fadeOutMs) gain *= (left.toFloat() / fadeOutMs).coerceIn(0f, 1f)
        return gain
    }
}

/**
 * The gain at one moment of an edited film: [whole]'s at [timelineMs] of [totalMs], times
 * [clip]'s at [intoClipMs] of [clipMs]. Either may be absent, which is unity.
 */
fun soundGain(whole: SoundMix?, clip: SoundMix?, timelineMs: Long, totalMs: Long, intoClipMs: Long, clipMs: Long): Float =
    (whole?.gainAt(timelineMs, totalMs) ?: 1f) * (clip?.gainAt(intoClipMs, clipMs) ?: 1f)
