package com.woka.integration

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.video.render.renderVideo
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.probe.generateClip
import com.woka.probe.readBack
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `Render video…` end to end on a real file (ME-291, ME-292): a generated 4 s clip with a
 * keyframe every half second, because every committed corpus video has exactly one
 * keyframe and a cut on it would prove nothing about cutting mid-file (ME-290).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRenderTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val size = IntSize(640, 360)
    private val tint = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

    private fun onClip(block: (File, EditorToolRegistry) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-render").toFile()
        try {
            val source = File(dir, "clip.mp4")
            generateClip(source, "libopenh264", seconds = 4, gop = 15)
            block(source, EditorToolRegistry(listOf(TrimTool, tint), SettingsRepositoryImpl()))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun cuts(vararg ranges: Pair<Long, Long>) = EditDocument(
        ops = listOf(TrimTool.opFor(ranges.mapIndexed { i, (from, to) -> TrimClip("c$i", from, to) }))
    )

    private fun render(source: File, document: EditDocument, registry: EditorToolRegistry): File {
        val result = renderVideo(source, document, registry, size, alive = { true }, progress = {})
        return File(assertIs<EditSaveResult.Saved>(result, "the render failed: $result").path)
    }

    @Test
    fun `cuts on keyframes are copied, shorter and beside the source`() = onClip { source, registry ->
        val before = source.length()
        val out = render(source, cuts(0L to 1_000L, 2_000L to 3_000L), registry)
        assertEquals("clip edited.mp4", out.name)
        val back = readBack(out)
        assertTrue(back.firstDecodes, "the copy does not decode: $back")
        assertTrue(abs(back.durationMs - 2_000L) <= 100L, "two seconds were kept, not $back")
        assertTrue(out.length() < source.length(), "a copy of half the film is not smaller")
        assertEquals(before, source.length(), "the source was touched")
        assertTrue(out.parentFile.listFiles()!!.none { it.name.endsWith(".woka-render") }, "scratch left behind")
    }

    @Test
    fun `a cut off a keyframe re-encodes and keeps the timeline's length`() = onClip { source, registry ->
        val out = render(source, cuts(0L to 1_000L, 2_200L to 3_000L), registry)
        val back = readBack(out)
        assertTrue(back.firstDecodes, "the encode does not decode: $back")
        assertTrue(abs(back.durationMs - 1_800L) <= 150L, "1.8 s were kept, not $back")
    }

    @Test
    fun `pixel work re-encodes, and a second render takes the next name`() = onClip { source, registry ->
        val tinted = EditDocument(ops = listOf(ToolOp("tint")))
        val first = render(source, tinted, registry)
        val second = render(source, tinted, registry)
        assertEquals("clip edited 2.mp4", second.name)
        assertTrue(readBack(first).firstDecodes && readBack(second).firstDecodes)
        assertTrue(abs(readBack(first).durationMs - 4_000L) <= 150L, "the whole film was not rendered")
    }

    @Test
    fun `a cancelled render leaves nothing behind`() = onClip { source, registry ->
        val result = renderVideo(source, cuts(0L to 3_000L), registry, size, alive = { false }, progress = {})
        assertEquals(EditSaveResult.NothingToDo, result)
        assertEquals(listOf("clip.mp4"), source.parentFile.listFiles()!!.map { it.name })
    }
}
