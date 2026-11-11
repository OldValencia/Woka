package com.woka.feature.viewer.video.engine

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.data.HdrViewMode
import com.woka.core.data.StillColorInfo
import com.woka.core.data.applyTo
import com.woka.core.data.replaceAtomically
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.encodeWebp
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.HdrImagePainter
import com.woka.feature.viewer.video.engine.decode.lastPicture
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.probe.rotatedClockwise
import com.woka.feature.viewer.video.engine.probe.toColour
import com.woka.feature.viewer.video.engine.probe.withSceneOf
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.min

/** A candidate's metadata copied before the grabber replaces its native frame. */
internal data class VideoPosterFrame(val image: BufferedImage, val colour: StillColorInfo)

internal fun FFmpegFrameGrabber.posterFrame(frame: Frame, converter: Java2DFrameConverter): VideoPosterFrame {
    val picture = lastPicture ?: frame.opaque as? AVFrame
    val colour = readStreamHdrInfo().readFrom(picture).withSceneOf(picture).toColour()
    return VideoPosterFrame(converter.getBufferedImage(frame), colour)
}

/**
 * A static poster, mapped once before its WebP is cached (1.0.8, `hdr-video-posters.md`).
 * The old BGR-to-WebP path omitted the viewer's HDR pass, measured on P8.1 on 2026-11-10.
 * Only the <=400 px bitmap is mapped; no extra native-size surface or per-draw pass.
 * The source bitmap is private until mapping ends, so its native pixels can be closed here.
 * Clip edits stay outside this cache, after colour management, as in the playing frame.
 */
internal fun saveVideoPoster(
    frame: VideoPosterFrame,
    turns: Int,
    previewFile: File,
    cacheKey: String,
    mode: HdrViewMode
): ImageBitmap {
    val image = frame.image.rotatedClockwise(turns)
    val scaled = scaledVideoPoster(image)
    val raw = scaled.toComposeImageBitmap()
    val colour = mode.applyTo(frame.colour)
    val bitmap = if (colour.needsColorManagement) {
        try {
            mapVideoPoster(raw, colour)
        } finally {
            raw.asSkiaBitmap().close()
        }
    } else raw
    try {
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { it.encodeWebp() }
        replaceAtomically(previewFile, bytes)
    } catch (error: Exception) {
        bitmap.asSkiaBitmap().close()
        throw error
    }
    ImageBitmapCache[cacheKey] = bitmap
    return bitmap
}

/** An owned <=400 px copy; keeping the converter's image would retain changing pixels. */
internal fun scaledVideoPoster(image: BufferedImage): BufferedImage {
    val scale = min(400.0 / image.width, 400.0 / image.height).coerceAtMost(1.0)
    val w = (image.width * scale).toInt().coerceAtLeast(1)
    val h = (image.height * scale).toInt().coerceAtLeast(1)
    val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(image, 0, 0, w, h, null)
    } finally {
        graphics.dispose()
    }
    return scaled
}

private fun mapVideoPoster(bitmap: ImageBitmap, colour: StillColorInfo): ImageBitmap {
    val painter = HdrImagePainter(
        bitmap,
        transfer = colour.transfer,
        isWideGamut = colour.isWideGamut,
        whitePoint = colour.whitePoint ?: ASSUMED_WHITE_POINT
    )
    val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    return Surface.makeRasterN32Premul(bitmap.width, bitmap.height).use { surface ->
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), size) {
            with(painter) { draw(size) }
        }
        surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
    }
}
