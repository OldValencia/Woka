package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource
import com.woka.feature.editor.core.FloatParam

/**
 * The bottom line of a value popover: type the number, or put it back (ME-155).
 *
 * Split from `ParamValuePopover` at the size mandate, along the seam that was already
 * there — the card and the slider on that side, the two small controls under them on
 * this one. Nothing moved changed.
 */

/**
 * The number, typed.
 *
 * **Text out of range is kept and not written.** Clamping the field's own string as it is
 * typed is how `-` becomes `0` before the digits arrive and a range starting at 12 makes
 * `120` unreachable; the value only leaves here once the string parses inside the range.
 */
@Composable
internal fun ValueField(spec: FloatParam, value: Float, writer: ParamWriter) {
    var text by remember { mutableStateOf(plainFloat(value)) }
    // Kept honest when the value moves from the slider, a stepper end or a reset.
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = plainFloat(value)
    }

    Row(
        modifier = Modifier
            .width(FIELD_WIDTH)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.viewer.onScrimHover)
            .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(8.dp))
            // The same gesture the track and the chips answer: right-click is reset
            // wherever a value is shown, so nothing has to be remembered per control.
            .resetOnRightClick { writer.resetTo(spec) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() || it == '-' || it == '.' }.take(7)
                text.toFloatOrNull()?.let { if (it in spec.range) writer.edit(spec, it) }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Theme.viewer.onScrim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Theme.viewer.onScrim),
            modifier = Modifier
                .weight(1f)
                .testTag("param-${spec.key}-field")
                .onPreviewKeyEvent { event ->
                    // **Enter is swallowed, not acted on.** The panel is a sibling of the
                    // inspector rather than a popup, so there is nothing for a key to
                    // dismiss — and letting it through would reach the editor's own
                    // `Ctrl+S`/`Enter`, which is `Done`.
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    enter && event.type == KeyEventType.KeyDown
                }
        )
        val unit = spec.unit
        if (unit != null) {
            BasicText(
                text = unit,
                style = TextStyle(color = Theme.viewer.onScrimDisabled, fontSize = 11.sp)
            )
        }
    }
}

/** The written-out twin of the right-click every control already answers (`ParamReset`). */
@Composable
internal fun ResetButton(enabled: Boolean, tag: String, onClick: () -> Unit) {
    ViewerTooltip("Reset") {
        Box(
            modifier = Modifier
                .size(RESET_SIZE)
                .clip(RoundedCornerShape(7.dp))
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(enabled = enabled, onClick = onClick)
                .testTag(tag),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Icons.Static.Reset),
                contentDescription = null,
                modifier = Modifier.size(RESET_GLYPH),
                colorFilter = ColorFilter.tint(
                    if (enabled) Theme.colors.accentEditor else Theme.viewer.onScrimDisabled
                )
            )
        }
    }
}

/** Sized against the field beside it, not against the word it replaced. */
private val RESET_SIZE = 22.dp
private val RESET_GLYPH = 13.dp

private val FIELD_WIDTH = 84.dp
