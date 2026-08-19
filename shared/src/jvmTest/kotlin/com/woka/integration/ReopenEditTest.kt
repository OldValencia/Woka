package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.ImageLoader
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
import kotlin.test.assertTrue

/**
 * Two sittings on one photograph (ME-134).
 *
 * The ticket exists because the second `Done` **replaced** the first one's edit: the
 * session started from an empty document whatever the file held, so it wrote only what
 * that sitting did. Driven through a real engine and a real session rather than through
 * `EditRecipe`, because the loss was in what the shell handed the session, not in the
 * carrier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReopenEditTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun turn() =
        ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))

    private fun onPhotos(block: (File, File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-reopen").toFile()
        try {
            AppliedStillEdits.install(RecipeStillEdits(registry))
            block(photo(dir, "twice.jpg"), photo(dir, "once.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun photo(dir: File, name: String) = File(dir, name).apply {
        writeBytes(SampleMedia.file(STILL_JPG).readBytes())
    }

    /**
     * One sitting: open the file as the shell does — on what it already carries — apply
     * [turns] quarter turns and write.
     */
    private fun edit(file: File, turns: Int): EditSaveResult {
        val engine = ImageEditorEngine(file, 0, registry, EditCapability.Recorded)
        val session = EditorSession(MediaItem.Image(file), engine.storedDocument())
        return try {
            repeat(turns) {
                session.preview(turn())
                session.commit(ToolCardinality.Repeating)
            }
            runBlocking { engine.save(session.document.value) }
        } finally {
            session.close()
        }
    }

    @Test
    fun `a second sitting continues the first rather than replacing it`() {
        onPhotos { file, _ ->
            assertTrue(edit(file, turns = 1) is EditSaveResult.Saved)
            assertEquals(1, EditRecipe.read(file)?.ops?.size, "the first sitting is missing")

            assertTrue(edit(file, turns = 1) is EditSaveResult.Saved)

            val ops = EditRecipe.read(file)?.ops.orEmpty()
            assertEquals(2, ops.size, "the second Done replaced the first sitting")
            assertTrue(ops.all { it.params[TransformTool.RotateRight] }, "a turn was lost")
        }
    }

    /**
     * The property the whole recipe model exists for: the pixels are the original's
     * every time, so two sittings and one sitting doing the same work are the same
     * picture — no second compression, no generational loss.
     */
    @Test
    fun `two sittings decode identically to one sitting doing both turns`() {
        onPhotos { twice, once ->
            edit(twice, turns = 1)
            edit(twice, turns = 1)
            edit(once, turns = 2)

            val half = ImageLoader.load(twice)
            assertEquals(640, half.width, "a half turn must not swap the axes")
            val flat = ImageLoader.load(once).toPixelMap().buffer
            assertContentEquals(flat, half.toPixelMap().buffer)
        }
    }
}
