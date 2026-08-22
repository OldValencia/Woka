package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
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
 * A crop, through the readers that draw it (ME-109).
 *
 * **This is the pair of assertions step 2 exists for.** A crop rect is in source
 * pixels, and `StillPreview` decodes small — so the two readers below are handed
 * bitmaps of very different sizes for the same file, and before the fold moved to
 * native the preview would have cut a region several times too large and landed off
 * the picture entirely.
 *
 * The preview is asserted by **ratio, not by size**: it is capped to a preview
 * dimension, so its absolute numbers are the cap's business and only its shape is the
 * crop's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CropReadersTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** A tall region out of the 640×480 fixture — a shape nothing else could produce. */
    private val rect = CropRect(left = 160, top = 40, width = 200, height = 400)

    private fun cropDocument() = EditDocument(
        ops = listOf(ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect)))
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-crop").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(CropTool), SettingsRepositoryImpl()))
            )
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the viewer draws exactly the region that was kept`() {
        onPhoto { file ->
            assertEquals(640, ImageLoader.load(file).width, "the fixture is not 640 wide")
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.write(file, cropDocument()).isSuccess)
            val cropped = ImageLoader.load(file)

            assertEquals(rect.width, cropped.width)
            assertEquals(rect.height, cropped.height)
        }
    }

    /**
     * The reader that decodes small. Its bitmap is nothing like the source's size, so
     * a recipe folded against *it* would cut somewhere else — this is the assertion
     * that fails if `RasterRecipe.scaledBy` is ever dropped.
     */
    @Test
    fun `the preview keeps the crop's shape although it decodes small`() {
        onPhoto { file ->
            val before = StillPreview.load(file)
            assertTrue(before.width > before.height, "the fixture is not landscape")
            ImageBitmapCache.remove(ImageBitmapCache.previewKey(file.absolutePath))

            assertTrue(EditRecipe.write(file, cropDocument()).isSuccess)
            val after = StillPreview.load(file)

            assertTrue(after.height > after.width, "the preview kept the old orientation")
            val wanted = rect.width.toFloat() / rect.height
            val got = after.width.toFloat() / after.height
            assertTrue(
                abs(wanted - got) < 0.05f,
                "the preview's shape is $got, the crop's is $wanted"
            )
        }
    }
}
