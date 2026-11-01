package com.woka.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.components.resetOnRightClick
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.slider.MediaVerticalSlider
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A right-click puts a slider back to its resting value (asked for 2026-11-01), wired the
 * way the speed, zoom and volume sliders wire it: through the slider's own `modifier`.
 * The press lands far from the default, so a slider that took it as a tap would leave
 * the value there instead.
 */
@OptIn(ExperimentalTestApi::class)
class SliderResetUiTest {

    private fun rightClickAt(fractionX: Float, fractionY: Float): MouseInjectionScope.() -> Unit = {
        moveTo(Offset(width * fractionX, height * fractionY))
        press(MouseButton.Secondary)
        release(MouseButton.Secondary)
    }

    @Test
    fun `a right-click returns a detent slider to its default`() = onUi {
        var fraction by mutableStateOf(0.8f)
        setContent {
            DetentSlider(
                fraction = fraction,
                colors = DetentSliderColors(
                    track = Theme.colors.border,
                    detent = Theme.colors.border,
                    knob = Theme.colors.textPrimary
                ),
                onTap = { fraction = it },
                modifier = Modifier
                    .width(200.dp)
                    .testTag("slider")
                    .resetOnRightClick { fraction = 0.25f }
            )
        }
        onNodeWithTag("slider").performMouseInput(rightClickAt(0.9f, 0.5f))
        waitForIdle()
        assertEquals(0.25f, fraction)
    }

    @Test
    fun `a right-click returns the volume slider to 100 percent`() = onUi {
        var volume by mutableStateOf(0.3f)
        setContent {
            MediaVerticalSlider(
                value = volume,
                onValueChange = { volume = it },
                modifier = Modifier
                    .width(16.dp)
                    .height(80.dp)
                    .testTag("volume")
                    .resetOnRightClick { volume = 1f }
            )
        }
        onNodeWithTag("volume").performMouseInput(rightClickAt(0.5f, 0.9f))
        waitForIdle()
        assertEquals(1f, volume)
    }
}
