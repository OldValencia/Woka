package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.image.SvgStillDecoder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SVG through skia's `SVGDOM` (2026-10-17): a 20×10 vector comes out as a 2048×1024
 * raster in its own aspect, the size the viewer is told matches the bitmap, and what
 * the file draws is there — not a transparent rectangle of the right size.
 */
class SvgStillDecoderTest {

    private inline fun <T> withSvg(svg: String, block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-svg").toFile()
        return try {
            block(File(dir, "icon.svg").also { it.writeText(svg) })
        } finally {
            dir.deleteRecursively()
        }
    }

    private val wide = """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="10">
        <rect width="20" height="10" fill="#ff0000"/></svg>"""

    @Test
    fun `a declared size is drawn large in its own aspect`() = withSvg(wide) { file ->
        val bitmap = SvgStillDecoder.frame(file, 0)
        assertEquals(2048, bitmap.width)
        assertEquals(1024, bitmap.height)
        assertEquals(IntSize(2048, 1024), SvgStillDecoder.nativeSize(file, 0))
    }

    @Test
    fun `what the file draws is in the bitmap`() = withSvg(wide) { file ->
        val centre = SvgStillDecoder.frame(file, 0).toPixelMap()[1024, 512]
        assertTrue(centre.red > 0.9f && centre.green < 0.1f && centre.alpha > 0.9f, "centre was $centre")
    }

    @Test
    fun `a view box alone gives the aspect`() = withSvg(
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 40"><circle cx="5" cy="20" r="4"/></svg>"""
    ) { file ->
        assertEquals(IntSize(512, 2048), SvgStillDecoder.nativeSize(file, 0))
    }
}
