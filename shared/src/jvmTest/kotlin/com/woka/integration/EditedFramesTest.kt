package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.recipe.carrierFor
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The third question of `StillEdits`' seam: which frames carry an edit of their own
 * (ME-157).
 *
 * Its own file rather than four more tests in `StillEditsTest`, which is at 134 lines
 * and would have gone past the size mandate. The subject is different enough to earn
 * one: that file is about a picture *looking* edited, this one is about which frames
 * are marked.
 */
class EditedFramesTest {

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-marks").toFile()
        try {
            AppliedStillEdits.install(RecipeStillEdits(registry))
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun marks(file: File) = runBlocking { AppliedStillEdits.editedFrames(file) }

    @Test
    fun `a file with no recipe marks nothing`() = onPhoto { file ->
        assertEquals(emptySet(), marks(file))
    }

    /**
     * The distinction the whole mark rests on: a file-wide recipe applies to every
     * frame, and marking all of them would say nothing "photo.heic · Edited" does not
     * already say better.
     */
    @Test
    fun `an un-keyed recipe marks nothing either`() = onPhoto { file ->
        EditRecipe.write(file, quarterTurn())

        assertEquals(emptySet(), marks(file))
    }

    @Test
    fun `a keyed payload marks exactly its keys`() = onPhoto { file ->
        val keyed = EditPayload(frames = mapOf(1 to quarterTurn(), 3 to quarterTurn()))
        EditRecipe.write(file, keyed)

        assertEquals(setOf(1, 3), marks(file))
    }

    /** A malformed payload must not stop a strip from drawing, exactly as `carries`. */
    @Test
    fun `a garbage payload answers empty rather than throwing`() = onPhoto { file ->
        val carrier = carrierFor(file) ?: error("the fixture has no carrier")
        val broken = carrier.with(file.readBytes(), "{not json") ?: error("nothing written")
        file.writeBytes(broken)

        assertEquals(emptySet(), marks(file))
    }
}
