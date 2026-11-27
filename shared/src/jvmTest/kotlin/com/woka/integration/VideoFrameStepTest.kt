package com.woka.integration

import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.stepOneFrame
import com.woka.feature.viewer.video.engine.frameRate
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.shownFrameMs
import com.woka.feature.viewer.video.stepVideoFrame
import com.woka.support.awaitTrue
import com.woka.support.episodeTwoFile
import com.woka.support.onOpenPlayer
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `,` / `.` on a film in the viewer (RD-501): a paused film moves one picture, counted from
 * the picture on screen; a playing one is left alone. On a real decode, because what is
 * asserted is the picture the player lands on, not the arithmetic (`FrameStepsTest`).
 */
class VideoFrameStepTest {

    /** The player reads its settings through Koin, as `VideoSeekTest`'s does. */
    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `a paused step lands on the next picture and back`() = onOpenPlayer(episodeTwoFile()) { player ->
        awaitTrue("a picture", timeoutMs = 15_000L) { player.shownFrameMs >= 0L }
        player.isPlaying = false
        player.seek(1_000L)
        awaitTrue("the seek's picture", timeoutMs = 15_000L) { player.shownFrameMs >= 990L }
        waitFor(300L) { false }
        val period = (1000.0 / player.frameRate()).roundToLong()
        val start = player.shownFrameMs

        stepVideoFrame(player, null, 1)
        awaitTrue("a picture on", timeoutMs = 15_000L) { player.shownFrameMs > start }
        assertTrue(abs(player.shownFrameMs - start - period) <= 1, "${player.shownFrameMs - start} ms is not one picture")

        stepVideoFrame(player, null, -1)
        awaitTrue("a picture back", timeoutMs = 15_000L) { player.shownFrameMs < start + period }
        assertEquals(start, player.shownFrameMs, "the step back did not return to the same picture")
        assertFalse(player.isPlaying)
    }

    @Test
    fun `a playing film ignores the key`() = onOpenPlayer(episodeTwoFile()) { player ->
        player.isPlaying = true
        val state = MediaViewerState(player.file.absolutePath)
        assertFalse(state.stepOneFrame(player, null, 1), "a playing film took the step")
        assertTrue(player.isPlaying)
    }
}
