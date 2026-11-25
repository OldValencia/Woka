package com.woka.feature.editor.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditorTool

/**
 * Which tab is open (RD-201). **One always is** — the prototype's inspector never stands
 * empty — so with nothing chosen yet, or the chosen tab gone (switched off in Settings,
 * not applicable to this scope), the first available one is shown.
 */
internal class EditorTabState(
    val tabs: List<EditorTab>,
    private val blocked: Map<String, String>,
    private val chosen: MutableState<String?>
) {
    private fun available(tab: EditorTab) = tab.canvasTool.manifest.id !in blocked

    val tab: EditorTab?
        get() = tabs.firstOrNull { it.id == chosen.value && available(it) } ?: tabs.firstOrNull(::available)

    fun choose(id: String) {
        chosen.value = id
    }
}

@Composable
internal fun rememberEditorTabs(tools: List<EditorTool>, blocked: Map<String, String>): EditorTabState {
    // The choice outlives a rebuilt list — a tool switched off elsewhere leaves this tab open.
    val chosen = remember { mutableStateOf<String?>(null) }
    return remember(tools, blocked) { EditorTabState(tabsOf(tools), blocked, chosen) }
}
