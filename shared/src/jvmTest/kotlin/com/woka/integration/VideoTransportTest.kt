package com.woka.integration

import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.skipBy
import com.woka.feature.viewer.video.engine.togglePlayPause
import com.woka.support.episodeTwoFile
import com.woka.support.onOpenPlayer
import com.woka.support.startKoinOnce
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Skip, play/pause and the drag handshake, on a player whose duration is known. */
class VideoTransportTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the seek handshake pauses and restores what it found`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.isPlaying = true
            player.pauseForSeek()
            assertFalse(player.isPlaying)
            player.resumeAfterSeek()
            assertTrue(player.isPlaying, "playback was not restored after the drag")

            player.isPlaying = false
            player.pauseForSeek()
            player.resumeAfterSeek()
            assertFalse(player.isPlaying, "a paused player started itself after a drag")
        }
    }

    @Test
    fun `resuming without a matching pause changes nothing`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.isPlaying = false
            player.resumeAfterSeek()
            assertFalse(player.isPlaying, "a stray resume started playback")
        }
    }

    @Test
    fun `a relative skip stays inside the file`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(0L)
            player.skipBy(-5_000L)
            assertEquals(0L, player.currentPositionMs)
            player.skipBy(player.durationMs + 5_000L)
            assertEquals(player.durationMs, player.currentPositionMs)
        }
    }

    @Test
    fun `repeat file turns a skip off the end into a rewind`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.repeatMode = RepeatMode.File
            player.seek(player.durationMs / 2)
            player.skipBy(player.durationMs)
            assertEquals(0L, player.currentPositionMs, "Repeat File parked at the end instead of wrapping")
            assertTrue(player.isPlaying, "Repeat File paused instead of playing on")
        }
    }

    @Test
    fun `without repeat file a skip off the end stops`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.repeatMode = RepeatMode.Folder
            player.seek(player.durationMs / 2)
            player.skipBy(player.durationMs)
            assertEquals(player.durationMs, player.currentPositionMs)
            assertFalse(player.isPlaying)
        }
    }

    @Test
    fun `a backward skip is never turned into a rewind by repeat file`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.repeatMode = RepeatMode.File
            val middle = player.durationMs / 2
            player.seek(middle)
            player.skipBy(-1L)
            assertEquals(middle - 1L, player.currentPositionMs)
        }
    }

    @Test
    fun `play from the end starts over rather than sitting there`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(player.durationMs)
            assertFalse(player.isPlaying)
            player.togglePlayPause()
            assertEquals(0L, player.currentPositionMs, "Play at the end did not rewind")
            assertTrue(player.isPlaying)
        }
    }

    @Test
    fun `pause and play in the middle leave the play head alone`() {
        onOpenPlayer(episodeTwoFile()) { player ->
            player.seek(player.durationMs / 3)
            val at = player.currentPositionMs
            player.isPlaying = true
            player.togglePlayPause()
            assertFalse(player.isPlaying)
            assertEquals(at, player.currentPositionMs)
        }
    }
}
