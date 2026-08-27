package com.woka.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.LocalCanvasScale
import com.woka.feature.editor.engine.image.EditorPlayback
import com.woka.feature.editor.engine.image.PROXY_MAX_PX
import com.woka.feature.editor.engine.image.rememberCanvasSource
import com.woka.support.onUi
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wider than [PROXY_MAX_PX], so the proxy is genuinely a reduction — 2560 × 85. */
private const val NATIVE_WIDTH = 3000
private const val NATIVE_HEIGHT = 100

/**
 * Which bitmap the editing canvas draws from, at Fit and magnified (ME-116).
 *
 * Asserted on `rememberCanvasSource` rather than on pixels: once `RasterSurface` has
 * drawn, the two bitmaps differ only in sampling, and a pixel test would be asserting
 * that Skia interpolates — which is not this ticket's claim. The claim is *which
 * bitmap was chosen*, and that is a width.
 *
 * A fresh file per test, in a directory of its own: both caches are keyed by absolute
 * path (`MediaCopies`), and these assertions are about what was decoded, not about
 * what a previous test left behind.
 */
@OptIn(ExperimentalTestApi::class)
class EditorZoomFidelityUiTest {

    private val sourceSize = IntSize(NATIVE_WIDTH, NATIVE_HEIGHT)

    /**
     * Generated rather than committed: it only has to be wider than the proxy cap, and
     * a solid 3000 × 100 PNG is a few hundred bytes. `TYPE_INT_RGB` starts black, so
     * nothing has to be drawn into it.
     */
    private fun wideSource(): File {
        val dir = Files.createTempDirectory("woka-native").toFile()
        val file = File(dir, "wide.png")
        val image = BufferedImage(NATIVE_WIDTH, NATIVE_HEIGHT, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "png", file)
        return file
    }

    @Test
    fun `at fit the canvas draws the proxy`() = onUi {
        val file = wideSource()
        var width = 0
        setContent {
            // Fit for a 3000 px picture in an ordinary window, and far below the
            // proxy's own 0.85 of source — there is nothing here to sharpen.
            CompositionLocalProvider(LocalCanvasScale provides 0.5f) {
                width = rememberCanvasSource(file, 0, EditorPlayback(), sourceSize).width
            }
        }
        waitForIdle()
        assertEquals(PROXY_MAX_PX, width, "the canvas asked for a native decode it did not need")
    }

    @Test
    fun `zoomed past the proxy the canvas draws the native bitmap`() = onUi {
        val file = wideSource()
        var width = 0
        setContent {
            CompositionLocalProvider(LocalCanvasScale provides 2f) {
                width = rememberCanvasSource(file, 0, EditorPlayback(), sourceSize).width
            }
        }
        // The decode runs off the composition's dispatcher, so idling is not enough.
        waitUntil(timeoutMillis = 10_000) { width == NATIVE_WIDTH }
    }

    /**
     * The regression that matters. `EditorBottomPanel` is a sibling of
     * `EditorCanvasHost`, so a filter-strip thumbnail composes with the local at its
     * `0f` default — eight of them each holding a native decode is the eviction storm
     * the proxy exists to avoid, and it has to be refused by arithmetic rather than by
     * a list of surfaces to exclude.
     */
    @Test
    fun `a surface outside the canvas host never asks for a native frame`() = onUi {
        val file = wideSource()
        val before = ImageBitmapCache.retainedBytes
        var width = 0
        setContent {
            width = rememberCanvasSource(file, 0, EditorPlayback(), sourceSize).width
        }
        waitForIdle()
        assertEquals(PROXY_MAX_PX, width, "a thumbnail read a canvas scale it was never given")
        assertTrue(
            ImageBitmapCache.retainedBytes <= before,
            "the editor put a decode into the shared cache it is meant to stay out of"
        )
    }

    /**
     * Zooming in and back out leaves `ImageBitmapCache` exactly as it was found — the
     * guard rail §10 asks for, asserted here rather than in `ImageCacheEvictionTest`
     * because only a composition can make the editor decode anything.
     */
    @Test
    fun `holding a native frame never grows the shared cache`() = onUi {
        val file = wideSource()
        val before = ImageBitmapCache.retainedBytes
        var width = 0
        var scale by mutableStateOf(2f)
        setContent {
            CompositionLocalProvider(LocalCanvasScale provides scale) {
                width = rememberCanvasSource(file, 0, EditorPlayback(), sourceSize).width
            }
        }
        waitUntil(timeoutMillis = 10_000) { width == NATIVE_WIDTH }

        scale = 0.5f
        waitUntil(timeoutMillis = 10_000) { width == PROXY_MAX_PX }
        assertEquals(
            before,
            ImageBitmapCache.retainedBytes,
            "zooming the editor changed what the shared cache holds"
        )
    }
}
