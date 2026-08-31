package com.woka.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * What a segmented control is painted with.
 *
 * Parameterised for the same reason `DetentSlider` already is: the defaults are read
 * from `Theme.colors` and describe a control on an app surface, where
 * `surfacePressed` is a groove and `border` is a hairline. Over a photograph both are
 * black at 8 % and neither exists.
 */
@Immutable
data class SegmentedControlColors(
    val track: Color,
    val indicator: Color,
    val indicatorBorder: Color,
    val selectedText: Color,
    val text: Color
)

object SegmentedControlDefaults {

    val onSurface: SegmentedControlColors
        @Composable get() = SegmentedControlColors(
            track = Theme.colors.surfacePressed,
            indicator = Theme.colors.surface,
            indicatorBorder = Theme.colors.border,
            selectedText = Theme.colors.textPrimary,
            text = Theme.colors.textSecondary
        )

    /** For a control drawn on a scrim over media — the editor's inspector. */
    val onScrim: SegmentedControlColors
        @Composable get() = SegmentedControlColors(
            track = Theme.viewer.onScrimHover,
            indicator = Theme.viewer.onScrimSelected,
            indicatorBorder = Theme.viewer.onScrimBorder,
            selectedText = Theme.viewer.onScrim,
            text = Theme.viewer.onScrimSecondary
        )
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    colors: SegmentedControlColors = SegmentedControlDefaults.onSurface
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colors.track)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                .clip(RoundedCornerShape(8.dp))
                .background(colors.indicator)
                .border(1.dp, colors.indicatorBorder, RoundedCornerShape(8.dp))
        )

        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.selectedText else colors.text,
                    animationSpec = tween(160)
                )
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = option,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
