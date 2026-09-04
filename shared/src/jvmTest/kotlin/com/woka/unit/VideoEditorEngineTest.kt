package com.woka.unit

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_BMP
import com.woka.fixtures.STILL_PNG
import com.woka.support.StageTool
import com.woka.support.episodeTwoFile
import com.woka.support.toolOp
import com.woka.support.withFoldRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A crop's shape: it names its own frame, which is what `drawnSize` reads. */
private val CROP_FRAME = IntSize(320, 240)

/**
 * The factory now answers for a video, and what it hands back folds (ME-201) — and since
 * ME-288 the answer depends on the container, exactly as it does for a photograph.
 *
 * Asked without a window: everything here is about the engine's *answers* — its kind, the
 * size operations are addressed in, what a crop does to the drawn frame. The canvas is a
 * composable and belongs to the UI tier, and the round trip through a real container is
 * `VideoRecipeTest`'s.
 *
 * **The still cases are here on purpose.** Changing the factory's branch is exactly what
 * could quietly re-answer for a photograph, so both ends of the image table are asserted
 * in the file that widens it.
 */
class VideoEditorEngineTest {

    @Test
    fun `a video with a carrier answers Recorded`() = withFoldRegistry(emptyList()) { registry, _ ->
        val factory = EditorEngineFactoryImpl(registry)
        val path = SampleMedia.file(LIVE_PHOTO_MOTION).absolutePath

        val engine = assertNotNull(factory.create(path), "the factory refused a mov")
        assertEquals(EditCapability.Recorded, factory.capability(path))
        assertEquals(EditCapability.Recorded, engine.capability)
        assertEquals(MediaKind.Video, engine.kind)
    }

    /**
     * **The visible half of ME-288.** A Matroska container has no slot yet, so the refusal
     * happens at the menu rather than at `Done` — the line `EditorEngineFactoryImpl`'s own
     * KDoc holds for the eight image formats it moved forward in 2026-08-19. ME-282 gives
     * it a carrier and this answer changes back.
     */
    @Test
    fun `a video without one answers Unavailable and gets no engine`() =
        withFoldRegistry(emptyList()) { registry, _ ->
            val factory = EditorEngineFactoryImpl(registry)
            val path = episodeTwoFile().absolutePath

            assertEquals(EditCapability.Unavailable, factory.capability(path))
            assertNull(factory.create(path), "an mkv was given an editor it cannot save from")
        }

    @Test
    fun `sourceSize is the coded frame, and a crop moves the drawn frame`() {
        val crop = StageTool("crop", ToolDomain.Geometry, RasterStage.Geometry(Matrix(), CROP_FRAME))
        withFoldRegistry(listOf(crop)) { registry, _ ->
            val engine = assertNotNull(
                EditorEngineFactoryImpl(registry)
                    .create(SampleMedia.file(LIVE_PHOTO_MOTION).absolutePath)
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
