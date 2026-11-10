package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onClip
import com.woka.support.render
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A timed shape in a rendered file (ME-263): present inside its window, absent before and
 * after it, half way through its fade out — the same envelope the canvas and the viewer read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkupTimingRenderTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** A thick red frame over most of the picture, so its presence moves the mean colour. */
    private val box = MarkupShape.Rectangle("box", 0xFFFF0000, 120f, MarkupPoint(120f, 80f), MarkupPoint(520f, 280f))

    @Test
    fun `a timed shape renders inside its window and nowhere else`() = onClip { source, _ ->
        val registry = EditorToolRegistry(listOf(TrimTool, MarkupTool), SettingsRepositoryImpl())
        val window = TimeWindow(atMs = 1_000L, holdMs = 1_500L, fadeOutMs = 1_000L)
        val params = ParamSet()
            .with(MarkupTool.Shapes, listOf(box))
            .with(MarkupTool.Windows, mapOf("box" to window))
        val out = render(source, EditDocument(listOf(ToolOp(MarkupTool.manifest.id, params))), registry)

        val frames = redOverGreenByFrame(out)
        fun at(ms: Long) = frames.first { (stamp, _) -> stamp >= ms }.second
        val before = at(500L)
        val held = at(1_200L)
        val fading = at(2_000L)
        val after = at(3_000L)
        assertTrue(held > before * 1.5, "the shape is not there inside its window: $before vs $held")
        assertTrue(fading < held && fading > before, "the fade out is not part way: $before, $fading, $held")
        assertTrue(after < held / 1.5, "the shape outlives its window: $after vs $held")
    }

    /**
     * Mean red over mean green of every frame, by its own timestamp in milliseconds — read
     * straight through, because a seek in this clip lands half a second off the time asked.
     */
    private fun redOverGreenByFrame(file: File): List<Pair<Long, Double>> {
        val grabber = FFmpegFrameGrabber(file).apply { pixelFormat = AV_PIX_FMT_RGBA }
        try {
            grabber.start()
            return generateSequence { grabber.grabImage() }.map { frame ->
                val bytes = frame.image[0] as ByteBuffer
                var red = 0L
                var green = 0L
                for (at in 0 until bytes.limit() - 3 step 4) {
                    red += bytes.get(at).toInt() and 0xFF
                    green += bytes.get(at + 1).toInt() and 0xFF
                }
                frame.timestamp / 1000L to red.toDouble() / green.coerceAtLeast(1L)
            }.toList()
        } finally {
            grabber.stop()
            grabber.release()
        }
    }
}
