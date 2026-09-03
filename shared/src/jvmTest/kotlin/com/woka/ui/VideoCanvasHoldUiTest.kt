package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.feature.editor.engine.video.HoldWhileDragging
import com.woka.support.MainDispatcherRule
import com.woka.support.episodeTwoFile
import com.woka.support.onOpenPlayer
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The picture stands still for the length of a drag (ME-207).
 *
 * **Against a real player**, because the rule is four state transitions and there is no
 * seam worth faking: what is being checked is that `isPlaying` goes down on the way into
 * a drag and comes back up on the way out — and *only* if it was up to begin with, which
 * is the half a naive implementation gets wrong.
 *
 * `document.pending != null` is what the engine passes as `dragging`; that wiring is one
 * expression in `VideoEditorEngine.Surface` and is not what this test is for.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class VideoCanvasHoldUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The decode loop reads `AppDependencies.settingsRepository` for playback resume. */
    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `a drag pauses a playing canvas and releases it back into play`() =
        onOpenPlayer(episodeTwoFile()) { player ->
            var dragging by mutableStateOf(false)
            player.isPlaying = true

            onUi {
                setContent { HoldWhileDragging(player, dragging) }
                waitForIdle()
                assertTrue(player.isPlaying, "composing the rule stopped a playing canvas")

                dragging = true
                waitForIdle()
                assertFalse(player.isPlaying, "the picture kept moving under the drag")

                dragging = false
                waitForIdle()
                assertTrue(player.isPlaying, "the drag never gave playback back")
            }
        }

    @Test
    fun `a drag over a paused canvas leaves it paused`() =
        onOpenPlayer(episodeTwoFile()) { player ->
            var dragging by mutableStateOf(false)
            player.isPlaying = false

            onUi {
                setContent { HoldWhileDragging(player, dragging) }
                waitForIdle()

                dragging = true
                waitForIdle()
                assertFalse(player.isPlaying)

                // The half that matters: releasing must not *start* a video the user had
                // stopped. Only a drag that took playback away may give it back.
                dragging = false
                waitForIdle()
                assertFalse(player.isPlaying, "releasing a drag started a paused canvas")
            }
        }
}
