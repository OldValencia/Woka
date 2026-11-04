package com.woka.integration

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.audio.AudioTempoFilter
import com.woka.feature.viewer.video.engine.decode.openAudioGrabber
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.probe.generateClip
import com.woka.support.MainDispatcherRule
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A cut film's sound, spliced to the sample (ME-233), read as the PCM the line would play.
 * The generated clip carries a 440 Hz tone at 6000: its steepest natural step between two
 * samples at 48 kHz is ~345, and a splice without a ramp steps by up to twice the amplitude.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSpliceTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `three ranges play as one stream, as long as the result, with no step at a join`() = onSplice(
        listOf(SourceRange(300L, 900L), SourceRange(2_311L, 2_900L), SourceRange(1_207L, 1_700L))
    ) { pcm, rate ->
        val expectedMs = 600L + 589L + 493L
        assertTrue(abs(pcm.size * 1000L / rate - expectedMs) <= 25L, "${pcm.size * 1000L / rate} ms of sound, not $expectedMs")
        val worst = pcm.toList().zipWithNext().maxOf { (a, b) -> abs(a - b) }
        assertTrue(worst < 1_000, "a step of $worst between two samples")
    }

    @Test
    fun `a freeze is silence for its hold`() = onSplice(
        listOf(SourceRange(300L, 900L), SourceRange(900L, 900L, holdMs = 500L), SourceRange(900L, 1_400L))
    ) { pcm, rate ->
        val silent = longestSilence(pcm) * 1000L / rate
        assertTrue(abs(silent - 500L) <= 15L, "$silent ms of silence for a 500 ms hold")
    }

    /** The longest run of zero samples, in frames of the left channel. */
    private fun longestSilence(pcm: ShortArray): Long {
        var best = 0L
        var run = 0L
        for (sample in pcm) {
            run = if (sample.toInt() == 0) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    /** Feeds the clip's audio through the splicer over [ranges]; [check] gets the left channel. */
    private fun onSplice(ranges: List<SourceRange>, check: (ShortArray, Int) -> Unit) {
        startKoinOnce()
        val dir = Files.createTempDirectory("woka-splice").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 4, gop = 60)
            val player = VideoPlayerState(scope, film, initialVolume = 0f).apply { isPlaying = false }
            assertTrue(waitFor(20_000L) { player.durationMs > 0L && player.tracksProbed.isCompleted }, "no duration")
            player.playRanges(ranges, this)
            val grabber = player.openAudioGrabber(-1).apply { start() }
            val channel = Channel<ByteArray>(Channel.UNLIMITED)
            runBlocking {
                grabber.setTimestamp(ranges.first().fromMs * 1000L - 100_000L)
                player.edit.audio.follow(player, ranges.first().fromMs)
                var tempo: AudioTempoFilter? = null
                while (!player.edit.audio.ended) {
                    val frame = grabber.grabSamples() ?: break
                    tempo = player.edit.audio.deliver(player, grabber, frame, tempo, channel).tempo
                }
            }
            channel.close()
            val bytes = generateSequence { channel.tryReceive().getOrNull() }.flatMap { it.asSequence() }.toList()
            val left = ShortArray(bytes.size / 4) { i -> ((bytes[i * 4].toInt() and 0xFF) or (bytes[i * 4 + 1].toInt() shl 8)).toShort() }
            val rate = grabber.sampleRate
            grabber.stop()
            grabber.release()
            player.dispose()
            check(left, rate)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
