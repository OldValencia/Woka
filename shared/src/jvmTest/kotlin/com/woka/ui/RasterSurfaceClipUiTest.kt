package com.woka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterSurface
import com.woka.support.onUi
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The canvas must not draw outside the frame the recipe chose (2026-08-22).
 *
 * **A crop is the first operation whose output is smaller than what it draws.** The
 * surface's box is `recipe.outputSize`, the picture inside it is the *source*, and a
 * Compose `Canvas` does not clip — so the part a crop discards went on being painted
 * outside the box. In the editor that looked like a picture wider than the crop applied
 * to it, with the whole of it visible once zoomed out; every reader that goes through a
 * Skia surface was unaffected, because that surface is physically the output's size.
 */
@OptIn(ExperimentalTestApi::class)
class RasterSurfaceClipUiTest {

    /** A solid red source, larger than the frame the recipe will keep. */
    private fun redSource(size: Int): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(size, size, ColorAlphaType.PREMUL))
        bitmap.erase(0xFFFF0000.toInt())
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    @Test
    fun `nothing is drawn outside the recipe's frame`() {
        val source = 200
        val kept = 60

        onUi {
            setContent {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White)
                        .testTag("clip-probe"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(60.dp)) {
                        RasterSurface(
                            source = redSource(source),
                            sourceSize = IntSize(source, source),
                            recipe = RasterRecipe(
                                // The crop's own translation: keep a middle square.
                                matrix = Matrix().apply { translate(-70f, -70f) },
                                outputSize = IntSize(kept, kept),
                                colorFilter = null,
                                paints = emptyList(),
                                skipped = emptyList()
                            )
                        )
                    }
                }
            }
            waitForIdle()

            val shot = onNodeWithTag("clip-probe").captureToImage().toPixelMap()
            // The corner of the outer box is far outside the 60 dp frame in the middle.
            assertEquals(
                Color.White,
                shot[2, 2],
                "the surface painted outside the frame its recipe chose"
            )
        }
    }
}
