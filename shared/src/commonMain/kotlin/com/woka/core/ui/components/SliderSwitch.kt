package com.woka.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * What a switch is painted with.
 *
 * No vendor prefix in the name: these are Woka's own components, taking their cues
 * from that design language rather than being branded with it. The composable keeps
 * its old name until ME-129 renames it across every call site.
 *
 * [on] stays green wherever the switch is used, including the editor: a control that
 * means "on" must not change colour by screen. [off] is what varies — on an app
 * surface `Theme.colors.border` reads as a groove, and over a photograph it vanishes.
 */
@Immutable
data class SwitchColors(
    val on: Color,
    val off: Color,
    val thumb: Color
)

object SwitchDefaults {

    /** For a switch on an ordinary app surface — Settings, dialogs, panels. */
    val onSurface: SwitchColors
        @Composable get() = SwitchColors(
            on = SWITCH_ON,
            off = Theme.colors.border,
            thumb = Theme.colors.surfaceElevated
        )

    /** For a switch drawn on a scrim over media — the editor's inspector. */
    val onScrim: SwitchColors
        @Composable get() = SwitchColors(
            on = SWITCH_ON,
            off = Theme.viewer.onScrimBorder,
            thumb = Theme.viewer.onScrim
        )
}

/** The system green every switch in the app has always used for "on". */
private val SWITCH_ON = Color(0xFF34C759)

@Composable
fun SliderSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.onSurface
) {
    val trackColor = if (checked) colors.on else colors.off
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = 250)
    )

    Box(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(27.dp)
                .clip(CircleShape)
                .background(colors.thumb)
        )
    }
}
