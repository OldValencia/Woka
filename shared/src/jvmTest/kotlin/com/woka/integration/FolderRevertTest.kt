package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.folder.revertEdit
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grid's road to Revert (ME-107, step 5).
 *
 * The removal itself is `RevertTest`'s; what this holds is that the menu entry reaches
 * it — through the Koin factory rather than through a copy of the logic — so a
 * photograph put back from the grid is put back exactly as one put back from the
 * editor. The confirmation in front of it is the dialog's, not this function's.
 */
class FolderRevertTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-folder-revert").toFile()
        try {
            // Built so the fold has a tool to resolve `transform` with, exactly as the
            // running app's registry does.
            EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `reverting from the grid leaves the file as it was shot`() {
        onPhoto { file ->
            val asShot = file.readBytes()
            assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)
            assertTrue(EditRecipe.carries(file))

            runBlocking { revertEdit(file.absolutePath) }

            assertFalse(EditRecipe.carries(file))
            assertContentEquals(asShot, file.readBytes())
        }
    }

    /** A file with nothing in it is not rewritten — the mtime is a cache key. */
    @Test
    fun `reverting an untouched photograph writes nothing`() {
        onPhoto { file ->
            val stamp = file.lastModified()

            runBlocking { revertEdit(file.absolutePath) }

            assertEquals(stamp, file.lastModified())
        }
    }
}
