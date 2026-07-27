package com.woka.ui.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

object ThumbnailLoader {
    private val thumbnailCache = ConcurrentHashMap<String, ImageBitmap>()
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(4)

    suspend fun loadThumbnail(item: MediaItem): ImageBitmap? {
        val cached = thumbnailCache[item.file.absolutePath]
        if (cached != null) return cached

        return withContext(ioDispatcher) {
            try {
                if (item is MediaItem.Video) {
                    val previewDir = File(System.getProperty("user.home"), "Documents/OldValencia/Woka/.thumbnails")
                    if (!previewDir.exists()) previewDir.mkdirs()
                    val previewFile = File(previewDir, "${item.file.name.hashCode()}.png")
                    
                    if (previewFile.exists() && previewFile.isFile) {
                        val bytes = previewFile.readBytes()
                        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                        thumbnailCache[item.file.absolutePath] = bitmap
                        return@withContext bitmap
                    }

                    org.bytedeco.javacv.FFmpegLogCallback.set()
                    org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET)
                    
                    val grabber = FFmpegFrameGrabber(item.file)
                    grabber.start()
                    if (grabber.lengthInTime > 1000000L) {
                        grabber.setTimestamp(1000000L)
                    }
                    var frame: org.bytedeco.javacv.Frame? = null
                    var attempts = 0
                    while (attempts < 100) {
                        frame = grabber.grabImage()
                        if (frame != null && frame.image != null) break
                        attempts++
                    }
                    if (frame?.image != null) {
                        val converter = Java2DFrameConverter()
                        val originalImage = converter.getBufferedImage(frame)
                        val maxDim = 400.0
                        val scale = kotlin.math.min(maxDim / originalImage.width, maxDim / originalImage.height).coerceAtMost(1.0)
                        val newWidth = (originalImage.width * scale).toInt()
                        val newHeight = (originalImage.height * scale).toInt()
                        val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
                        val g2d = scaledImage.createGraphics()
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
                        g2d.dispose()

                        val outputStream = ByteArrayOutputStream()
                        ImageIO.write(scaledImage, "png", outputStream)
                        val bytes = outputStream.toByteArray()
                        previewFile.writeBytes(bytes)
                        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                        thumbnailCache[item.file.absolutePath] = bitmap
                        grabber.stop()
                        grabber.release()
                        bitmap
                    } else {
                        grabber.stop()
                        grabber.release()
                        null
                    }
                } else {
                    val bytes = item.file.readBytes()
                    val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    thumbnailCache[item.file.absolutePath] = bitmap
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
