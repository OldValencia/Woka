package com.woka.feature.viewer.video.engine

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
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
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Routes FFmpeg's own logging into JavaCV and silences it. Called from the
 * thumbnail facade rather than from this object's initialiser so it still runs at
 * the exact moment the old `ThumbnailLoader` object was first touched — moving it
 * to first *video* use would leave the CLI "open a video directly" path noisy.
 */
internal fun quietFfmpegLogs() {
    try {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Poster frame for a video: read from the on-disk cache, or decoded and stored. */
internal object VideoThumbnailExtractor {

    fun load(item: MediaItem.Video): ImageBitmap? {
        val previewFile = thumbnailCacheFile(item.file)
        if (previewFile.exists() && previewFile.isFile) {
            val existing = try {
                ImageIO.read(previewFile)
            } catch (_: Exception) {
                null
            }
            if (existing != null && !isMonochrome(existing)) {
                val bitmap = Image.makeFromEncoded(previewFile.readBytes()).toComposeImageBitmap()
                ImageBitmapCache[item.file.absolutePath] = bitmap
                return bitmap
            }
        }
        return extract(item.file, previewFile)
    }

    private fun extract(file: File, previewFile: File): ImageBitmap? {
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
        if (duration !in 1..targetTs) grabber.setTimestamp(targetTs)
        (0 until 15).forEach { i ->
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
        ImageBitmapCache[file.absolutePath] = bitmap
        return bitmap
    }
}
