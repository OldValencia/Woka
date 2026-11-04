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
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
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
        val result = renderVideo(source, document, { emptyList() }, registry, size, alive = { true }, progress = {})
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

    /** ME-216: two seconds at 2x render as one, encoded — a copy cannot retime. */
    @Test
    fun `a fast clip renders at its timeline length`() = onClip { source, registry ->
        val fast = EditDocument(ops = listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 2_000L, speed = 2f)))))
        val back = readBack(render(source, fast, registry))
        assertTrue(back.firstDecodes, "the retimed render does not decode: $back")
        assertTrue(abs(back.durationMs - 1_000L) <= 150L, "2 s at 2x is 1 s, not $back")
    }

    /** ME-215: the look is clip 1's own, so only clip 1's frames come out redder. */
    @Test
    fun `a clip's own look renders on that clip alone`() = onClip { source, registry ->
        val clips = listOf(TrimClip("a", 0L, 1_000L), TrimClip("b", 2_000L, 3_000L, key = 1))
        val document = EditDocument(ops = listOf(TrimTool.opFor(clips)))
        val looks = { key: Int -> if (key == 1) listOf(ToolOp("tint")) else emptyList() }
        val result = renderVideo(source, document, looks, registry, size, alive = { true }, progress = {})
        val out = File(assertIs<EditSaveResult.Saved>(result, "the render failed: $result").path)
        val plain = redOverGreen(out, 500L)
        val tinted = redOverGreen(out, 1_500L)
        assertTrue(tinted > plain * 1.2, "clip 1 is not redder than clip 0: $plain vs $tinted")
    }

    /** Mean red over mean green of the frame at [atMs]. */
    private fun redOverGreen(file: File, atMs: Long): Double {
        val grabber = FFmpegFrameGrabber(file).apply { pixelFormat = AV_PIX_FMT_RGBA }
        try {
            grabber.start()
            grabber.setTimestamp(atMs * 1000L)
            val frame = generateSequence { grabber.grabImage() }.first { it.image != null }
            val bytes = frame.image[0] as ByteBuffer
            var red = 0L
            var green = 0L
            for (at in 0 until bytes.limit() - 3 step 4) {
                red += bytes.get(at).toInt() and 0xFF
                green += bytes.get(at + 1).toInt() and 0xFF
            }
            return red.toDouble() / green.coerceAtLeast(1L)
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    @Test
    fun `a cancelled render leaves nothing behind`() = onClip { source, registry ->
        val result = renderVideo(source, cuts(0L to 3_000L), { emptyList() }, registry, size, alive = { false }, progress = {})
        assertEquals(EditSaveResult.NothingToDo, result)
        assertEquals(listOf("clip.mp4"), source.parentFile.listFiles()!!.map { it.name })
    }
}
