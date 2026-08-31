package com.woka.feature.viewer.video.engine

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.core.data.markUsed
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.encodeWebp
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
import java.io.File
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
        val previewFile = WokaStorage.thumbnailFile(item.file)
        readCached(previewFile)?.let { cached ->
            ImageBitmapCache[item.file.absolutePath] = cached
            return cached
        }
        return extract(item.file, previewFile)
    }

    /**
     * The cached poster, decoded once and asked no questions.
     *
     * This used to re-run [isMonochrome] on what it read and go back to ffmpeg when the
     * answer was yes — so a video whose poster is legitimately flat, a dark opening frame
     * being an ordinary thing to own, was **re-extracted on every launch, forever**. The
     * heuristic belongs to [extract], which is where a frame is chosen; by the time one
     * has been chosen and written, re-asking only re-runs the same failing search.
     *
     * Measured 2026-08-31 by `ThumbnailCacheProbe`: 8 of 8 posters rewritten on a second
     * pass, and 0 of 8 once a poster the heuristic accepts was planted at the same name.
     * The old code also decoded the file twice, once through `ImageIO` for the check and
     * again through Skia for the bitmap; one decode is left.
     */
    private fun readCached(previewFile: File): ImageBitmap? {
        if (!previewFile.isFile) return null
        val bitmap = runCatching {
            Image.makeFromEncoded(previewFile.readBytes()).toComposeImageBitmap()
        }.getOrNull()
        if (bitmap != null) markUsed(previewFile)
        return bitmap
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
            // Guarded, or a `start()` that threw is masked by whatever `stop()`
            // throws on top of it and the real cause never leaves this method (F5).
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    private fun grabFrameAt(
        grabber: FFmpegFrameGrabber,
        converter: Java2DFrameConverter,
        targetTs: Long,
        duration: Long
    ): BufferedImage? {
        if (duration !in 1..targetTs) grabber.setTimestamp(targetTs)
        (0 until 15).forEach { _ ->
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
        // Through Skia rather than `ImageIO.write`, which has no WebP encoder at all.
        // It also removes a round trip the old code paid without needing to: the
        // poster used to be encoded to PNG bytes only so that Skia could decode them
        // straight back for the bitmap this returns.
        val bitmap = scaled.toComposeImageBitmap()
        previewFile.writeBytes(bitmap.encodeWebp())
        ImageBitmapCache[file.absolutePath] = bitmap
        return bitmap
    }
}
