package io.iris.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.iris.ui.theme.IrisTheme

@Composable
fun AbstractDialog(
    onDismissRequest: () -> Unit,
    width: Dp = 300.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .shadow(24.dp, RoundedCornerShape(12.dp))
                .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .width(width)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun DialogTitle(text: String, color: Color) {
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    )
}

@Composable
fun DialogButton(text: String, onClick: () -> Unit, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
fun InformationDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 300.dp,
    closeText: String = "Close",
    content: @Composable ColumnScope.() -> Unit
) {
    AbstractDialog(onDismissRequest = onDismiss, width = width) {
        DialogTitle(text = title, color = IrisTheme.colors.textPrimary)
        content()
        Box(modifier = Modifier.align(Alignment.End)) {
            DialogButton(text = closeText, onClick = onDismiss, color = IrisTheme.colors.accent)
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    width: Dp = 300.dp,
    confirmText: String = "Yes",
    dismissText: String = "No",
    content: @Composable ColumnScope.() -> Unit
) {
    AbstractDialog(onDismissRequest = onDismiss, width = width) {
        DialogTitle(text = title, color = IrisTheme.colors.textPrimary)
        content()
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogButton(text = dismissText, onClick = onDismiss, color = IrisTheme.colors.textSecondary)
            DialogButton(text = confirmText, onClick = onConfirm, color = IrisTheme.colors.danger)
        }
    }
}

@Composable
fun ErrorDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 300.dp,
    closeText: String = "Close",
    content: @Composable ColumnScope.() -> Unit
) {
    AbstractDialog(onDismissRequest = onDismiss, width = width) {
        DialogTitle(text = title, color = IrisTheme.colors.danger)
        content()
        Box(modifier = Modifier.align(Alignment.End)) {
            DialogButton(text = closeText, onClick = onDismiss, color = IrisTheme.colors.danger)
        }
    }
}
