package com.woka.integration

import com.woka.feature.viewer.video.engine.seek
import com.woka.support.awaitTrue
import com.woka.support.episodeTwoFile
import com.woka.support.onOpenPlayer
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Seeking against the real decoder. The rest of the transport is `VideoTransportTest`. */
class VideoSeekTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `seeking to the middle moves the play head there`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            val middle = player.durationMs / 2
            player.seek(middle)
            assertEquals(middle, player.currentPositionMs)
            awaitTrue("a frame after the seek", timeoutMs = 15_000L) { player.currentFrame != null }
        }
    }

    @Test
    fun `seeking backwards is not treated as the end`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(player.durationMs / 2)
            player.seek(0L)
            assertEquals(0L, player.currentPositionMs)
            assertFalse(player.reachedEnd, "rewinding raised the end flag")
        }
    }

    @Test
    fun `seeking before the start is clamped, not negative`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(-30_000L)
            assertEquals(0L, player.currentPositionMs)
        }
    }

    @Test
    fun `seeking past the end pins the head there and pauses`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(player.durationMs + 60_000L)
            assertEquals(player.durationMs, player.currentPositionMs)
            assertFalse(player.isPlaying, "playback ran on past the end")
        }
    }

    @Test
    fun `every seek is answered by a frame`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            val targets = listOf(0L, player.durationMs / 4, player.durationMs / 2, 0L)
            targets.forEach { target ->
                player.currentFrame = null
                player.seek(target)
                awaitTrue("a frame after seeking to $target", timeoutMs = 15_000L) {
                    player.currentFrame != null
                }
                assertNotNull(player.currentFrame)
            }
        }
    }

    @Test
    fun `a seek while paused leaves it paused and still shows the frame`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.isPlaying = false
            player.currentFrame = null
            player.seek(player.durationMs / 3)
            awaitTrue("a frame while paused", timeoutMs = 15_000L) { player.currentFrame != null }
            assertFalse(player.isPlaying, "the seek resumed playback on its own")
        }
    }

    @Test
    fun `a seek clears the end flag`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.reachedEnd = true
            player.seek(player.durationMs / 2)
            assertFalse(player.reachedEnd)
        }
    }

    @Test
    fun `seeking does not disturb the duration`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            val duration = player.durationMs
            listOf(0L, duration / 2, duration, -1L).forEach { player.seek(it) }
            assertFalse(waitFor(300L) { player.durationMs != duration }, "the duration moved")
        }
    }
}
