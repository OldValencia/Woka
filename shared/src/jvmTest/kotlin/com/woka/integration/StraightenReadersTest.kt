package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.straighten.StraightenTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.StillPreview
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A straightened photo through the readers that draw it (RD-205): the viewer at native size
 * and the grid's small preview, both through `renderAtNativeScale`, the save's own path.
 *
 * The 640 × 480 fixture at 10° keeps 526 × 394 — `k = cos 10° + 4/3 · sin 10° ≈ 1.2163`,
 * floored — and its corners must be picture, not the transparency around a turned bitmap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StraightenReadersTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun levelled() = EditDocument(
        ops = listOf(ToolOp(StraightenTool.manifest.id, ParamSet().with(StraightenTool.Angle, 10f)))
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-straighten").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(StraightenTool), SettingsRepositoryImpl()))
            )
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the viewer draws the levelled picture at its real size with filled corners`() {
        onPhoto { file ->
            assertEquals(640, ImageLoader.load(file).width, "the fixture is not 640 wide")
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.write(file, levelled()).isSuccess)
            val picture = ImageLoader.load(file)

            assertEquals(526, picture.width)
            assertEquals(394, picture.height)
            val pixels = picture.toPixelMap()
            val right = picture.width - 1
            val bottom = picture.height - 1
            listOf(0 to 0, right to 0, 0 to bottom, right to bottom).forEach { (x, y) ->
                assertEquals(1f, pixels[x, y].alpha, 0.01f, "corner ($x, $y) is not opaque")
            }
        }
    }

    @Test
    fun `the preview keeps the levelled shape`() {
        onPhoto { file ->
            StillPreview.load(file)
            ImageBitmapCache.remove(ImageBitmapCache.previewKey(file.absolutePath))

            assertTrue(EditRecipe.write(file, levelled()).isSuccess)
            val after = StillPreview.load(file)

            val got = after.width.toFloat() / after.height
            assertTrue(abs(526f / 394f - got) < 0.05f, "the preview's shape is $got")
        }
    }
}
