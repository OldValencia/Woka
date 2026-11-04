package com.woka.feature.editor.engine.video.render

import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.soundGain
import com.woka.feature.editor.tools.trim.TrimClip
import org.bytedeco.javacv.Frame
import java.nio.ShortBuffer

/**
 * An edit's sound in a render (ME-240): the whole film's mix, and each clip's own. The render
 * decodes sound as interleaved 16-bit, so the gain is a multiplication per sample — the same
 * envelope `soundGain` gives the preview, at each sample's place on the result.
 */
internal class RenderSound(val whole: SoundMix?, val clipOf: (TrimClip) -> SoundMix?, val totalMs: Long) {

    /**
     * [samples] of [clip] scaled in place. [startMs] is where the first sample lands on the
     * result, [intoClipMs] how far into the clip that is; both advance by the clip's speed.
     */
    fun apply(samples: Frame, clip: TrimClip, startMs: Double, intoClipMs: Double, sampleRate: Int, channels: Int) {
        val pcm = samples.samples?.firstOrNull() as? ShortBuffer ?: return
        val mix = clipOf(clip)
        if (sampleRate <= 0 || channels <= 0) return
        val msPerFrame = 1000.0 / sampleRate / clip.speed
        val frames = pcm.limit() / channels
        for (n in 0 until frames) {
            val gain = soundGain(
                whole, mix,
                (startMs + n * msPerFrame).toLong(), totalMs,
                (intoClipMs + n * msPerFrame).toLong(), clip.lengthMs
            )
            if (gain == 1f) continue
            for (c in 0 until channels) {
                val at = n * channels + c
                pcm.put(at, (pcm.get(at) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
    }
}
