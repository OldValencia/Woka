package com.woka.ui.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.random.Random

object ThumbnailLoader {
    private val thumbnailCache = ConcurrentHashMap<String, ImageBitmap>()
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(2)

    init {
        try {
            FFmpegLogCallback.set()
            av_log_set_level(AV_LOG_QUIET)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadThumbnail(item: MediaItem): ImageBitmap? {
        thumbnailCache[item.file.absolutePath]?.let { return it }
        return withContext(ioDispatcher) {
            try {
                if (item is MediaItem.Video) {
                    loadVideoThumbnail(item)
                } else {
                    val bitmap = Image.makeFromEncoded(item.file.readBytes()).toComposeImageBitmap()
                    thumbnailCache[item.file.absolutePath] = bitmap
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun loadVideoThumbnail(item: MediaItem.Video): ImageBitmap? {
        val previewDir = File(System.getProperty("user.home"), "Documents/OldValencia/Woka/.thumbnails")
        if (!previewDir.exists()) previewDir.mkdirs()
        val previewFile = File(previewDir, "${item.file.name.hashCode()}.png")
        if (previewFile.exists() && previewFile.isFile) {
            val existing = try {
                ImageIO.read(previewFile)
            } catch (_: Exception) {
                null
            }
            if (existing != null && !isMonochrome(existing)) {
                val bitmap = Image.makeFromEncoded(previewFile.readBytes()).toComposeImageBitmap()
                thumbnailCache[item.file.absolutePath] = bitmap
                return bitmap
            }
        }
        return extractVideoThumbnail(item.file, previewFile)
    }

    private fun extractVideoThumbnail(file: File, previewFile: File): ImageBitmap? {
        val grabber = FFmpegFrameGrabber(file)
        return try {
            grabber.setVideoOption("hwaccel", "auto")
            grabber.pixelFormat = AV_PIX_FMT_BGR24
            grabber.start()
            val duration = grabber.lengthInTime
            val timestamps = buildTimestampCandidates(duration)
            var selectedImage: BufferedImage? = null
            val converter = Java2DFrameConverter()
            for (ts in timestamps) {
                val candidate = grabFrameAt(grabber, converter, ts, duration) ?: continue
                if (selectedImage == null) selectedImage = candidate
                if (!isMonochrome(candidate)) {
                    selectedImage = candidate
                    break
                }
            }
            selectedImage?.let { saveAndDecode(it, previewFile, file) }
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    private fun grabFrameAt(
        grabber: FFmpegFrameGrabber,
        converter: Java2DFrameConverter,
        targetTs: Long,
        duration: Long
    ): BufferedImage? {
        if (targetTs < duration || duration <= 0L) grabber.setTimestamp(targetTs)
        for (i in 0 until 15) {
            val frame: Frame? = grabber.grabImage()
            if (frame != null && frame.image != null) {
                return converter.getBufferedImage(frame)
            }
        }
        return null
    }

    private fun saveAndDecode(image: BufferedImage, previewFile: File, file: File): ImageBitmap {
        val scale = min(400.0 / image.width, 400.0 / image.height).coerceAtMost(1.0)
        val w = (image.width * scale).toInt()
        val h = (image.height * scale).toInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, w, h, null)
            dispose()
        }
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(scaled, "png", outputStream)
        val bytes = outputStream.toByteArray()
        previewFile.writeBytes(bytes)
        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
        thumbnailCache[file.absolutePath] = bitmap
        return bitmap
    }

    private fun buildTimestampCandidates(duration: Long): List<Long> {
        return if (duration > 1000000L) {
            val d05 = (duration * 0.05).toLong()
            val d15 = (duration * 0.15).toLong()
            val d20 = (duration * 0.20).toLong()
            val d25 = (duration * 0.25).toLong()
            listOf(
                Random.nextLong(d05, d15.coerceAtLeast(d05 + 1)),
                Random.nextLong(d15, d20.coerceAtLeast(d15 + 1)),
                Random.nextLong(d20, d25.coerceAtLeast(d20 + 1))
            )
        } else if (duration > 0L) {
            listOf((duration / 2).coerceAtLeast(0L))
        } else {
            listOf(2000000L, 5000000L, 10000000L, 20000000L)
        }
    }

    private fun isMonochrome(image: BufferedImage): Boolean {
        if (image.width <= 0 || image.height <= 0) return true
        var minR = 255
        var maxR = 0
        var minG = 255
        var maxG = 0
        var minB = 255
        var maxB = 0
        for (y in 0 until image.height step (image.height / 20).coerceAtLeast(1)) {
            for (x in 0 until image.width step (image.width / 20).coerceAtLeast(1)) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (g < minG) minG = g
                if (g > maxG) maxG = g
                if (b < minB) minB = b
                if (b > maxB) maxB = b
            }
        }
        return (maxR - minR) < 18 && (maxG - minG) < 18 && (maxB - minB) < 18
    }
}
