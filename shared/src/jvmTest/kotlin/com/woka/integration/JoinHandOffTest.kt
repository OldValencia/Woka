package com.woka.integration

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.PictureFilter
import com.woka.feature.viewer.video.engine.join.LanePool
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.feature.viewer.video.engine.rangeIndex
import com.woka.feature.viewer.video.engine.seekInRanges
import com.woka.probe.generateClip
import com.woka.support.MainDispatcherRule
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cut film played by the player's own walk (ME-232), on a generated 4 s clip with a
 * keyframe every 2 s — so every range below starts off a keyframe and a seek to it has to
 * decode forward, which is what a lane pays ahead of time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinHandOffTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private class Sample(val atMs: Long, val positionMs: Long, val range: Int)

    @Test
    fun `a lane parks on its range's first frame`() = onPlayer { player, film ->
        val ranges = listOf(SourceRange(0L, 1_000L), SourceRange(2_300L, 2_900L))
        runBlocking {
            val pool = LanePool(player)
            pool.plan(ranges, playing = 0, positionMs = 0L)
            val lane = pool.laneFor(1, 2_300L)
            assertTrue(lane != null && lane.awaitParked(), "no lane parked on the second range")
            val landed = lane.trade(FFmpegFrameGrabber(film), PictureFilter())
            assertTrue(landed != null && landed.tsMs in 2_300L until 2_340L, "parked at ${landed?.tsMs}")
            landed.picture.close()
            runCatching { landed.grabber.stop() }
            landed.grabber.release()
            pool.closeAll()
        }
    }

    @Test
    fun `kept ranges play in order without their cuts`() = onPlayer { player, _ ->
        val ranges = listOf(SourceRange(300L, 900L), SourceRange(2_300L, 2_900L), SourceRange(1_200L, 1_700L))
        val samples = playThrough(player, ranges)
        samples.forEach { sample ->
            assertTrue(ranges.any { sample.positionMs in it.fromMs - 40L..it.toMs + 40L }, "a frame from a cut, at ${sample.positionMs}")
        }
        assertEquals(listOf(0, 1, 2), samples.map { it.range }.distinct())
        assertTrue(player.currentPositionMs in 1_600L..1_740L, "stopped at ${player.currentPositionMs}")
        val changes = samples.zipWithNext().filter { (a, b) -> a.positionMs != b.positionMs }.map { it.second.atMs }
        val worst = changes.zipWithNext().maxOf { (a, b) -> b - a }
        assertTrue(worst < 200L, "the picture stood still for $worst ms")
    }

    @Test
    fun `a freeze holds its frame for its hold`() = onPlayer { player, _ ->
        val samples = playThrough(player, withFreeze)
        assertTrue(abs(timeIn(samples, 1) - 1_000L) <= 200L, "held ${timeIn(samples, 1)} ms of 1000")
    }

    @Test
    fun `Pause during a hold pauses it`() = onPlayer { player, _ ->
        thread {
            if (waitFor(8_000L) { player.rangeIndex == 1 }) {
                player.isPlaying = false
                Thread.sleep(500L)
                player.isPlaying = true
            }
        }
        val samples = playThrough(player, withFreeze)
        assertTrue(abs(timeIn(samples, 1) - 1_500L) <= 250L, "held ${timeIn(samples, 1)} ms with a 500 ms pause")
    }

    private val withFreeze = listOf(
        SourceRange(300L, 900L),
        SourceRange(900L, 900L, holdMs = 1_000L),
        SourceRange(900L, 1_500L)
    )

    /** How long range [index] was on screen. */
    private fun timeIn(samples: List<Sample>, index: Int): Long {
        val first = samples.first { it.range == index }.atMs
        val after = samples.first { it.atMs > first && it.range > index }.atMs
        return after - first
    }

    /** Plays [ranges] from the first one's start and samples the player until the result ends. */
    private fun playThrough(player: VideoPlayerState, ranges: List<SourceRange>): List<Sample> {
        player.playRanges(ranges, this)
        player.seekInRanges(0, ranges.first().fromMs)
        assertTrue(waitFor(5_000L) { player.generation.isVideoAtSeekTarget }, "the first seek never landed")
        player.isPlaying = true
        val samples = mutableListOf<Sample>()
        val start = System.nanoTime()
        while (!player.edit.joins.endShown && (System.nanoTime() - start) / 1_000_000L < 10_000L) {
            samples += Sample((System.nanoTime() - start) / 1_000_000L, player.currentPositionMs, player.rangeIndex)
            Thread.sleep(2L)
        }
        assertTrue(player.edit.joins.endShown, "the result never ended")
        return samples
    }

    private fun onPlayer(block: (VideoPlayerState, File) -> Unit) {
        startKoinOnce()
        val dir = Files.createTempDirectory("woka-joins").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 4, gop = 60)
            val player = VideoPlayerState(scope, film, initialVolume = 0f).apply { isPlaying = false }
            assertTrue(waitFor(20_000L) { player.durationMs > 0L && player.tracksProbed.isCompleted }, "no duration")
            try {
                block(player, film)
            } finally {
                player.dispose()
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
