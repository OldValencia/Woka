package com.woka.probe

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * TEMPORARY (8K investigation §7): real-time draw cadence, repeated resize and decoder teardown.
 * Real delays are the subject: each phase has a bounded 5s settling and 8s observation window.
 * Explicit opt-in avoids opening windows or running long probes in the ordinary suite.
 */
class EightKPlaybackProbe {
    @Test
    fun `measure repeatable playback at small and screen sized surfaces`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val output = File("build/dv-investigation/live").also { it.mkdirs() }
        val report = File(output, "playback.txt")
        report.writeText("JVM ${ManagementFactory.getRuntimeMXBean().inputArguments}\n")
        withTempStorageRoot {
            startKoinOnce()
            Recording(Configuration.getConfiguration("profile")).use { recording ->
                recording.start()
                try {
                    runBlocking {
                        repeat(2) { round ->
                            for (kind in listOf("10bit", "HDR10")) {
                                val file = File("D:/Movies/DV samples/Test Jellyfin 8K HEVC $kind 150M.mp4")
                                require(file.isFile) { "Missing sample: $file" }
                                trial(file, round + 1, report)
                            }
                        }
                    }
                } finally {
                    recording.stop()
                    recording.dump(File(output, "playback.jfr").toPath())
                }
            }
        }
    }

    private suspend fun trial(file: File, round: Int, report: File) {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Default)
        val metrics = EightKProbeMetrics()
        val player = withContext(Dispatchers.Swing) { VideoPlayerState(scope, file, initialVolume = 0f) }
        var view: EightKProbeWindow? = null
        try {
            view = withContext(Dispatchers.Swing) { EightKProbeWindow(player, metrics) }
            withTimeout(30000) {
                while (!player.tracksProbed.isCompleted || player.currentFrame == null) delay(50)
            }
            for ((index, full) in listOf(false, true, false).withIndex()) {
                withContext(Dispatchers.Swing) {
                    player.isPlaying = false
                    view.resize(full)
                }
                delay(1500)
                withContext(Dispatchers.Swing) {
                    player.seek(2000)
                    player.isPlaying = true
                }
                delay(5000)
                withTimeout(30000) {
                    while (player.isSeeking || player.isReloadingVideo) delay(50)
                }
                metrics.start()
                val startPosition = player.currentPositionMs
                delay(8000)
                val data = metrics.finish()
                val row = "${file.name} round=$round phase=$index screenSized=$full surface=${view.surface} " +
                    "rgba=${player.currentFrame?.width}x${player.currentFrame?.height} " +
                    "applied=${player.renderScale.decodeWidth}x${player.renderScale.decodeHeight} " +
                    "mediaDeltaMs=${player.currentPositionMs - startPosition} " +
                    "playing=${player.isPlaying} failed=${player.openFailed} " +
                    "publishedId=${player.currentFrameId} acknowledgedId=${view.acknowledged} $data"
                report.appendText("$row\n")
                println("DV8K $row")
            }
        } finally {
            withContext(Dispatchers.Swing) {
                view?.window?.dispose()
                player.dispose()
            }
            withTimeout(30000) { owner.cancelAndJoin() }
        }
    }
}
