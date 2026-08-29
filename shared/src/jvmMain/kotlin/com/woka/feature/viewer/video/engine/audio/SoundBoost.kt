package com.woka.feature.viewer.video.engine.audio

/**
 * Volume past 100 %, applied to the samples rather than to the line.
 *
 * **Why not `MASTER_GAIN`.** `setVolumeInternal` turns the level into decibels and clamps
 * it into the control's *reported* `[minimum, maximum]`. Below unity that is exactly what
 * is wanted; above it the ceiling belongs to the device — it differs by machine and the
 * clamp is silent, so a boost asked for there would work on one computer and do nothing
 * on the next.
 *
 * **The level is therefore split at unity.** The line keeps [lineLevel], which is what
 * runs today whenever boost is off or the slider is at 100 % or below; everything above
 * unity is [boostFactor], and that is multiplied into the samples on the way to the line.
 * The two are continuous at 1f — the same loudness whichever side does the work.
 */
internal fun lineLevel(volume: Float): Float = volume.coerceAtMost(1f)

internal fun boostFactor(volume: Float): Float = volume.coerceAtLeast(1f)

/**
 * [pcm] amplified by [factor] — 16-bit signed little-endian, which is what `initAudio`
 * opens the line as and therefore what the decoder puts on the channel.
 *
 * **Hard-clamped, never wrapped.** A sample multiplied past `Short.MAX_VALUE` and allowed
 * to overflow flips sign, and a waveform that jumps to its own negative is a crack rather
 * than a loud note — the one failure worth writing a test for. Clipping is the honest
 * price of boost; a limiter is a different feature.
 *
 * Returns the array it was handed when there is nothing to do, so the ordinary path
 * allocates nothing. Otherwise a **new** array: the buffer comes off a `Channel` and this
 * code does not own it.
 *
 * A trailing odd byte cannot happen with a frame-aligned buffer and is copied through
 * rather than trusted not to exist.
 */
internal fun boosted(pcm: ByteArray, factor: Float): ByteArray {
    if (factor <= 1f) return pcm

    val out = pcm.copyOf()
    var i = 0
    while (i + 1 < out.size) {
        val sample = ((out[i + 1].toInt() shl 8) or (out[i].toInt() and 0xFF)).toShort()
        val scaled = (sample * factor)
            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            .toInt()
        out[i] = (scaled and 0xFF).toByte()
        out[i + 1] = ((scaled shr 8) and 0xFF).toByte()
        i += 2
    }
    return out
}
