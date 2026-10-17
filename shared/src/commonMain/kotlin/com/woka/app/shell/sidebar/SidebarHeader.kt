package com.woka.app.shell.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.woka.app.shell.ToolbarSidebarButton
import com.woka.core.ui.theme.Theme

/**
 * `.side-top`: the logo square, the app's name, and the button that hides the
 * sidebar (SIDE-003). It sits in the title bar's row, so the caller wraps it in the
 * window-drag area; the toolbar carries the matching button that brings it back.
 */
@Composable
internal fun SidebarHeader(onHide: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SidebarTokens.HeaderHeight)
            .padding(start = SidebarTokens.HeaderStart, end = SidebarTokens.HeaderEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SidebarTokens.RowPadding)
    ) {
        Box(
            modifier = Modifier
                .size(SidebarTokens.LogoSize)
                .background(
                    Brush.linearGradient(listOf(Theme.colors.accent, SidebarTokens.LogoGradientEnd)),
                    RoundedCornerShape(SidebarTokens.LogoRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                "W",
                style = TextStyle(
                    color = Color.White,
                    fontSize = SidebarTokens.SubSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        BasicText(
            "Woka",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = SidebarTokens.LabelSize,
                fontWeight = FontWeight.SemiBold
            )
        )
        ToolbarSidebarButton("Hide sidebar", onClick = onHide)
    }
}
