package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.utils.drawnImageOf
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * `Copy` on a GIF hands over a **file**, never a picture, and an edited one is baked frame
 * by frame into a new GIF (2026-11-21): the clipboard's picture is one frame, so a chat
 * would receive a still where the person copied an animation.
 *
 * Asserted one step short of the system clipboard, as `CopyImageTest` is. The fixture is
 * 320×240, eight frames of 120 ms, looping forever; a quarter turn makes the edit a pair
 * of integers.
 */
class CopyGifTest {

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onGif(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-copy-gif").toFile()
        try {
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl()))
            )
            val file = File(dir, "anim.gif")
            file.writeBytes(SampleMedia.file(ANIM_GIF).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an untouched GIF goes over as its own file and nothing else`() {
        onGif { file ->
            val contents = drawnImageOf(file.absolutePath, 0)

            assertContentEquals(arrayOf(DataFlavor.javaFileListFlavor), contents.transferDataFlavors)
            assertFalse(contents.isDataFlavorSupported(DataFlavor.imageFlavor))
            assertEquals(listOf(file), contents.getTransferData(DataFlavor.javaFileListFlavor))
        }
    }

    @Test
    fun `an edited GIF is baked into a new animated GIF`() {
        onGif { file ->
            assertEquals(true, EditRecipe.write(file, quarterTurn()).isSuccess)
            val before = file.readBytes()

            val contents = drawnImageOf(file.absolutePath, 0)
            val copied = (contents.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).single() as File
            try {
                assertFalse(contents.isDataFlavorSupported(DataFlavor.imageFlavor))
                assertEquals("anim.gif", copied.name)
                val codec = Codec.makeFromData(Data.makeFromBytes(copied.readBytes()))
                assertEquals(240 to 320, codec.width to codec.height)
                assertEquals(List(8) { 120 }, codec.framesInfo.map { it.duration })
                assertEquals(-1, codec.repetitionCount)
                assertFalse(EditRecipe.carries(copied))
                assertContentEquals(before, file.readBytes())
            } finally {
                copied.delete()
                copied.parentFile.delete()
            }
        }
    }
}
