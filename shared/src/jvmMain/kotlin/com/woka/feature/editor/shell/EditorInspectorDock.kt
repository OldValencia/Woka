package com.woka.feature.editor.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession

/**
 * The inspector's column beside the canvas (RD-201): out from nothing on entry, so the panel
 * arrives beside the picture rather than appearing, and in again on F6. Out of `EditorScreen`
 * when RD-302 pushed it past the size mandate.
 */
@Composable
internal fun EditorInspectorDock(
    panel: EditorPanelState,
    scope: (@Composable () -> Unit)?,
    tabs: EditorTabState,
    unavailable: Map<String, String>,
    session: EditorSession,
    engine: EditorEngine,
    document: EditDocument,
    selection: ToolSelection,
    selectedClip: String?,
    canRevert: Boolean,
    onRevert: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val panelWidth by animateDpAsState(
        if (entered && panel.shown) EditorTokens.Panel.width + 1.dp else 0.dp,
        tween(250, easing = FastOutSlowInEasing)
    )
    Box(modifier = Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
        Row(modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.room.line))
            EditorInspectorPanel(
                scope = scope,
                tabs = tabs.shown,
                tab = tabs.tab,
                unavailable = unavailable,
                onSelectTab = tabs::choose,
                session = session,
                engine = engine,
                document = document,
                selectedId = { selection.id },
                selectedClip = selectedClip,
                canRevert = canRevert,
                onRevert = onRevert
            )
        }
    }
}
