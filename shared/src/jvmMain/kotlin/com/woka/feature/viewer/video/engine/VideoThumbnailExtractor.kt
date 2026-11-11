package com.woka.feature.viewer.video.engine

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.core.data.markUsed
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.probe.clockwiseTurns
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Image
import java.io.File

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
        val cacheKey = ImageBitmapCache.key(item.file.absolutePath, 0)
        val mode = HdrView.mode
        readCached(previewFile)?.let { cached ->
            ImageBitmapCache[cacheKey] = cached
            return cached
        }
        return extract(item.file, previewFile, cacheKey, mode)
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

    private fun extract(file: File, previewFile: File, cacheKey: String, mode: HdrViewMode): ImageBitmap? {
        val grabber = FreshFrameGrabber(file)
        return try {
            grabber.setVideoOption("hwaccel", "auto")
            grabber.pixelFormat = AV_PIX_FMT_BGR24
            grabber.start()
            val duration = grabber.lengthMs(file) * 1000L
            // The author's cover (ME-287) is taken as it is, dark or not; else the guess.
            val chosen = AppliedVideoEdits.editOf(file)?.coverMs
            val timestamps = chosen?.let { listOf(it * 1000L) } ?: buildTimestampCandidates(duration)
            var selectedImage: VideoPosterFrame? = null
            val converter = Java2DFrameConverter()
            for (ts in timestamps) {
                val candidate = grabFrameAt(grabber, converter, ts, duration) ?: continue
                if (chosen != null || !isMonochrome(candidate.image)) {
                    selectedImage = candidate
                    break
                }
                // The converter reuses its full-size image; retain only a small fallback
                // so later candidates cannot pair new pixels with the old HDR metadata.
                if (selectedImage == null) selectedImage = candidate.copy(image = scaledVideoPoster(candidate.image))
            }
            // Every candidate past the end: a one-frame `.bik` (VF-001) still has a picture.
            if (selectedImage == null) selectedImage = grabFrameAt(grabber, converter, 0L, duration)
            // Upright before it is scaled and cached: JavaCV never applies the
            // display matrix, so a phone's portrait clip arrived on its side (VF-105).
            val turns = grabber.clockwiseTurns
            selectedImage?.let { saveVideoPoster(it, turns, previewFile, cacheKey, mode) }
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
    ): VideoPosterFrame? {
        if (duration !in 1..targetTs) {
            return grabber.grabImageAt(targetTs)?.let { grabber.posterFrame(it, converter) }
        }
        (0 until 15).forEach { _ ->
            val frame: Frame? = grabber.grabImage()
            if (frame != null && frame.image != null) {
                return grabber.posterFrame(frame, converter)
            }
        }
        return null
    }

}
