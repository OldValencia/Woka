package com.woka.feature.editor.shell

import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolDomain

/**
 * The scope the engine's timeline is handed (RD-301), built where the timeline is placed: under
 * the canvas, docked, beside the inspector — no longer floating over the picture's foot inside
 * `EditorBottomPanel`, which keeps only the notices.
 *
 * **Opening a tool means opening its tab.** A tool that `joins` another — Crop into Transform —
 * lives in the owner's tab, so the tab is found by its members rather than by its id.
 */
internal fun timelineScopeOf(
    session: EditorSession,
    tools: List<EditorTool>,
    clipSelection: String?,
    tabs: EditorTabState,
    selection: ToolSelection
): EditorTimelineScope = EditorTimelineScopeImpl(
    session = session,
    tools = tools,
    selected = clipSelection,
    onOpenTool = { id, from -> tabFor(tabs.tabs, tabs.tab, id, from)?.let { tabs.choose(it.id) } },
    shapes = selection
)

/**
 * The tab holding tool [id] — or none while the [open] tab's tool is not of one of the domains
 * [from] names. A click on a clip opens Clip only from Transform or Markup (RD-301): from Styles
 * or Adjust the clip's own look is what the author is editing, and stays in view.
 */
internal fun tabFor(tabs: List<EditorTab>, open: EditorTab?, id: String, from: Set<ToolDomain>?): EditorTab? {
    if (from != null && open?.canvasTool?.manifest?.domain !in from) return null
    return tabs.firstOrNull { tab -> tab.tools.any { it.manifest.id == id } }
}
