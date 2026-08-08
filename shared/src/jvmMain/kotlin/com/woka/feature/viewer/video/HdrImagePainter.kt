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
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** `vec2 scale` then two floats — declaration order is the packing order. */
private const val UNIFORM_BYTES = 16

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
    private val isWideGamut: Boolean = true
) : Painter() {

    private val effect by lazy { RuntimeEffect.makeForShader(HDR_SHADER) }

    override val intrinsicSize: Size
        get() = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    override fun DrawScope.onDraw() {
        val uniforms = ByteBuffer.allocate(UNIFORM_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(intrinsicSize.width / size.width)
            .putFloat(intrinsicSize.height / size.height)
            .putFloat(transferCode())
            .putFloat(if (isWideGamut) 1f else 0f)
            .array()

        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val shader = effect.makeShader(
            Data.makeFromBytes(uniforms),
            arrayOf(image.makeShader()),
            null
        )
        drawRect(brush = ShaderBrush(shader.asComposeShader()), size = size)
    }

    /** Floats rather than an int uniform: every Skia build takes these. */
    private fun transferCode(): Float = when (transfer) {
        TransferKind.SDR -> 0f
        TransferKind.PQ -> 1f
        TransferKind.HLG -> 2f
    }
}
