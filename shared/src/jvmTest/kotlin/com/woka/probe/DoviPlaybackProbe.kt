package com.woka.probe

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bytedeco.javacpp.Pointer
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/** DV5-104: real audio/pacer/presenter, observed without a window or GPU drawing. */
class DoviPlaybackProbe {
    @Test
    fun `measure published P5 cadence through resize and backward seek`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val report = File("build/dv-p5/playback-cadence.txt")
        report.writeText("Published frames; no Compose draw or display timing.\n")
        withTempStorageRoot {
            startKoinOnce()
            runBlocking {
                val owner = SupervisorJob()
                val scope = CoroutineScope(owner + Dispatchers.Default)
                val file = File("D:/Movies/DV samples/Mystery Box Dolby Vision Profile 5.mp4")
                val player = withContext(Dispatchers.Swing) { VideoPlayerState(scope, file, initialVolume = 0f) }
                try {
                    withTimeout(30_000) {
                        while (player.currentFrame == null || !player.tracksProbed.isCompleted) delay(20)
                    }
                    for (width in listOf(3840, 1920, 3840)) {
                        withContext(Dispatchers.Swing) { player.renderScale.setSurface(width, width * 9 / 16) }
                        withTimeout(30_000) {
                            while (player.currentFrame?.width != width || player.isReloadingVideo) delay(20)
                        }
                        withContext(Dispatchers.Swing) { player.seek(5000) }
                        withTimeout(30_000) {
                            while (player.isSeeking || player.currentPositionMs < 5000) delay(20)
                        }
                        // Real time is the measurement; settle for 3s, observe a bounded 10s window.
                        observe(player, 3000)
                        val position = player.currentPositionMs
                        val result = observe(player, 10_000)
                        val row = "width=$width mediaDeltaMs=${player.currentPositionMs - position} " +
                            "audio=${player.hasAudio} failed=${player.openFailed} " +
                            "physicalMiB=${Pointer.physicalBytes() / 1048576} $result"
                        report.appendText("$row\n")
                        println("DV5-PLAYBACK $row")
                    }
                } finally {
                    withContext(Dispatchers.Swing) { player.dispose() }
                    withTimeout(30_000) { owner.cancelAndJoin() }
                }
            }
        }
    }

    private suspend fun observe(player: VideoPlayerState, milliseconds: Long): String {
        val start = System.nanoTime()
        var last = start
        var id = player.currentFrameId
        var count = 0
        val gaps = mutableListOf<Double>()
        while (System.nanoTime() - start < milliseconds * 1_000_000L) {
            withContext(Dispatchers.Swing) {
                val next = player.currentFrameId
                if (next != id) {
                    val now = System.nanoTime()
                    gaps += (now - last) / 1e6
                    count++
                    last = now
                    id = next
                    // No window holds earlier frames; exercise the existing retention boundary.
                    player.frameRetention.acknowledge(next)
                }
            }
            delay(2)
        }
        gaps.sort()
        val elapsed = (System.nanoTime() - start) / 1e9
        return "publishedFps=${count / elapsed} p95Ms=${gaps.getOrNull((gaps.size * .95).toInt())} " +
            "maxMs=${gaps.lastOrNull()} gapsOver25ms=${gaps.count { it > 25 }}"
    }
}
