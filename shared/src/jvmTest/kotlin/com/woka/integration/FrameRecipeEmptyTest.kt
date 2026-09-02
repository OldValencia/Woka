package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * When a `Done` **removes** the recipe rather than writing one (§5.2).
 *
 * Its own class since 2026-09-02: `FrameRecipeTest` is about which frame an edit reaches,
 * and this is about the file no longer carrying anything — a different claim that grew
 * that file past the size mandate.
 */
class FrameRecipeEmptyTest {

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onBurst(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-empty").toFile()
        try {
            val file = File(dir, BURST_HEIC)
            file.writeBytes(SampleMedia.file(BURST_HEIC).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun save(file: File, frameIndex: Int, document: EditDocument, scope: EditScope) {
        val engine = ImageEditorEngine(file, frameIndex, registry, EditCapability.Recorded)
        val layers = if (scope == EditScope.AllFrames) EditLayers(document)
        else EditLayers(EditDocument(), mapOf(frameIndex to document), setOf(frameIndex))
        val result = runBlocking { engine.save(layers) }
        assertTrue(result is EditSaveResult.Saved, "the save did not happen: $result")
    }


    @Test
    fun `a payload folding to nothing removes the recipe`() = onBurst { file ->
        val original = file.readBytes()
        save(file, 3, quarterTurn(), EditScope.AllFrames)
        assertTrue(EditRecipe.carries(file), "the setup wrote nothing")

        save(file, 3, EditDocument(), EditScope.AllFrames)

        assertFalse(EditRecipe.carries(file))
        assertContentEquals(original, file.readBytes(), "the file did not come back as shot")
    }
}
