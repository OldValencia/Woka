package com.woka.unit

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_BMP
import com.woka.fixtures.STILL_PNG
import com.woka.support.StageTool
import com.woka.support.episodeTwoFile
import com.woka.support.toolOp
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A crop's shape: it names its own frame, which is what `drawnSize` reads. */
private val CROP_FRAME = IntSize(320, 240)

/**
 * The factory now answers for a video, and what it hands back folds (ME-201).
 *
 * Asked without a window: everything here is about the engine's *answers* — its kind,
 * the size operations are addressed in, what a crop does to the drawn frame, and the
 * fact that a save fails with a sentence rather than throwing. The canvas is a
 * composable and belongs to the UI tier.
 *
 * **The still cases are here on purpose.** Adding a fourth `EditCapability` and a branch
 * to the factory is exactly the change that could quietly re-answer for a photograph, so
 * both ends of the image table are asserted in the file that widens it.
 */
class VideoEditorEngineTest {

    @Test
    fun `a video is editable and answers Preview`() = withFoldRegistry(emptyList()) { registry, _ ->
        val factory = EditorEngineFactoryImpl(registry)
        val path = episodeTwoFile().absolutePath

        val engine = assertNotNull(factory.create(path), "the factory refused a video")
        assertEquals(EditCapability.Preview, factory.capability(path))
        assertEquals(EditCapability.Preview, engine.capability)
        assertEquals(MediaKind.Video, engine.kind)
    }

    @Test
    fun `sourceSize is the coded frame, and a crop moves the drawn frame`() {
        val crop = StageTool("crop", ToolDomain.Geometry, RasterStage.Geometry(Matrix(), CROP_FRAME))
        withFoldRegistry(listOf(crop)) { registry, _ ->
            val engine = assertNotNull(
                EditorEngineFactoryImpl(registry).create(episodeTwoFile().absolutePath)
            )

            val coded = engine.sourceSize
            assertTrue(coded.width > 0 && coded.height > 0, "no coded size was read: $coded")
            assertEquals(coded, engine.drawnSize(EditDocument()), "an empty document moved the frame")
            assertEquals(
                CROP_FRAME,
                engine.drawnSize(EditDocument(ops = listOf(toolOp("crop")))),
                "drawnSize followed the source rather than the crop"
            )
        }
    }

    @Test
    fun `a save fails with the sentence the banner prints, and never throws`() =
        withFoldRegistry(emptyList()) { registry, _ ->
            val engine = assertNotNull(
                EditorEngineFactoryImpl(registry).create(episodeTwoFile().absolutePath)
            )

            val saved = runBlocking { engine.save(EditLayers(EditDocument())) }
            val failed = assertIs<EditSaveResult.Failed>(saved, "a save must report, not pretend")
            assertEquals("Video edits cannot be saved yet.", failed.reason)
            assertEquals(EditSaveResult.NothingToDo, runBlocking { engine.revert() })
        }

    @Test
    fun `the image table still answers exactly as it did`() =
        withFoldRegistry(emptyList()) { registry, _ ->
            val factory = EditorEngineFactoryImpl(registry)
            val png = SampleMedia.file(STILL_PNG).absolutePath
            val bmp = SampleMedia.file(STILL_BMP).absolutePath

            assertEquals(EditCapability.Recorded, factory.capability(png))
            assertEquals(EditCapability.Unavailable, factory.capability(bmp))
            assertNotNull(factory.create(png), "a PNG stopped being editable")
            assertNull(factory.create(bmp), "a BMP became editable")
        }
}
