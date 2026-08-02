package com.woka.ui.viewer.player

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import org.jetbrains.skia.Image as SkiaImage

internal fun Frame.toComposeBitmap(hdrInfo: HdrInfo? = null): ImageBitmap {
    val buffer = this.image[0] as ByteBuffer
    val bytes = ByteArray(this.imageStride * this.imageHeight)
    buffer.rewind()
    buffer.get(bytes)
    buffer.rewind()

    val imageInfo = ImageInfo(this.imageWidth, this.imageHeight, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = SkiaImage.makeRaster(imageInfo, bytes, this.imageStride)
    return image.toComposeImageBitmap()
}

/**
 * Publishes a decoded frame together with the HDR metadata it was decoded under,
 * in one snapshot.
 *
 * The bitmap itself is always plain RGBA — tone mapping is a UI decision driven by
 * [VideoPlayerState.currentFrameHdrInfo] picking `HdrImagePainter`. Writing the two
 * separately lets the UI recompose in between and draw a frame against the *other*
 * track's transfer function, which is what made a switch back to an HDR track flash
 * an untonemapped picture before correcting itself (§2.3).
 */
internal fun VideoPlayerState.publishFrame(bitmap: ImageBitmap, hdrInfo: HdrInfo?) {
    Snapshot.withMutableSnapshot {
        currentFrame = bitmap
        currentFrameHdrInfo = hdrInfo
    }
}

internal fun setVolumeInternal(line: SourceDataLine, vol: Float) {
    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val min = gainControl.minimum
        val max = gainControl.maximum
        val safeVol = vol.coerceAtLeast(0.0001f).toDouble()
        val dB = (20.0 * Math.log10(safeVol)).toFloat()
        gainControl.value = dB.coerceIn(min, max)
    }
}
