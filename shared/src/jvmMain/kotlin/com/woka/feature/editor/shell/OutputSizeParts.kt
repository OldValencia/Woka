package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/** A field of whole pixels (`.output-size-fields`): its label above, the number in the room's panel. */
@Composable
internal fun OutputSizeField(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester = remember { FocusRequester() },
    onValueChange: (String) -> Unit
) {
    val tokens = EditorDialogTokens.Group
    val room = Theme.room
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tokens.fieldRadius)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(color = room.muted, fontSize = tokens.labelSize))
        BasicTextField(
            value = value,
            onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(6)) },
            singleLine = true,
            textStyle = TextStyle(color = room.text, fontSize = tokens.fieldText),
            cursorBrush = SolidColor(room.text),
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.fieldHeight)
                .clip(shape)
                .background(room.panel)
                .border(1.dp, if (focused) room.accentLine else room.line, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .testTag(tag)
        )
    }
}

/** `Keep proportions` (`.output-size-lock`): a box in the room's orange and its word. */
@Composable
internal fun OutputSizeLock(checked: Boolean, onChange: (Boolean) -> Unit) {
    val room = Theme.room
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .padding(top = EditorDialogTokens.Group.lockTop)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox
            ) { onChange(!checked) }
            .testTag("output-keep-proportions"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(shape)
                .background(if (checked) room.accent else room.panel)
                .border(1.dp, if (checked) room.accent else room.muted, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                val mark = room.onAccent
                Canvas(Modifier.size(10.dp)) {
                    val stroke = 1.8.dp.toPx()
                    val w = size.width
                    drawLine(mark, Offset(w * 0.08f, w * 0.52f), Offset(w * 0.4f, w * 0.82f), stroke, StrokeCap.Round)
                    drawLine(mark, Offset(w * 0.4f, w * 0.82f), Offset(w * 0.94f, w * 0.18f), stroke, StrokeCap.Round)
                }
            }
        }
        BasicText(
            "Keep proportions",
            style = TextStyle(color = room.text, fontSize = EditorDialogTokens.Group.labelSize)
        )
    }
}
