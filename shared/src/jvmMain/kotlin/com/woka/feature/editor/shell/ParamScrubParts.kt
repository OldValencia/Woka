package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import org.jetbrains.compose.resources.painterResource

/**
 * The number, typed (`.control.scrub .number`): muted at the default, the room's text once
 * moved. Out-of-range text is kept and not written; the value settles — one history entry —
 * when the field lets go of focus or Enter is pressed.
 */
@Composable
internal fun ScrubField(spec: FloatParam, value: Float, changed: Boolean, writer: ParamWriter) {
    var text by remember { mutableStateOf(plainFloat(value)) }
    var focused by remember { mutableStateOf(false) }
    val keys = LocalCanvasKeys.current
    val focus = LocalFocusManager.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = plainFloat(value)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() || it == '-' || it == '.' }.take(7)
                text.toFloatOrNull()?.let { if (it in spec.range) writer.edit(spec, it, settled = false) }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = if (changed || focused) room.text else room.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(room.text),
            modifier = Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (focused) room.well2 else if (hovered) room.hoverStrong else Color.Transparent)
                .border(1.dp, if (focused) room.accentLine else Color.Transparent, RoundedCornerShape(5.dp))
                .hoverable(hover)
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .testTag("param-${spec.key}-field")
                .onFocusChanged {
                    if (focused && !it.isFocused) writer.settle()
                    focused = it.isFocused
                    keys?.fieldFocused = it.isFocused
                }
                .onPreviewKeyEvent { event ->
                    // Enter lets go of the field rather than reaching the editor's own keys.
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyDown) focus.clearFocus()
                    enter
                }
        )
        spec.unit?.let { unit ->
            BasicText(unit, Modifier.padding(start = 2.dp), style = TextStyle(color = room.faint, fontSize = 12.sp))
        }
    }
}

/** `↺` beside the number, shown on hover once the value has moved (`.scrub .control-reset`). */
@Composable
internal fun ScrubReset(visible: Boolean, tag: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(22.dp)
            .alpha(if (visible) 1f else 0f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (visible && hovered) Theme.room.hoverStrong else Color.Transparent)
            .hoverable(hover)
            .then(if (visible) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = visible, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.Reset),
            contentDescription = "Reset",
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(if (hovered) Theme.room.text else Theme.room.muted)
        )
    }
}
