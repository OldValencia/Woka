package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.utils.drawnImageOf
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.withTempStorageRoot
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Copy` hands over the **picture**, drawn (2026-08-19).
 *
 * Asserted one step short of the system clipboard, deliberately: the transferable is
 * everything the action decides, and a test that called `setContents` would throw away
 * whatever the person running it had copied.
 *
 * A quarter turn is the assertion, because it is the one edit whose result is a pair of
 * integers — an edited landscape photograph leaves here as a portrait one. That is also
 * the whole point of this action: inside the file the edit is invisible to every other
 * program, and this is how it gets out (§5.3).
 */
class CopyImageTest {

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-copy").toFile()
        try {
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl()))
            )
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun Any.size() = (this as Image).let { it.getWidth(null) to it.getHeight(null) }

    @Test
    fun `an untouched photograph is copied at its own size`() {
        onPhoto { file ->
            val contents = drawnImageOf(file.absolutePath, 0)

            assertTrue(contents.isDataFlavorSupported(DataFlavor.imageFlavor))
            assertEquals(640 to 480, contents.getTransferData(DataFlavor.imageFlavor).size())
        }
    }

    @Test
    fun `an edited photograph is copied as it is drawn, not as it is stored`() {
        onPhoto { file ->
            assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            val copied = drawnImageOf(file.absolutePath, 0).getTransferData(DataFlavor.imageFlavor)

            assertEquals(480 to 640, copied.size(), "the clipboard got the unedited picture")
        }
    }
}
