package com.woka.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.engine.video.EDITOR_BAND_TAG
import com.woka.feature.editor.engine.video.VideoEditorBand
import com.woka.feature.editor.engine.video.VideoPlayerHolder
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.episodeTwoFile
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The band over a real player (ME-206).
 *
 * The shell's half — that a band is drawn at all, and only for an engine that fills the
 * slot — is `EditorBandUiTest` against a fixture. This is the other half: that what the
 * band draws is **this player's** position and transport, which needs a decoder and gets
 * one from the corpus.
 *
 * **What is deliberately not asserted here is the drag.** A scrub is a pointer gesture
 * over a slider whose travel is measured in pixels, and an assertion about where a swipe
 * landed tests the test's arithmetic more than the band's. It is on the by-hand list.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class VideoBandUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The decode loop reads `AppDependencies.settingsRepository` for playback resume. */
    @BeforeTest
    fun koin() = startKoinOnce()

    /**
     * The holder is opened the way the canvas opens it — paused, at the app's volume —
     * because that is the state the band has to draw, and closed in `finally` so a
     * failing assertion cannot leave a decoder running into the next test.
     */
    private fun onBand(block: (VideoPlayerHolder) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) {
                (holder.state?.durationMs ?: 0L) > 0L
            }
            block(holder)
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun `the band draws this player's duration`() = onBand { holder ->
        val duration = holder.state!!.durationMs

        onUi {
            setContent { VideoEditorBand(holder, Modifier) }
            waitForIdle()

            onNodeWithTag(EDITOR_BAND_TAG).assertExists()
            // 3 s of corpus, so the readout is "0:00 / 0:03" — asserted through the
            // formatter rather than against a literal, which would be a second copy of it.
            onNodeWithTag("editor-band-readout").assertExists()
            assertTrue(duration > 0L, "the corpus file reported no duration")
        }
    }

    @Test
    fun `the transport starts and stops the player it was given`() = onBand { holder ->
        val player = holder.state!!
        assertFalse(player.isPlaying, "the holder did not open paused")

        onUi {
            setContent { VideoEditorBand(holder, Modifier) }
            waitForIdle()

            onNodeWithTag("editor-band-transport").performClick()
            waitForIdle()
            assertTrue(player.isPlaying, "the transport did not start it")

            onNodeWithTag("editor-band-transport").performClick()
            waitForIdle()
            assertEquals(false, player.isPlaying, "the transport did not stop it again")
        }
    }
}
