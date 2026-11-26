package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/** The knob under the open tab (RD-202, `.indicator`), split from `EditorTabBar` in RD-302. */
@Composable
internal fun TabIndicator(modifier: Modifier) {
    val shape = RoundedCornerShape(EditorTokens.Panel.tabRadius)
    Box(
        modifier
            .height(EditorTokens.Panel.tabHeight)
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
            .background(Theme.room.knob, shape)
    )
}

/**
 * One tab: a glyph over its name. [growth] is its share of the bar, 1 once it is in — less while
 * Clip grows in (RD-302, the prototype's `flexGrow` 0 → 1), and its opacity with it.
 */
@Composable
internal fun RowScope.TabButton(tab: EditorTab, selected: Boolean, enabled: Boolean, growth: Float, onSelect: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    val text = when {
        !enabled -> room.faint
        selected || hovered -> room.text
        else -> room.muted
    }
    Column(
        modifier = Modifier
            .weight(growth.coerceAtLeast(MIN_GROWTH))
            .alpha(growth.coerceIn(0f, 1f))
            .height(EditorTokens.Panel.tabHeight)
            .clip(RoundedCornerShape(EditorTokens.Panel.tabRadius))
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(tab.id) }
            )
            .semantics { this.selected = selected }
            .testTag("editor-tab-${tab.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Panel.tabLabelGap, Alignment.CenterVertically)
    ) {
        Image(
            painter = painterResource(tab.icon),
            contentDescription = null,
            modifier = Modifier.size(EditorTokens.Panel.tabGlyph),
            colorFilter = ColorFilter.tint(if (selected) room.accent else text)
        )
        BasicText(
            text = tab.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = text,
                fontSize = EditorTokens.Panel.tabLabel,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

/** A row weight must be positive; a tab at its first frame is this thin. */
internal const val MIN_GROWTH = 0.001f
