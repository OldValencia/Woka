package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.encodePng
import com.woka.feature.editor.engine.image.EditorProxy
import com.woka.feature.editor.engine.image.PROXY_MAX_PX
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_PNG
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The editing proxy, and the guard rail it carries.
 *
 * The size assertions are ordinary. The cache one is the point: §10's "keep a huge
 * source out of the shared cache" is *implemented by* this object never writing to
 * it, and a guarantee of absence survives only if something asserts the absence.
 *
 * The oversized source is **synthesised** rather than committed. The corpus tops out
 * at 640×480 — deliberately, it is 3.3 MB of fixtures — so the downscale path had no
 * file to exercise it, and a test that never reaches the branch it is named for is
 * worse than none.
 */
class EditorProxyTest {

    @Test
    fun `a source within the cap keeps its dimensions`() {
        val proxy = EditorProxy.load(SampleMedia.file(STILL_PNG))

        assertEquals(640, proxy.width)
        assertEquals(480, proxy.height)
    }

    @Test
    fun `an oversized source is capped on its long edge with the aspect kept`() {
        withGeneratedImage(width = 4000, height = 1000) { file ->
            val proxy = EditorProxy.load(file)

            assertEquals(PROXY_MAX_PX, proxy.width, "the long edge is the one capped")
            assertEquals(640, proxy.height, "4000:1000 is 4:1, and 2560/4 is 640")
        }
    }

    @Test
    fun `a tall source is capped on its long edge too`() {
        withGeneratedImage(width = 1000, height = 4000) { file ->
            val proxy = EditorProxy.load(file)

            assertEquals(640, proxy.width)
            assertEquals(PROXY_MAX_PX, proxy.height)
        }
    }

    @Test
    fun `the proxy never lands in the shared bitmap cache`() {
        withGeneratedImage(width = 4000, height = 1000) { file ->
            val before = ImageBitmapCache.retainedBytes

            EditorProxy.load(file)

            // Not "roughly the same" — exactly. One leaked entry of a huge source
            // evicts every thumbnail and poster the app is holding (§10, ME-010).
            assertEquals(before, ImageBitmapCache.retainedBytes)
        }
    }

    @Test
    fun `a tiled HEIF builds through the capped decode without touching the cache`() {
        val before = ImageBitmapCache.retainedBytes

        val proxy = EditorProxy.load(SampleMedia.file(BURST_HEIC), frameIndex = 0)

        assertEquals(320, proxy.width)
        assertEquals(240, proxy.height)
        assertEquals(before, ImageBitmapCache.retainedBytes)
        assertTrue(proxy.height > 0)
    }
}

/** A PNG of the given size on disk, deleted however the block ends. */
private fun withGeneratedImage(width: Int, height: Int, block: (File) -> Unit) {
    val dir = Files.createTempDirectory("woka-proxy").toFile()
    try {
        val file = File(dir, "generated.png")
        file.writeBytes(solidImage(width, height).encodePng())
        block(file)
    } finally {
        dir.deleteRecursively()
    }
}

private fun solidImage(width: Int, height: Int): ImageBitmap =
    Surface.makeRasterN32Premul(width, height).use { surface ->
        surface.canvas.drawRect(
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Paint().apply { color = Color.CYAN }
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
