package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import io.iris.ui.theme.IrisTheme
import androidx.compose.ui.window.Dialog

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IrisTheme.colors.surface)
                .padding(24.dp)
        ) {
            BasicText(
                text = "Rename",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(IrisTheme.colors.background)
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    textStyle = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(IrisTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                if (newName.isNotBlank()) {
                                    onConfirm(newName)
                                }
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText("Cancel", style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 16.sp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (newName.isNotBlank()) IrisTheme.colors.accent else IrisTheme.colors.border)
                        .pointerHoverIcon(if (newName.isNotBlank()) PointerIcon.Hand else PointerIcon.Default)
                        .clickable(enabled = newName.isNotBlank(), onClick = { onConfirm(newName) })
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BasicText("Rename", style = TextStyle(color = IrisTheme.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}
