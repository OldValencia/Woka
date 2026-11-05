package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.PlayEditedRanges
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.probe.generateClip
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The viewer plays only what `Done` kept (ME-284): a 4 s film trimmed to its first 1.5 s
 * stops there, as the author's 26 s film trimmed to 10 s did not (reported 2026-11-02).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditedPlaybackUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a trimmed film stops at the end of what was kept`() {
        startKoinOnce()
        val dir = Files.createTempDirectory("woka-walk").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 4, gop = 15)
            val player = VideoPlayerState(scope, film, initialVolume = 0f)
            onUi {
                setContent { PlayEditedRanges(player, EditedClock(listOf(SourceRange(0L, 1_500L)))) }
                assertTrue(waitFor(20_000L) { player.durationMs > 0L }, "no duration")
                player.isPlaying = true
                val stopped = waitFor(6_000L) { mainClock.advanceTimeBy(50); !player.isPlaying }
                assertTrue(stopped, "it played on past the cut, at ${player.currentPositionMs}")
                assertTrue(player.currentPositionMs < 2_000L, "it stopped at ${player.currentPositionMs}")
            }
            player.dispose()
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
