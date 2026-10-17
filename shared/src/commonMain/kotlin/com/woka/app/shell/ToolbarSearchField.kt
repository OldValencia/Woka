package com.woka.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * `.search`: the toolbar's search box — a magnifier, the word and its shortcut.
 *
 * **It opens the search rather than taking text, for now.** Typing into the bar and
 * getting the results in the content is SIDE-004; until then the box is the door to
 * the existing overlay, shaped like the field it will become so the toolbar does not
 * change under the user twice. Under [LocalToolbarCompact] it shrinks to the icon.
 */
@Composable
fun ToolbarSearchField(onOpen: () -> Unit) {
    if (LocalToolbarCompact.current) {
        ToolbarIconButton(Icons.Static.Search, "Search", onClick = onOpen)
        return
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .width(170.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) Theme.colors.surfacePressed else Theme.colors.surfaceHover)
            .pointerHoverIcon(PointerIcon.Text)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onOpen)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(Icons.Static.Search),
            contentDescription = "Search",
            modifier = Modifier.size(13.dp),
            colorFilter = ColorFilter.tint(Theme.colors.textTertiary)
        )
        BasicText(
            "Search",
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Theme.colors.textTertiary, fontSize = 12.5.sp)
        )
        BasicText(
            "Ctrl+F",
            style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp)
        )
    }
}
