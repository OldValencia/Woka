package com.woka.feature.viewer.video.engine

import kotlinx.coroutines.channels.Channel
import java.nio.Buffer
import java.nio.ShortBuffer

/**
 * PCM handoff between the audio decoder and the sound line, split out of
 * [startAudioDecodeLoop] per the §2 file-size mandate. Shared by the plain path
 * and the `atempo` path (§6), which both end up writing the same byte layout.
 */

internal suspend fun sendAudioSamples(samples: Array<Buffer>, audioChannel: Channel<ByteArray>) {
    audioChannel.send((samples[0] as ShortBuffer).toPcmBytes())
}

/** Interleaved S16 samples to the little-endian byte layout the sound line takes. */
internal fun ShortBuffer.toPcmBytes(): ByteArray {
    val shortArray = ShortArray(capacity())
    get(shortArray)
    val byteArray = ByteArray(shortArray.size * 2)
    for (i in shortArray.indices) {
        byteArray[i * 2] = shortArray[i].toByte()
        byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
    }
    return byteArray
}
