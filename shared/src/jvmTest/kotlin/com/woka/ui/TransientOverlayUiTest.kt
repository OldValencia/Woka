package com.woka.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.woka.feature.viewer.shell.TransientOverlay
import com.woka.feature.viewer.shell.ViewerEvent
import com.woka.support.onUi
import kotlin.test.Test

private const val HUD = "hud"

/**
 * The flash HUD's gate.
 *
 * Play/pause reaches every file: a tap on the picture and the keyboard's Space both
 * raise the one event, and a photograph receives it like everything else. Answering
 * that with a pause glyph claims something stopped that was never moving, which is the
 * regression this gate exists to prevent — so the raise is left alone and the HUD is
 * told to stay down.
 */
@OptIn(ExperimentalTestApi::class)
class TransientOverlayUiTest {

    @Composable
    private fun Hud(event: ViewerEvent, enabled: Boolean) {
        TransientOverlay(
            event = event,
            durationMs = 400L,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            enabled = enabled
        ) {
            BasicText(HUD, modifier = Modifier.testTag(HUD))
        }
    }

    @Test
    fun `a raise draws the hud`() {
        val event = ViewerEvent()
        onUi {
            setContent { Hud(event, enabled = true) }
            onNodeWithTag(HUD).assertDoesNotExist()

            event.raise()
            waitForIdle()
            onNodeWithTag(HUD).assertIsDisplayed()
        }
    }

    @Test
    fun `the same raise draws nothing when there is nothing to answer it`() {
        val event = ViewerEvent()
        onUi {
            setContent { Hud(event, enabled = false) }

            event.raise()
            waitForIdle()
            mainClock.advanceTimeBy(200)
            onNodeWithTag(HUD).assertDoesNotExist()
        }
    }
}
