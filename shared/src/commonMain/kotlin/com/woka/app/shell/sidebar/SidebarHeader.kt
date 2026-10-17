package com.woka.app.shell.sidebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.woka.app.shell.ToolbarSidebarButton
import com.woka.core.ui.icons.Icons
import org.jetbrains.compose.resources.painterResource

/**
 * `.side-top`: the logo and the button that hides the sidebar (SIDE-003). It sits
 * in the title bar's row, so the caller wraps it in the window-drag area; the
 * toolbar carries the matching button that brings it back.
 */
@Composable
internal fun SidebarHeader(onHide: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SidebarTokens.HeaderHeight)
            .padding(start = SidebarTokens.HeaderStart, end = SidebarTokens.HeaderEnd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Icons.Static.Logo),
            contentDescription = "Woka",
            modifier = Modifier.height(SidebarTokens.LogoSize)
        )
        Spacer(Modifier.weight(1f))
        ToolbarSidebarButton("Hide sidebar", onClick = onHide)
    }
}
