package com.woka.feature.viewer.shell.ambient

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode

/**
 * The glow's resolution. Sixty-four pixels across *is* the blur: upscaled sixty
 * times to a fullscreen bar, each source pixel is already a soft patch, so the
 * `BlurEffect` on top only has to remove the bilinear tent, not build a gradient
 * out of a 4K frame.
 */
internal const val AMBIENT_W = 64
internal const val AMBIENT_H = 36

/**
 * How often a new frame is folded in. Twenty per second rather than ten: the
 * published bitmap is what the layer redraws, so the tick rate is the fill's own
 * frame rate, and ten was slow enough to see it step. A 64×36 resample costs
 * nothing next to the decode already running.
 */
internal const val AMBIENT_SAMPLE_MS = 50L

/**
 * Weight of each new sample. At one sample per [AMBIENT_SAMPLE_MS] this is a time
 * constant of roughly 800ms — which is the whole point of the accumulator rather
 * than drawing the live frame: a hard cut moves the fill over about a second
 * instead of flashing, and nothing on screen flickers at the cut rate.
 */
private const val BLEND = 0.06f

/**
 * Keeps a tiny, temporally smoothed copy of the video for [AmbientFillLayer].
 *
 * **Frame retention (§6).** [blend] copies its argument and never stores the
 * reference, and it deliberately does *not* call `frameRetention.acknowledge` — it
 * does not draw the decoder's bitmap, and acknowledging would advance the close
 * mark for a frame no scene has drawn. The copy is safe because a frame is closed
 * only once sixteen strictly newer ones have been drawn (`FrameRetention.kt`),
 * ~660ms at 24fps, against a downscale measured in milliseconds.
 */
@Stable
internal class AmbientSampler {

    /** The published accumulator. Null until the first frame has been folded in. */
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The transfer the sample was taken under, so the layer tone maps it as the picture is. */
    var color by mutableStateOf(StillColorInfo.Sdr)
        private set

    private val info = ImageInfo(AMBIENT_W, AMBIENT_H, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
    private val scratch = Bitmap().apply { allocPixels(info) }
    private val scratchPixels = scratch.peekPixels()
    private val accumulator = FloatArray(AMBIENT_W * AMBIENT_H * 4)
    private var seeded = false

    fun blend(frame: ImageBitmap, hdr: HdrInfo?) {
        val target = scratchPixels ?: return
        val source = Image.makeFromBitmap(frame.asSkiaBitmap())
        val scaled = try {
            source.scalePixels(target, SamplingMode.MITCHELL, false)
        } finally {
            source.close()
        }
        if (!scaled) return
        val sample = scratch.readPixels() ?: return
        if (sample.size != accumulator.size) return
        publish(accumulate(sample), hdr)
    }

    /**
     * Folds [sample] into the accumulator and returns the bytes to publish. The
     * first sample is taken outright: easing up from an all-black accumulator
     * would fade the fill in twice, once here and once through the layer's alpha.
     */
    private fun accumulate(sample: ByteArray): ByteArray {
        val out = ByteArray(sample.size)
        val weight = if (seeded) BLEND else 1f
        for (i in accumulator.indices) {
            val incoming = (sample[i].toInt() and 0xFF).toFloat()
            accumulator[i] += (incoming - accumulator[i]) * weight
            out[i] = accumulator[i].toInt().coerceIn(0, 255).toByte()
        }
        seeded = true
        return out
    }

    /**
     * A fresh 9KB bitmap per tick, because mutating the published one would not
     * invalidate the draw. Built by `installPixels` rather than `makeRaster`, which
     * allocates a second bitmap and blits into it (see `FramePacer.convert`).
     */
    private fun publish(bytes: ByteArray, hdr: HdrInfo?) {
        val bitmap = Bitmap()
        if (!bitmap.installPixels(info, bytes, AMBIENT_W * 4)) return
        bitmap.setImmutable()
        val next = bitmap.asComposeImageBitmap()
        Snapshot.withMutableSnapshot {
            image = next
            color = hdr?.let { StillColorInfo(it.transfer, it.isWideGamut) } ?: StillColorInfo.Sdr
        }
    }
}
