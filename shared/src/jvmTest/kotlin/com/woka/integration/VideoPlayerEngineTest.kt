package com.woka.integration

import com.woka.fixtures.BROKEN
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import com.woka.support.awaitTrue
import com.woka.support.episodeTwoFile
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Opening, probing and tearing down the real decode engine. */
class VideoPlayerEngineTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `opening a video reports its duration and its tracks`() {
        onPlayer(episodeTwoFile()) { player ->
            awaitTrue("the probe to finish") { player.tracksProbed.isCompleted }
            awaitTrue("a duration to be read") { player.durationMs > 0L }
            assertTrue(player.hasAudio, "the fixture's audio track was not found")
            assertEquals(1, player.tracks.mediaTracks.audio.size)
            assertEquals(1, player.tracks.mediaTracks.video.size)
        }
    }

    @Test
    fun `a frame reaches the screen`() {
        onPlayer(episodeTwoFile()) { player ->
            awaitTrue("the first frame", timeoutMs = 15_000L) { player.currentFrame != null }
            val frame = assertNotNull(player.currentFrame)
            assertTrue(frame.width > 0 && frame.height > 0)
            assertNotNull(player.streamHdrInfo, "the stream's colour metadata was not read")
        }
    }

    @Test
    fun `a video with no audio opens and says so`() {
        onPlayer(SampleMedia.file(NO_AUDIO)) { player ->
            awaitTrue("the probe to finish") { player.tracksProbed.isCompleted }
            assertFalse(player.hasAudio, "a silent file reported audio")
            assertTrue(player.tracks.mediaTracks.audio.isEmpty())
            awaitTrue("a duration to be read") { player.durationMs > 0L }
        }
    }

    @Test
    fun `disposing twice is safe`() {
        onPlayer(episodeTwoFile()) { player ->
            awaitTrue("the probe to finish") { player.tracksProbed.isCompleted }
            player.dispose()
            player.dispose()
            awaitTrue("the decode loop to stop") { player.grabberJob?.isActive != true }
        }
    }

    @Test
    fun `disposing stops every loop it started`() {
        onPlayer(episodeTwoFile()) { player ->
            awaitTrue("the first frame", timeoutMs = 15_000L) { player.currentFrame != null }
            player.dispose()
            awaitTrue("all four loops to stop") {
                listOf(player.grabberJob, player.audioDecodeJob, player.presenterJob, player.audioJob)
                    .none { it?.isActive == true }
            }
        }
    }

    @Test
    fun `a file that is not there fails without hanging and without an NPE`() {
        val missing = File(Files.createTempDirectory("woka-player").toFile(), "gone.mkv")
        onPlayer(missing) { player ->
            awaitTrue("the loop to give up", timeoutMs = 15_000L) { player.tracksProbed.isCompleted }
            assertEquals(0L, player.durationMs, "a duration was invented for a missing file")
            assertFalse(waitFor(500L) { player.currentFrame != null }, "a frame came from nowhere")
        }
    }

    @Test
    fun `a truncated container errors instead of hanging`() {
        onPlayer(SampleMedia.file(BROKEN)) { player ->
            // The latch is completed in the decoder's `finally`, so it is reached
            // whether start() threw or the stream simply ran out.
            awaitTrue("the loop to settle", timeoutMs = 20_000L) { player.tracksProbed.isCompleted }
            assertTrue(player.durationMs >= 0L)
        }
    }

    @Test
    fun `the volume it opens with is the volume it reports`() {
        onPlayer(episodeTwoFile()) { player ->
            assertEquals(0f, player.volume)
            player.updateVolume(0.75f)
            assertEquals(0.75f, player.volume)
        }
    }

    @Test
    fun `two players over one file do not tread on each other`() {
        onPlayer(episodeTwoFile()) { first ->
            onPlayer(episodeTwoFile()) { second ->
                awaitTrue("both probes to finish", timeoutMs = 20_000L) {
                    first.tracksProbed.isCompleted && second.tracksProbed.isCompleted
                }
                awaitTrue("both durations", timeoutMs = 15_000L) {
                    first.durationMs > 0L && second.durationMs > 0L
                }
                assertEquals(first.durationMs, second.durationMs)
            }
        }
    }
}
