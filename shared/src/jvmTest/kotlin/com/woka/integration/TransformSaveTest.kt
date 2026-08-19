package com.woka.integration

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.shell.ParamWriter
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_WEBP
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The claim this ticket exists to make: a tool that is nothing but data reaches the
 * file on disk.
 *
 * Everything in between — the writer producing an op, the fold ordering and composing
 * it, the frame being derived rather than declared, the save rendering at native scale
 * — is exercised by these three tests and by nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransformSaveTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * A **bucket B** picture, so the save re-encodes and the rotation has to reach the
     * pixels to be seen at all (§5.1). On a `png` the same edit would be recorded as a
     * recipe, and a test asserting on the saved file's dimensions would be asserting
     * that `EditRecipe` round-trips — which `EditRecipeTest` already does.
     */
    private fun onPhoto(block: (EditorHarness, File, ImageEditorEngine) -> Unit) {
        val dir = Files.createTempDirectory("woka-transform").toFile()
        try {
            val file = File(dir, "photo.webp")
            file.writeBytes(SampleMedia.file(STILL_WEBP).readBytes())
            onEditor(listOf(TransformTool)) { harness ->
                val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())
                block(
                    harness,
                    file,
                    ImageEditorEngine(file, 0, registry, EditCapability.Destructive)
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A fresh writer per press, exactly as a recomposition would build one. */
    private fun EditorHarness.press(action: ActionParam) {
        ParamWriter(session, TransformTool, session.document.value).edit(action, true)
    }

    @Test
    fun `a rotate committed through the writer swaps the saved dimensions`() {
        onPhoto { harness, file, engine ->
            harness.press(TransformTool.RotateRight)

            val document = harness.session.document.value
            assertEquals(IntSize(480, 640), engine.drawnSize(document), "the frame did not turn")

            val result = runBlocking { engine.save(document) }
            assertTrue(result is EditSaveResult.Saved, "got $result")

            val saved = ImageLoader.load(file)
            assertEquals(480, saved.width)
            assertEquals(640, saved.height)
        }
    }

    @Test
    fun `four rotations come back to where they started`() {
        onPhoto { harness, _, engine ->
            repeat(4) { harness.press(TransformTool.RotateRight) }

            assertEquals(4, harness.session.document.value.ops.size, "Repeating did not append")
            assertEquals(IntSize(640, 480), engine.drawnSize(harness.session.document.value))
        }
    }

    @Test
    fun `a rotate then a flip are two ops carrying one flag each`() {
        onPhoto { harness, _, _ ->
            harness.press(TransformTool.RotateRight)
            harness.press(TransformTool.FlipHorizontal)

            val ops = harness.session.document.value.ops
            assertEquals(2, ops.size)
            // The bug this guards: an action op built from the previous one carries
            // that one's flag forward, and the picture turns twice.
            assertTrue(ops[0].params[TransformTool.RotateRight])
            assertTrue(!ops[1].params[TransformTool.RotateRight], "the rotate leaked into the flip")
            assertTrue(ops[1].params[TransformTool.FlipHorizontal])
        }
    }
}
