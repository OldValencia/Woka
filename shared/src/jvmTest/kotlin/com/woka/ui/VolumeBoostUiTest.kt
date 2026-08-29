package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.woka.core.data.MAX_MEDIA_VOLUME
import com.woka.feature.viewer.video.SOUND_BOOST_SWITCH_TAG
import com.woka.feature.viewer.video.VOLUME_SLIDER_TAG
import com.woka.feature.viewer.video.VolumeVerticalOverlay
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The volume popover with Sound boost off and on.
 *
 * **Driven through the real slider**, because the claim is that the *range* grows: a test
 * that read the ceiling off a function would pass with a slider still clamped to 1f, which
 * is exactly the shape of the bug — five separate `coerceIn(0f, 1f)` had to learn the same
 * ceiling and any one of them left behind caps the level silently.
 */
@OptIn(ExperimentalTestApi::class)
class VolumeBoostUiTest {

    @Test
    fun `with the boost off the top of the slider is 100 percent`() {
        onUi {
            var level = 0.5f
            popover(volume = level, soundBoost = false, onVolumeChange = { level = it })

            clickSlider(atFractionFromTop = 0f)
            assertEquals(1f, level, "the slider went past 100 % with the boost off")
        }
    }

    @Test
    fun `with the boost on the slider reaches the ceiling`() {
        onUi {
            var level = 0.5f
            popover(volume = level, soundBoost = true, onVolumeChange = { level = it })

            clickSlider(atFractionFromTop = 0f)
            assertEquals(MAX_MEDIA_VOLUME, level, "the boosted slider stopped short")
        }
    }

    /**
     * The detent: a range three times as tall must not make the ordinary setting harder to
     * land on, so a press just above 100 % is pulled onto it exactly.
     */
    @Test
    fun `a press near 100 percent snaps to it`() {
        onUi {
            var level = 0.5f
            popover(volume = level, soundBoost = true, onVolumeChange = { level = it })

            // 100 % sits a third of the way up, so it is two thirds down from the top.
            clickSlider(atFractionFromTop = (1f - 1f / MAX_MEDIA_VOLUME) - 0.02f)
            assertEquals(1f, level, "the press landed at $level instead of snapping to 100 %")
        }
    }

    @Test
    fun `the switch is in the popover and reports what it was set to`() {
        onUi {
            var enabled = false
            popover(volume = 0.5f, soundBoost = false, onSoundBoostChange = { enabled = it })

            onNodeWithTag(SOUND_BOOST_SWITCH_TAG).assertExists()
            onNodeWithTag(SOUND_BOOST_SWITCH_TAG).performClick()
            waitForIdle()
            assertTrue(enabled, "the switch did not report being turned on")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.popover(
    volume: Float,
    soundBoost: Boolean,
    onVolumeChange: (Float) -> Unit = {},
    onSoundBoostChange: (Boolean) -> Unit = {}
) {
    setContent {
        VolumeVerticalOverlay(
            isVisible = true,
            volume = volume,
            bottomPadding = 0.dp,
            soundBoost = soundBoost,
            onSoundBoostChange = onSoundBoostChange,
            onVolumeChange = onVolumeChange,
            onHover = {}
        )
    }
    waitForIdle()
}

/** A press-and-release on the track, [atFractionFromTop] of the way down it. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.clickSlider(atFractionFromTop: Float) {
    onNodeWithTag(VOLUME_SLIDER_TAG).performMouseInput {
        val at = Offset(width / 2f, height * atFractionFromTop)
        moveTo(at)
        press()
        release()
    }
    waitForIdle()
}
