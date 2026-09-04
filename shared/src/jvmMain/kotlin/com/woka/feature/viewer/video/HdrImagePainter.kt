package com.woka.feature.viewer.video

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.woka.core.data.TransferKind
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** `vec2 scale` then three floats — declaration order is the packing order. */
private const val UNIFORM_BYTES = 20

/**
 * Draws a picture that cannot go straight to an sRGB display, doing the right
 * thing for **which** kind of picture it is.
 *
 * [transfer] and [isWideGamut] are passed rather than a "needs tone mapping"
 * boolean because the two are separate operations: an HDR transfer wants a tone
 * curve, BT.2020 primaries want a matrix, and a wide-gamut SDR photograph wants
 * the second without the first. See [HDR_SHADER].
 *
 * It serves stills as well as video frames now. The maths was tuned against 4K
 * HDR10 video and has never been checked against a photograph — if a still ever
 * wants different tuning, the operator, the peak and the desaturation should
 * become uniforms rather than the shader being forked.
 */
class HdrImagePainter(
    private val bitmap: ImageBitmap,
    private val transfer: TransferKind = TransferKind.PQ,
    private val isWideGamut: Boolean = true,
    /**
     * Filters the image shader instead of taking Skia's default, which is
     * nearest-neighbour. A decoded frame is drawn at roughly its own size and does
     * not care; the ambient fill magnifies a 64-pixel-wide sample across a whole
     * panel, where nearest turns it into hard-edged blocks.
     */
    private val smooth: Boolean = false,
    /**
     * What this picture peaks at, in units of SDR white — the divisor the tone curve is
     * normalised by (ME-120).
     *
     * Defaulted to [ASSUMED_WHITE_POINT] so a caller with nothing to say keeps the
     * behaviour it had, rather than silently acquiring a new one. A still measures its
     * own; a video frame cannot, and says so by leaving this alone.
     */
    private val whitePoint: Float = ASSUMED_WHITE_POINT
) : Painter() {

    override val intrinsicSize: Size
        get() = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    /**
     * Every object made here is a native one, and none of them used to be closed.
     * The runtime-effect shader holds the image shader, which holds the image,
     * which shares the bitmap's pixel storage — so every draw left behind a chain
     * pinning a whole frame of pixels, and it outlived the frame's own wrapper.
     * Twenty-four of those a second is what turned an eight-deep frame queue into
     * gigabytes.
     *
     * Closing the handles the moment `drawRect` returns is safe: the canvas took
     * its own references while recording, and they outlive ours.
     */
    override fun DrawScope.onDraw() {
        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val imageShader = if (smooth) {
            image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null)
        } else {
            image.makeShader()
        }
        val data = Data.makeFromBytes(uniforms())
        val shader = HDR_EFFECT.makeShader(data, arrayOf(imageShader), null)
        try {
            drawRect(brush = ShaderBrush(shader.asComposeShader()), size = size)
        } finally {
            shader.close()
            data.close()
            imageShader.close()
            image.close()
        }
    }

    private fun DrawScope.uniforms(): ByteArray =
        ByteBuffer.allocate(UNIFORM_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(intrinsicSize.width / size.width)
            .putFloat(intrinsicSize.height / size.height)
            .putFloat(transfer.shaderCode())
            .putFloat(if (isWideGamut) 1f else 0f)
            .putFloat(whitePoint)
            .array()
}
