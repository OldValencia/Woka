package com.woka.integration

import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.awaitTrue
import com.woka.support.onOpenPlayer
import com.woka.support.startKoinOnce
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A file with no picture plays on the same engine as a film, with a clock in place
 * of the video decoder (AP-003). Real decode, real sound line at volume 0, against
 * the AP-001 fixtures: 2 s of 440 Hz each.
 */
class AudioPlaybackTest {

    /** The track probe reads the settings for sidecar and preference lookups. */
    @BeforeTest
    fun before() = startKoinOnce()

    private fun tone(name: String) = File(javaClass.getResource("/media/audio/$name")!!.toURI())

    @Test
    fun `an mp3 plays to its end and says so`() = onOpenPlayer(tone("tone.mp3")) { player ->
        awaitTrue("the play head to move", 3_000L) { player.currentPositionMs > 300L }
        awaitTrue("the end", 6_000L) { player.reachedEnd }
        assertFalse(player.isPlaying)
    }

    /**
     * The cover is a one-picture video stream (AP-001). Had it reached the clock, the
     * file would end straight after that picture; it must take its full two seconds.
     */
    @Test
    fun `a file with an embedded cover plays its whole length`() = onOpenPlayer(tone("tone-cover.mp3")) { player ->
        val started = System.nanoTime()
        awaitTrue("the end", 6_000L) { player.reachedEnd }
        val tookMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue(tookMs >= 1_500L, "ended after $tookMs ms of a 2000 ms file")
    }

    @Test
    fun `a seek moves the play head and playback goes on from there`() = onOpenPlayer(tone("tone.flac")) { player ->
        awaitTrue("the sound to start", 3_000L) { player.currentPositionMs > 0L }
        player.seek(1_200L)
        awaitTrue("the play head past the target", 3_000L) { player.currentPositionMs > 1_300L }
        awaitTrue("the end", 4_000L) { player.reachedEnd }
    }

    @Test
    fun `repeat file starts again instead of ending`() = onOpenPlayer(tone("tone.mp3")) { player ->
        player.repeatMode = RepeatMode.File
        awaitTrue("the play head near the end", 4_000L) { player.currentPositionMs > 1_700L }
        awaitTrue("the loop back to the start", 3_000L) { player.currentPositionMs in 1L..900L }
        assertFalse(player.reachedEnd)
        assertTrue(player.isPlaying)
    }
}
