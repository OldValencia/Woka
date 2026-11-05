package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.StillColorInfo
import com.woka.core.ui.image.SourceRange
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.recordRecipe
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.video.RecipeVideoEdits
import com.woka.feature.editor.engine.video.coverIn
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.tools.cover.CoverTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.probe.generateClip
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What `Done` writes into an `mp4`, read back the way the viewer and the grid read it
 * (ME-284) — the gap the author found on 2026-11-02: markup in the editor, nowhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoEditsReaderTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tint = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(0f)))

    private fun onFilm(block: (File, RecipeVideoEdits) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-reader").toFile()
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 3, gop = 15)
            block(film, RecipeVideoEdits(EditorToolRegistry(listOf(TrimTool, tint), SettingsRepositoryImpl())))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun save(film: File, document: EditDocument) =
        assertIs<EditSaveResult.Saved>(recordRecipe(film, EditLayers(document), SourceStamp.of(film)))

    @Test
    fun `an unedited film reads as no edit`() = onFilm { film, edits ->
        assertNull(edits.editOf(film))
    }

    @Test
    fun `the cuts and the picture work both reach the reader`() = onFilm { film, edits ->
        val cuts = TrimTool.opFor(listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L)))
        save(film, EditDocument(ops = listOf(cuts, ToolOp("tint"))))

        val edit = assertNotNull(edits.editOf(film), "Done wrote an edit nobody reads")
        assertEquals(listOf(SourceRange(0L, 1_000L), SourceRange(2_000L, 3_000L)), edit.clock.ranges)
        val frame = ImageBitmap(64, 36)
        assertNotNull(edit.painter(frame, StillColorInfo.Sdr, moving = true), "the picture work is not drawn")
    }

    @Test
    fun `cuts alone leave the picture to the viewer's own painter`() = onFilm { film, edits ->
        save(film, EditDocument(ops = listOf(TrimTool.opFor(listOf(TrimClip("a", 500L, 2_500L))))))
        val edit = assertNotNull(edits.editOf(film))
        assertNull(edit.painter(ImageBitmap(64, 36), StillColorInfo.Sdr, moving = true))
    }

    /** ME-215: clip 1's own look is drawn while clip 1 plays, and not while clip 0 does. */
    @Test
    fun `a clip's own look is drawn on that clip alone`() = onFilm { film, edits ->
        val cuts = TrimTool.opFor(listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1)))
        val layers = EditLayers(EditDocument(listOf(cuts)), mapOf(1 to EditDocument(listOf(ToolOp("tint")))), setOf(1))
        assertIs<EditSaveResult.Saved>(recordRecipe(film, layers, SourceStamp.of(film)))

        val edit = assertNotNull(edits.editOf(film))
        val frame = ImageBitmap(64, 36)
        assertNull(edit.painter(frame, StillColorInfo.Sdr, moving = true), "clip 0 drew clip 1's look")
        edit.clock.index = 1
        assertNotNull(edit.painter(frame, StillColorInfo.Sdr, moving = true), "clip 1's look is not drawn")
    }

    @Test
    fun `the poster is drawn through the edit`() = onFilm { film, edits ->
        save(film, EditDocument(ops = listOf(ToolOp("tint"))))
        val poster = ImageBitmap(64, 36)
        val drawn = assertNotNull(edits.editOf(film)).poster(poster)
        assertNotEquals(poster, drawn, "the tile would show the film as shot")
        assertEquals(64, drawn.toPixelMap().width)
    }

    /** ME-287: a timeline cover is read back in source time, through its own clip's look. */
    @Test
    fun `the chosen cover is a frame of the result`() = onFilm { film, edits ->
        val cuts = TrimTool.opFor(listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1)))
        val cover = ToolOp(CoverTool.manifest.id, assertNotNull(CoverTool.atPlayHead(CoverTool.Use, ParamSet(), 1_500L, EditDocument(listOf(cuts)))))
        val layers = EditLayers(EditDocument(listOf(cuts, cover)), mapOf(1 to EditDocument(listOf(ToolOp("tint")))), setOf(1))
        assertIs<EditSaveResult.Saved>(recordRecipe(film, layers, SourceStamp.of(film)))

        val edit = assertNotNull(edits.editOf(film))
        assertEquals(2_500L, edit.coverMs, "the cover is not where the play head was")
        val poster = ImageBitmap(64, 36)
        assertNotEquals(poster, edit.poster(poster), "the cover's clip did not lend it its look")
    }

    @Test
    fun `a cover past a later trim stays inside the result, and Clear removes it`() {
        val clips = listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1))
        assertEquals(2_999L to 1, coverIn(clips, 9_000L))
        assertEquals(400L to 0, coverIn(emptyList(), 400L))
        assertNull(CoverTool.atPlayHead(CoverTool.Clear, ParamSet(), 1_500L, EditDocument()))
        assertNull(CoverTool.coverIn(listOf(ToolOp(CoverTool.manifest.id, ParamSet().with(CoverTool.Clear, true)))))
    }

    /** 1.0.6 review: kept as a timeline time alone, a reorder slid the cover onto another clip. */
    @Test
    fun `a cover stays on its clip's frame when the clips are reordered`() {
        val clips = listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1))
        val params = assertNotNull(CoverTool.atPlayHead(CoverTool.Use, ParamSet(), 400L, EditDocument(listOf(TrimTool.opFor(clips)))))
        val ops = listOf(ToolOp(CoverTool.manifest.id, params))
        assertEquals(400L to 0, coverIn(clips.reversed(), assertNotNull(CoverTool.coverIn(ops)), CoverTool.anchorIn(ops)))
    }
}
