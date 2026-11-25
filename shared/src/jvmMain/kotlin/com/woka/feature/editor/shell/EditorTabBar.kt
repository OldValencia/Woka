package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * The inspector's tabs (RD-201, `editor.css` `.tools`): one per [EditorTab], equal widths, in
 * a sunken well at the top of the panel. A glyph over its name — the rail's lesson
 * (2026-08-21) that two tools sharing a placeholder glyph are two identical squares holds
 * here too.
 *
 * [unavailable] is per file, as it was on the rail: a tab whose tool cannot apply to what is
 * open stays in place, faint, and says why on hover — a tab that vanished would move every
 * tab after it.
 */
@Composable
internal fun EditorTabBar(
    tabs: List<EditorTab>,
    selectedId: String?,
    unavailable: Map<String, String>,
    onSelect: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .padding(EditorTokens.Panel.tabsMargin)
            .fillMaxWidth()
            .clip(RoundedCornerShape(EditorTokens.Panel.tabsRadius))
            .background(Theme.room.track)
            .padding(EditorTokens.Panel.tabsPadding)
            .testTag("editor-tabs")
    ) {
        // The knob slides to the open tab (RD-202, `.indicator`), as the segments' does.
        val gap = EditorTokens.Panel.tabsGap
        val each = (maxWidth - gap * (tabs.size - 1).coerceAtLeast(0)) / tabs.size.coerceAtLeast(1)
        val index = tabs.indexOfFirst { it.id == selectedId }
        val offset by animateDpAsState((each + gap) * index.coerceAtLeast(0), spring(dampingRatio = 0.78f, stiffness = 420f))
        if (index >= 0) TabIndicator(Modifier.offset(x = offset).width(each))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            tabs.forEach { tab ->
                val reason = unavailable[tab.canvasTool.manifest.id]
                if (reason != null) {
                    ViewerTooltip(reason) { TabButton(tab, selected = false, enabled = false, onSelect) }
                } else {
                    TabButton(tab, selected = tab.id == selectedId, enabled = true, onSelect)
                }
            }
        }
    }
}

@Composable
private fun TabIndicator(modifier: Modifier) {
    val shape = RoundedCornerShape(EditorTokens.Panel.tabRadius)
    Box(
        modifier
            .height(EditorTokens.Panel.tabHeight)
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
            .background(Theme.room.knob, shape)
    )
}

@Composable
private fun RowScope.TabButton(tab: EditorTab, selected: Boolean, enabled: Boolean, onSelect: (String) -> Unit) {
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
            .weight(1f)
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
