package com.woka.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.search.SearchState
import org.jetbrains.compose.resources.painterResource

/**
 * `.search`: the toolbar's search box (SIDE-008). It takes text, and while it holds
 * any the results replace the grid. Esc empties it and gives the folder back; Ctrl+F
 * focuses it from anywhere (`MainShell`). Narrower under [LocalToolbarCompact].
 */
@Composable
fun ToolbarSearchField() {
    val focusManager = LocalFocusManager.current
    val query = SearchState.query
    Row(
        modifier = Modifier
            .width(if (LocalToolbarCompact.current) 120.dp else 180.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.colors.surfaceHover)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(Icons.Static.Search),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            colorFilter = ColorFilter.tint(Theme.colors.textTertiary)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                BasicText(
                    "Search",
                    style = TextStyle(color = Theme.colors.textTertiary, fontSize = 12.5.sp)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { SearchState.query = it },
                singleLine = true,
                textStyle = TextStyle(color = Theme.colors.textPrimary, fontSize = 12.5.sp),
                cursorBrush = SolidColor(Theme.colors.accent),
                modifier = Modifier
                    .focusRequester(SearchState.focus)
                    .onPreviewKeyEvent { event ->
                        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                        if (!escape) return@onPreviewKeyEvent false
                        SearchState.query = ""
                        focusManager.clearFocus()
                        true
                    }
            )
        }
        if (query.isNotEmpty()) {
            BasicText(
                "✕",
                modifier = Modifier.clickable { SearchState.query = "" },
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp)
            )
        } else if (!LocalToolbarCompact.current) {
            BasicText("Ctrl+F", style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp))
        }
    }
}
