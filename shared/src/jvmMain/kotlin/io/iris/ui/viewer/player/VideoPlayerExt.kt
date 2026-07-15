package io.iris.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import org.jetbrains.skia.Image as SkiaImage
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

internal fun BufferedImage.toComposeBitmap(): ImageBitmap {
    val width = this.width
    val height = this.height
    val argbPixels = IntArray(width * height)
    this.getRGB(0, 0, width, height, argbPixels, 0, width)
    
    val byteBuffer = ByteBuffer.allocate(width * height * 4)
    for (pixel in argbPixels) {
        byteBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
        byteBuffer.put(((pixel shr 8) and 0xFF).toByte())  // G
        byteBuffer.put((pixel and 0xFF).toByte())          // B
        byteBuffer.put(((pixel shr 24) and 0xFF).toByte()) // A
    }
    
    val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = SkiaImage.makeRaster(imageInfo, byteBuffer.array(), width * 4)
    return image.toComposeImageBitmap()
}

internal fun setVolumeInternal(line: SourceDataLine, vol: Float) {
    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val min = gainControl.minimum
        val max = gainControl.maximum
        val safeVol = vol.coerceAtLeast(0.0001f).toDouble()
        val dB = (20.0 * log10(safeVol)).toFloat()
        gainControl.value = dB.coerceIn(min, max)
    }
}
