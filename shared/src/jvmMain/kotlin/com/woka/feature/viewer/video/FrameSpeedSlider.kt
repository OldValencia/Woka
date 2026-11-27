package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.components.frames.FrameSpeedScale
import com.woka.core.ui.components.resetOnRightClick
import com.woka.core.ui.theme.Theme

/**
 * An animation's speed (RD-501): the film's header and track, on the animation's own scale
 * — 0.25–5×, 1× at the centre, pulled onto 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2 / 3 / 4 / 5×
 * ([FrameSpeedScale]). Unlike the film's, a drag is heard at once: the clock only reads a
 * number, there is no audio graph to rebuild per step. A right-click goes back to 1×.
 */
@Composable
internal fun FrameSpeedSlider(speed: Float, onSpeedChange: (Float) -> Unit) {
    Column {
        PlayerSpeedHeader(speed)
        DetentSlider(
            fraction = FrameSpeedScale.fractionOf(speed),
            colors = DetentSliderColors(
                track = Theme.viewer.onScrimHover,
                detent = Theme.viewer.onScrimBorder,
                knob = Theme.viewer.onScrim
            ),
            onTap = { onSpeedChange(FrameSpeedScale.speedAt(it)) },
            modifier = Modifier.resetOnRightClick { onSpeedChange(1f) },
            detents = FrameSpeedScale.DETENTS.map(FrameSpeedScale::fractionOf)
        )
    }
}
