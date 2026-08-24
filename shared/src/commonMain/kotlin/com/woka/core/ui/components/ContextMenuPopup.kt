package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.theme.Theme

/** The width every context menu in the app is drawn at. */
val CONTEXT_MENU_WIDTH = 180.dp

/**
 * A context menu at a point: the popup, and the card the items sit on.
 *
 * The chrome — shadow, elevated surface, hairline, 6 dp of padding — was written
 * out at each call site; [MenuSurface] is that card, so a submenu opening beside its
 * parent is the same object rather than a copy that drifts.
 */
@Composable
fun ContextMenuPopup(
    offset: IntOffset,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss
    ) {
        MenuSurface(content = content)
    }
}

@Composable
fun MenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .width(CONTEXT_MENU_WIDTH)
            .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
            .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
            .padding(6.dp),
        content = content
    )
}
