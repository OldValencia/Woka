package com.woka.feature.viewer.video.engine.probe

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Clockwise quarter turns for a display matrix's rotation (VF-105). FFmpeg reports
 * the angle counter-clockwise, as `ffprobe` prints it: a phone's portrait clip says
 * 90 and is shown with `transpose=cclock`, three clockwise turns — matched against
 * the CLI's own autorotate on 90, 180 and −90 (2026-11-01). NaN, no matrix, is 0.
 */
internal fun clockwiseQuarterTurns(displayRotation: Double): Int {
    if (displayRotation.isNaN()) return 0
    val clockwise = (-displayRotation.roundToInt()).mod(360)
    return (clockwise + 45) / 90 % 4
}

/** The started grabber's video stream, in [clockwiseQuarterTurns]. */
internal val FFmpegFrameGrabber.clockwiseTurns: Int
    get() = clockwiseQuarterTurns(displayRotation)

/** Odd turns swap a picture's sides. */
internal fun swapsSides(turns: Int): Boolean = turns % 2 == 1

/** One preview picture turned upright — the hover reel and the seek preview. */
internal fun ImageBitmap.rotatedClockwise(turns: Int): ImageBitmap {
    if (turns == 0) return this
    val outWidth = if (swapsSides(turns)) height else width
    val outHeight = if (swapsSides(turns)) width else height
    val surface = Surface.makeRasterN32Premul(outWidth, outHeight)
    surface.use {
        val canvas = it.canvas
        canvas.translate(outWidth / 2f, outHeight / 2f)
        canvas.rotate(90f * turns)
        Image.makeFromBitmap(asSkiaBitmap()).use { image ->
            canvas.drawImage(image, -width / 2f, -height / 2f)
        }
        return it.makeImageSnapshot().toComposeImageBitmap()
    }
}

/** The poster turned upright, before it is scaled and cached. */
internal fun BufferedImage.rotatedClockwise(turns: Int): BufferedImage {
    if (turns == 0) return this
    val outWidth = if (swapsSides(turns)) height else width
    val outHeight = if (swapsSides(turns)) width else height
    val turned = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB)
    val transform = AffineTransform()
    transform.translate(outWidth / 2.0, outHeight / 2.0)
    transform.quadrantRotate(turns)
    transform.translate(-width / 2.0, -height / 2.0)
    turned.createGraphics().apply {
        drawImage(this@rotatedClockwise, transform, null)
        dispose()
    }
    return turned
}
