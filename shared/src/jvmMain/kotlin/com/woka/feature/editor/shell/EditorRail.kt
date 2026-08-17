package com.woka.feature.editor.shell

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * The tool rail: one button per enabled tool, grouped by `ToolDomain`.
 *
 * **The dividers come from the grouping, never from a count.** A domain with no
 * enabled tools contributes no divider — otherwise switching the last colour tool off
 * in Settings leaves a line with nothing on either side of it.
 *
 * [unavailable] is *per file*, not per setting: a tool switched off in Settings leaves
 * the registry and never reaches this list at all (§3.4). What lands here is "this
 * tool cannot apply to what you have open" — an HDR output with no encoder for the
 * format — which is why the reason is a string rather than a boolean.
 */
@Composable
internal fun EditorRail(
    tools: List<EditorTool>,
    activeToolId: String?,
    unavailable: Map<String, String>,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(EditorTokens.Rail.containerRadius))
            .background(Theme.viewer.popoverScrim)
            .padding(EditorTokens.Rail.containerPadding)
            .testTag("editor-rail"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.Rail.itemGap)
    ) {
        tools.forEachIndexed { index, tool ->
            val previous = tools.getOrNull(index - 1)
            if (previous != null && previous.manifest.domain != tool.manifest.domain) {
                RailDivider()
            }
            RailButton(
                tool = tool,
                selected = tool.manifest.id == activeToolId,
                reason = unavailable[tool.manifest.id],
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun RailDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = EditorTokens.Rail.dividerGap)
            .width(EditorTokens.Rail.dividerWidth)
            .height(EditorTokens.Rail.dividerHeight)
            .background(Theme.viewer.onScrimBorder)
            .testTag("editor-rail-divider")
    )
}

@Composable
private fun RailButton(
    tool: EditorTool,
    selected: Boolean,
    reason: String?,
    onSelect: (String) -> Unit
) {
    val enabled = reason == null
    val tint = when {
        !enabled -> Theme.viewer.onScrimDisabled
        selected -> Theme.colors.accentEditor
        else -> Theme.viewer.onScrimSecondary
    }
    ViewerTooltip(reason ?: tool.manifest.title) {
        Box(
            modifier = Modifier
                .size(EditorTokens.Rail.itemSize)
                .clip(RoundedCornerShape(EditorTokens.Rail.itemRadius))
                .background(if (selected) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover.copy(alpha = 0f))
                .then(
                    if (enabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onSelect(tool.manifest.id) }
                    } else {
                        Modifier
                    }
                )
                .testTag("editor-tool-${tool.manifest.id}"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(tool.manifest.icon),
                contentDescription = tool.manifest.title,
                modifier = Modifier.size(EditorTokens.Rail.iconSize),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}
