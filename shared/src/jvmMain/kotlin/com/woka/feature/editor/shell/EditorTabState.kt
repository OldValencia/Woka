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
import androidx.compose.runtime.LaunchedEffect
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
 *
 * [hidden] tabs are left out of [shown] — Clip while no clip is selected (RD-302) — but stay in
 * [tabs], so a clip click from Transform finds Clip in the same event that selects the clip.
 */
internal class EditorTabState(
    val tabs: List<EditorTab>,
    private val blocked: Map<String, String>,
    private val chosen: MutableState<String?>,
    private val hidden: Set<String> = emptySet()
) {
    private fun available(tab: EditorTab) = tab.canvasTool.manifest.id !in blocked

    /** The tabs the bar draws. */
    val shown: List<EditorTab> = tabs.filter { it.id !in hidden }

    val tab: EditorTab?
        get() = shown.firstOrNull { it.id == chosen.value && available(it) } ?: shown.firstOrNull(::available)

    fun choose(id: String) {
        chosen.value = id
    }
}

@Composable
internal fun rememberEditorTabs(tools: List<EditorTool>, blocked: Map<String, String>, hidden: Set<String> = emptySet()): EditorTabState {
    // The choice outlives a rebuilt list — a tool switched off elsewhere leaves this tab open.
    val chosen = remember { mutableStateOf<String?>(null) }
    // A hidden tab is forgotten, not only skipped: the next clip selection must not jump back
    // to Clip (the prototype's `scope-all` sends Clip to Styles).
    LaunchedEffect(hidden) {
        if (chosen.value in hidden) chosen.value = null
    }
    return remember(tools, blocked, hidden) { EditorTabState(tabsOf(tools), blocked, chosen, hidden) }
}
