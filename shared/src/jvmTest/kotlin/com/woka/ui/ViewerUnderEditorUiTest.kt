package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.app.shell.EditorPresence
import com.woka.feature.viewer.shell.EditorPauseEffect
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.probe.generateClip
import com.woka.support.startKoinOnce
import com.woka.support.onUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The viewer's film never plays under an open editor (found by hand, 2026-11-27): a click
 * that slipped through the inspector's hairline started it. Whatever starts it, it is stopped;
 * and only the pause made as the editor opened is undone when it closes.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerUnderEditorUiTest {

    private fun withPlayer(block: (VideoPlayerState) -> Unit) {
        startKoinOnce()
        val dir = Files.createTempDirectory("woka-under-editor").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val film = File(dir, "clip.mp4")
            generateClip(film, "libopenh264", seconds = 2, gop = 30)
            val player = VideoPlayerState(scope, film, initialVolume = 0f).apply { isPlaying = false }
            try {
                block(player)
            } finally {
                player.dispose()
            }
        } finally {
            scope.cancel()
            EditorPresence.closed()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a start under the editor is stopped, and is not resumed when it closes`() = withPlayer { player ->
        onUi {
            setContent { EditorPauseEffect(player) }
            EditorPresence.opened {}
            waitForIdle()

            player.isPlaying = true
            waitForIdle()
            assertFalse(player.isPlaying, "the viewer's film played under the editor")

            EditorPresence.closed()
            waitForIdle()
            assertFalse(player.isPlaying, "a film paused before Edit came back playing")
        }
    }

    @Test
    fun `a film playing when Edit was pressed plays again on the way out`() = withPlayer { player ->
        onUi {
            setContent { EditorPauseEffect(player) }
            player.isPlaying = true
            waitForIdle()
            EditorPresence.opened {}
            waitForIdle()
            assertFalse(player.isPlaying)

            player.isPlaying = true
            waitForIdle()
            assertFalse(player.isPlaying, "a second start slipped through")

            EditorPresence.closed()
            waitForIdle()
            assertTrue(player.isPlaying, "the pause the editor made was not undone")
        }
    }
}
