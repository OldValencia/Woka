package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * A number between two steppers, clamped to its range.
 *
 * A whole component rather than two buttons at the call site because the clamping is
 * the part that gets forgotten: a `−` that can walk the value below the range writes a
 * document the spec's own `decode` then silently corrects, and the control and the
 * value disagree until the panel is reopened.
 *
 * [unit] is rendered **inside** the field, dimmer and smaller, attached to the number
 * it belongs to. The readout column means "the current value" on every other row, and
 * putting `px` there while a slider puts `+36%` there makes one column mean two things.
 */
@Composable
internal fun IntStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    testTag: String,
    unit: String? = null,
    step: Int = 1
) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(8.dp))
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton("−", "$testTag-minus", enabled = value > range.first) {
            onChange((value - step).coerceIn(range))
        }
        Box(
            modifier = Modifier.widthIn(min = 66.dp).height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BasicText(
                    text = "$value",
                    style = TextStyle(color = Theme.viewer.onScrim, fontSize = 12.sp)
                )
                if (unit != null) {
                    BasicText(
                        text = unit,
                        style = TextStyle(color = Theme.viewer.onScrimDisabled, fontSize = 10.5.sp)
                    )
                }
            }
        }
        StepperButton("+", "$testTag-plus", enabled = value < range.last) {
            onChange((value + step).coerceIn(range))
        }
    }
}

@Composable
private fun StepperButton(label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 26.dp)
            .testTag(tag)
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                        .background(Theme.viewer.onScrimHover.copy(alpha = 0f))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) Theme.viewer.onScrim else Theme.viewer.onScrimDisabled,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
