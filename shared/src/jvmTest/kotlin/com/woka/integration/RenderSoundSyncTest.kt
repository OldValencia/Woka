package com.woka.integration

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.trim.FREEZE_HOLD_MS
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.probe.withGrabber
import com.woka.support.MainDispatcherRule
import com.woka.support.cuts
import com.woka.support.onClip
import com.woka.support.render
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Rule
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** One AAC frame either way, and the encoder's priming. */
private const val SLACK_MS = 60L

/**
 * 1.0.6 review: an encode placed its picture by time and appended its sound by count, so any
 * sound a clip came up short by moved every later clip's sound earlier. The result's sound must
 * be as long as its picture, however many cuts, speeds and freezes it has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RenderSoundSyncTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `cuts off keyframes keep the sound as long as the picture`() = onClip { source, registry ->
        val document = cuts(0L to 700L, 1_130L to 1_810L, 2_240L to 3_330L)
        assertLevel(render(source, document, registry), 700L + 680L + 1_090L)
    }

    @Test
    fun `speeds and a freeze keep the sound as long as the picture`() = onClip { source, registry ->
        val clips = listOf(
            TrimClip("a", 0L, 1_000L, speed = 2f),
            TrimClip("f", 1_000L, 1_000L, key = 1, holdMs = FREEZE_HOLD_MS),
            TrimClip("b", 1_500L, 2_500L, key = 2, speed = 0.5f)
        )
        assertLevel(render(source, EditDocument(listOf(TrimTool.opFor(clips))), registry), clips.totalMs())
    }

    private fun assertLevel(out: File, expectedMs: Long) {
        val soundMs = soundMsOf(out)
        assertTrue(abs(soundMs - expectedMs) <= SLACK_MS, "$soundMs ms of sound under $expectedMs ms of picture")
    }

    /** Every decoded sample frame of [file], counted. */
    private fun soundMsOf(file: File): Long = withGrabber(file) { grabber: FFmpegFrameGrabber ->
        var frames = 0L
        while (true) {
            val samples = grabber.grabSamples() ?: break
            val pcm = samples.samples.first()
            frames += if (pcm is ShortBuffer) pcm.remaining() / grabber.audioChannels else pcm.limit()
        }
        frames * 1000L / grabber.sampleRate
    }
}
