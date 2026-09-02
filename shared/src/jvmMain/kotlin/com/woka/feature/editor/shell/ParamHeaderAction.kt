package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/** Sized against the title's cap height, so the row does not grow to hold them. */
private val ACTION_SIZE = 20.dp
private val ACTION_GLYPH = 14.dp

/**
 * The header actions for a tool, built where the card's title is drawn (ME-174).
 *
 * **Its own `ParamWriter`**, and that is not a duplicate to keep in step: the one
 * `ParamRows` builds is made inside the content lambda and cannot be reached from the
 * title's row, and a writer is a stateless wrapper over the session and the tool — two
 * of them cannot disagree about anything.
 */
@Composable
internal fun InspectorHeaderActions(
    tool: EditorTool,
    session: EditorSession,
    picture: PictureFacts,
    selectedId: () -> String?
) {
    HeaderActions(tool.header(tool.params), ParamWriter(session, tool, picture, selectedId))
}

/**
 * Every action a tool put in its header, in the order it named them (ME-174).
 *
 * Anything that is not an action is ignored rather than guessed at: a header is for
 * things that *do*, and a slider there would have no room to be one.
 */
@Composable
internal fun HeaderActions(header: List<ParamSpec<*>>, writer: ParamWriter) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        header.filterIsInstance<ActionParam>().forEach { spec -> HeaderAction(spec, writer) }
    }
}

/**
 * One action in a card's header — an icon, and a tooltip that names it (ME-174).
 *
 * **The tooltip is the label, not a hint.** An action in the header has no room for its
 * own word without competing with the tabs on the same row, so the only place it says
 * what it does is on hover — the same rule `EditorPeekButton` follows, for the same
 * reason.
 *
 * A spec with no glyph is not drawn here at all. That is deliberate rather than a
 * fallback to text: a tool putting a wordless action in its header would silently get a
 * button nobody can read, and the shell has no art of its own to lend it.
 *
 * **No plate behind it** (2026-09-02). It had one, and beside a segmented control — which
 * is itself a filled pill — two filled squares read as objects that had wandered in.
 * Level with the card's own name, chrome is a glyph and nothing else; it brightens under
 * the pointer, which is the whole of the affordance a 15 dp icon needs.
 */
@Composable
internal fun HeaderAction(spec: ActionParam, writer: ParamWriter) {
    val glyph = spec.icon ?: return
    val hovered = remember { MutableInteractionSource() }
    val over by hovered.collectIsHoveredAsState()
    ViewerTooltip(spec.label) {
        Box(
            modifier = Modifier
                .size(ACTION_SIZE)
                .clip(RoundedCornerShape(6.dp))
                .hoverable(hovered)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { writer.edit(spec, true) }
                .testTag("param-${spec.key}"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(glyph),
                contentDescription = null,
                modifier = Modifier.size(ACTION_GLYPH),
                colorFilter = ColorFilter.tint(
                    if (over) Theme.viewer.onScrim else Theme.viewer.onScrimSecondary
                )
            )
        }
    }
}
