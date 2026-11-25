package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolDomain
import org.jetbrains.compose.resources.DrawableResource

/**
 * The order the inspector's tabs come in, by domain (RD-201): the prototype puts a film's
 * Clip first and the colour tools before Transform. **Not** `ToolDomain`'s own order, which
 * is the fold's application order and must not move for a layout.
 */
private val TAB_DOMAINS = listOf(
    ToolDomain.Time,
    ToolDomain.Color,
    ToolDomain.Geometry,
    ToolDomain.Annotate,
    ToolDomain.Sound,
    ToolDomain.Output
)

/**
 * One tab of the inspector: a tool, or several that share a panel (RD-201).
 *
 * Named and drawn after the tool that owns it — the one the others `joins`. Transform is
 * the case: Crop's frame and rows, then Transform's rotate and flip, under one tab.
 */
@Immutable
internal class EditorTab(
    val id: String,
    val title: String,
    val icon: DrawableResource,
    val tools: List<EditorTool>
) {
    /**
     * The tool the canvas answers to while this tab is open: the one that draws over the
     * picture if there is one, else the first. Only one tool can hold the canvas.
     */
    val canvasTool: EditorTool
        get() = tools.firstOrNull { it is CanvasTool } ?: tools.first()
}

/**
 * The tabs for [tools], which arrive sorted by the registry.
 *
 * A tool with `tabbed = false` has none; a tool that `joins` another goes into that one's
 * tab — or takes its own when the owner is switched off in Settings, so Crop survives
 * Transform being turned off. Pure, so the grouping is asserted without a window
 * (`EditorTabsTest`).
 */
internal fun tabsOf(tools: List<EditorTool>): List<EditorTab> {
    val tabbed = tools.filter { it.manifest.tabbed }
    val ids = tabbed.map { it.manifest.id }.toSet()
    val owners = tabbed.filter { it.manifest.joins == null || it.manifest.joins !in ids }
    return owners
        .sortedWith(compareBy({ TAB_DOMAINS.indexOf(it.manifest.domain) }, { it.manifest.order }))
        .map { owner ->
            val members = tabbed.filter { it.manifest.joins == owner.manifest.id }
            EditorTab(
                id = owner.manifest.id,
                title = owner.manifest.title,
                icon = owner.manifest.icon,
                // The joining tools first: Crop's frame is what Transform's tab opens on.
                tools = members + owner
            )
        }
}
