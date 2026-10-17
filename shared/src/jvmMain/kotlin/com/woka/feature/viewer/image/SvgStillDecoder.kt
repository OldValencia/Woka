package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The longest side an SVG is drawn at. A vector has no native size worth the name:
 * an icon declares 20×20, and drawn at that the viewer's Fit would stretch twenty
 * pixels across the screen. Drawn at this and scaled down, it stays sharp at Fit
 * and up to about 2× zoom on a 1080p panel.
 */
private const val RASTER_LONG_SIDE = 2048f

/** What a file that declares neither a size nor a view box is assumed to be. */
private const val FALLBACK_SIDE = 512f

/**
 * SVG, through skia's own `SVGDOM` — already on the classpath with Compose, so no
 * dependency was added (2026-10-17). One picture, rasterised at [RASTER_LONG_SIDE]
 * on its longest side in the file's own aspect, transparent where the file is.
 *
 * [nativeSize] answers with that raster's size, not the declared one, because the
 * viewer's "Actual size" and zoom percentages are defined against the bitmap it is
 * handed; claiming 20×20 for a 2048-pixel bitmap would make 100 % a postage stamp.
 */
internal object SvgStillDecoder : StillDecoder {

    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    override fun nativeSize(file: File, frameIndex: Int): IntSize = Geometry.of(dom(file)).raster

    /**
     * The document is laid out at its **own** size and the canvas is scaled, rather
     * than the container set to the raster's size: a file with a width and height
     * but no view box draws in its own units and ignores a larger container, which
     * left a 2048-pixel bitmap with a 20-pixel drawing in its corner (2026-10-17,
     * caught by `SvgStillDecoderTest`).
     */
    override fun frame(file: File, frameIndex: Int): ImageBitmap {
        val dom = dom(file)
        val geometry = Geometry.of(dom)
        val surface = Surface.makeRasterN32Premul(geometry.raster.width, geometry.raster.height)
        dom.setContainerSize(geometry.width, geometry.height)
        surface.canvas.scale(geometry.scale, geometry.scale)
        dom.render(surface.canvas)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    private fun dom(file: File): SVGDOM = SVGDOM(Data.makeFromBytes(file.readBytes()))
}

/** The document's own size, and the scale that takes its longest side to [RASTER_LONG_SIDE]. */
private class Geometry(val width: Float, val height: Float) {
    val scale = RASTER_LONG_SIDE / max(width, height)
    val raster = IntSize(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1)
    )

    companion object {
        fun of(dom: SVGDOM): Geometry {
            val root = dom.root ?: error("not an SVG document")
            val declared = root.getIntrinsicSize(SVGLengthContext(FALLBACK_SIDE, FALLBACK_SIDE))
            val box = root.viewBox
            val width = declared.x.takeIf { it > 0f } ?: box?.width?.takeIf { it > 0f } ?: FALLBACK_SIDE
            val height = declared.y.takeIf { it > 0f } ?: box?.height?.takeIf { it > 0f } ?: FALLBACK_SIDE
            return Geometry(width, height)
        }
    }
}
