package com.woka.feature.viewer.video

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.data.StillColorInfo
import org.jetbrains.skia.Surface

/**
 * [bitmap] carried into ordinary sRGB, once, so nothing downstream has to know it was
 * ever HDR (ME-177).
 *
 * **Why this exists at all.** [HdrImagePainter] maps at *draw* time, which serves the
 * viewer and serves video — a frame is drawn once and replaced. It cannot serve the
 * editor: the editor draws through `RasterRecipe`, and a tone map inside that fold would
 * have to sit either before the edits (so the two surfaces compute different Adjust
 * values) or after them (so markup is mapped in one place and not the other). Mapping
 * here, before either surface sees the picture, removes the choice instead of making it.
 *
 * **The painter is reused rather than the effect.** `HDR_SHADER`'s uniforms are packed in
 * declaration order and its `scale` compensates for the draw size; both are already
 * written correctly once, in [HdrImagePainter], and a second copy of that packing is a
 * defect waiting for the next uniform. Drawn at exactly the bitmap's size, `scale` comes
 * out 1:1 and the painter does the rest.
 *
 * The white point is measured here for the same reason ME-120 measured it for a still:
 * the pixels are in hand, and there is nothing per-frame about a photograph.
 */
internal fun toneMapped(bitmap: ImageBitmap, colour: StillColorInfo): ImageBitmap {
    val painter = HdrImagePainter(
        bitmap = bitmap,
        transfer = colour.transfer,
        isWideGamut = colour.isWideGamut,
        whitePoint = measuredWhitePoint(bitmap, colour.transfer)
    )
    val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    return Surface.makeRasterN32Premul(bitmap.width, bitmap.height).use { surface ->
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            surface.canvas.asComposeCanvas(),
            size
        ) {
            with(painter) { draw(size) }
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
}
