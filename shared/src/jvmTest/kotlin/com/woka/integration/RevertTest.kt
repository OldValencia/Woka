package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.revertedToOriginal
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Revert: back to as shot, in one action (ME-107).
 *
 * The byte comparison is the whole promise. There is no backup store and there never
 * was one — the original pixels never left the file — so "reverted" has to mean the
 * file is the file again, not that it looks similar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevertTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun turn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-revert").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun engineOn(file: File) =
        ImageEditorEngine(file, 0, registry, EditCapability.Recorded)

    @Test
    fun `a reverted file is byte-identical to the one that was never edited`() {
        onPhoto { file ->
            val asShot = file.readBytes()
            val engine = engineOn(file)

            runBlocking { engine.save(EditLayers(turn())) }
            assertFalse(asShot.contentEquals(file.readBytes()), "the edit was never written")

            val result = runBlocking { engine.revert() }

            assertTrue(result is EditSaveResult.Saved)
            assertContentEquals(asShot, file.readBytes())
        }
    }

    @Test
    fun `reverting a file that carries nothing writes nothing at all`() {
        onPhoto { file ->
            val stamp = file.lastModified()

            val result = runBlocking { engineOn(file).revert() }

            assertEquals(EditSaveResult.NothingToDo, result)
            assertEquals(stamp, file.lastModified())
        }
    }

    /**
     * The half the session owns: after the file is clean, the editor must be clean
     * against it — not dirty against a document the file no longer carries.
     */
    @Test
    fun `the session comes back empty and clean`() {
        val session = EditorSession(MediaItem.Image(File("photo.jpg")), turn())
        try {
            assertTrue(session.canUndo.value)

            session.revertedToOriginal()

            assertTrue(session.document.value.isEmpty)
            assertFalse(session.isDirty.value, "clean against the file it now matches")
            assertFalse(session.canUndo.value, "there is nothing on disk left to undo to")
        } finally {
            session.close()
        }
    }
}
