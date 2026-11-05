package com.woka.integration

import java.nio.ShortBuffer
import java.io.File
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.probe.readBack
import com.woka.support.MainDispatcherRule
import com.woka.support.onClip
import com.woka.support.render
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** A render's time, retimed: a clip's speed (ME-216) and a freeze's hold (ME-217). */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRenderTimeTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** ME-216: two seconds at 2x render as one, encoded — a copy cannot retime. */
    @Test
    fun `a fast clip renders at its timeline length`() = onClip { source, registry ->
        val fast = EditDocument(ops = listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 2_000L, speed = 2f)))))
        val back = readBack(render(source, fast, registry))
        assertTrue(back.firstDecodes, "the retimed render does not decode: $back")
        assertTrue(abs(back.durationMs - 1_000L) <= 150L, "2 s at 2x is 1 s, not $back")
    }

    /** ME-217: one second of film and a one-second freeze render as two, with sound. */
    @Test
    fun `a freeze adds its hold to the render`() = onClip { source, registry ->
        val clips = listOf(TrimClip("a", 0L, 1_000L), TrimClip("f", 1_000L, 1_000L, key = 1, holdMs = 1_000L))
        val back = readBack(render(source, EditDocument(ops = listOf(TrimTool.opFor(clips))), registry))
        assertTrue(back.firstDecodes, "the frozen render does not decode: $back")
        assertTrue(abs(back.durationMs - 2_000L) <= 150L, "1 s + a 1 s hold is 2 s, not $back")
    }

    /** ME-240: the whole film muted renders silent; the source is not. */
    @Test
    fun `a muted film renders without sound`() = onClip { source, registry ->
        val mute = ToolOp(SoundTool.manifest.id, ParamSet().with(SoundTool.Mute, true))
        val document = EditDocument(ops = listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 2_000L))), mute))
        val out = render(source, document, registry)
        assertTrue(loudestSample(source) > 1_000, "the source has no sound to mute")
        assertTrue(loudestSample(out) < 50, "the muted render is not silent: ${loudestSample(out)}")
    }

    private fun loudestSample(file: File): Int {
        val grabber = FFmpegFrameGrabber(file).apply { sampleFormat = AV_SAMPLE_FMT_S16 }
        try {
            grabber.start()
            var loudest = 0
            repeat(40) {
                val pcm = grabber.grabSamples()?.samples?.firstOrNull() as? ShortBuffer ?: return@repeat
                for (i in 0 until pcm.limit()) loudest = maxOf(loudest, abs(pcm.get(i).toInt()))
            }
            return loudest
        } finally {
            grabber.stop()
            grabber.release()
        }
    }
}

