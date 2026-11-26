package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession

/** Whether the inspector is out — F6 and the header's panel button flip it (RD-201). */
internal class EditorPanelState {

    var shown by mutableStateOf(true)
        private set

    fun toggle() {
        shown = !shown
    }
}

/**
 * The editor's right-hand inspector (RD-201, `#editor/photo`): the tabs, the open tab's tools,
 * and `Revert to original…` at the foot, on the room's panel.
 *
 * It replaces the floating card and the rail under the picture. The picture keeps the room
 * to its left and the panel does not cover it, so nothing the user grabs on the canvas is
 * ever under a control.
 */
@Composable
internal fun EditorInspectorPanel(
    /** Who the edit is for, above the tabs (RD-204); null for a single picture. */
    scope: (@Composable () -> Unit)?,
    tabs: List<EditorTab>,
    tab: EditorTab?,
    unavailable: Map<String, String>,
    onSelectTab: (String) -> Unit,
    session: EditorSession,
    engine: EditorEngine,
    document: EditDocument,
    selectedId: () -> String?,
    canRevert: Boolean,
    onRevert: () -> Unit
) {
    val room = Theme.room
    Column(
        modifier = Modifier
            .width(EditorTokens.Panel.width)
            .fillMaxHeight()
            .background(room.panel)
            .testTag("editor-panel")
    ) {
        scope?.invoke()
        EditorTabBar(tabs, tab?.id, unavailable, onSelectTab)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(EditorTokens.Panel.bodyPadding),
            verticalArrangement = Arrangement.spacedBy(EditorTokens.Panel.sectionGap)
        ) {
            tab?.tools?.forEach { tool ->
                EditorToolSection(session, engine, document, tool, selectedId)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(room.line))
        RevertFoot(canRevert, onRevert)
    }
}

/**
 * `Revert to original…` — here rather than in the header, because it is about the whole
 * file and the header's left end is now Cancel's. Drawn while disabled, so its place is
 * learned before there is anything to revert.
 */
@Composable
private fun RevertFoot(enabled: Boolean, onRevert: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    Box(Modifier.padding(EditorTokens.Panel.footPadding)) {
        BasicText(
            text = "Revert to original…",
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (enabled && hovered) room.hoverStrong else Color.Transparent)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onRevert)
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("editor-revert"),
            style = TextStyle(
                color = when {
                    !enabled -> room.faint
                    hovered -> room.text
                    else -> room.muted
                },
                fontSize = EditorTokens.Panel.footLabel
            )
        )
    }
}
