package com.woka.app.shell.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.PinnedSession
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `Folders`: the linked roots by name, each opening into its sub-folders in place.
 *
 * The roots are drawn at once from their names and filled in with counts off the
 * main thread, so a slow or sleeping drive delays a number, never the list.
 */
@Composable
internal fun SidebarFolders(
    linked: List<String>,
    tree: SidebarTreeState,
    current: String?,
    menu: SidebarMenuRequest?,
    pinned: List<PinnedSession>,
    actions: SidebarActions
) {
    val placeholders = linked.sortedBy { File(it).name.lowercase() }
        .map { SidebarNode(it, File(it).name, mediaCount = 0, hasChildren = true) }
    val roots by produceState(placeholders, linked) {
        value = withContext(Dispatchers.IO) { placeholders.map { nodeOf(it.path) } }
    }
    val expanded by tree.expanded.collectAsState()
    val children by tree.children.collectAsState()

    SectionLabel("Folders")
    if (linked.isEmpty()) {
        RowLabel("Nothing linked yet", Modifier.padding(SidebarTokens.RowPadding), Theme.colors.textTertiary)
        return
    }
    val level = TreeLevel(expanded, children, current, menu, pinned, actions)
    level.Rows(roots, depth = 0)
}

/** The tree's fixed inputs, so the recursion passes one value and a depth. */
private class TreeLevel(
    val expanded: Set<String>,
    val children: Map<String, List<SidebarNode>>,
    val current: String?,
    val menu: SidebarMenuRequest?,
    val pinned: List<PinnedSession>,
    val actions: SidebarActions
) {
    @Composable
    fun Rows(nodes: List<SidebarNode>, depth: Int) {
        nodes.forEach { node ->
            val kind = if (depth == 0) SidebarRowKind.Root else SidebarRowKind.Folder
            val open = node.path in expanded
            Box {
                SidebarFolderRow(
                    model = FolderRowModel(node, depth, node.hasChildren, open, samePath(node.path, current)),
                    onClick = { actions.open(node.path) },
                    onToggle = { actions.toggle(node.path) },
                    onSecondaryClick = { at -> actions.menu(SidebarMenuRequest(node.path, kind, at)) }
                )
                if (menu != null && menu.kind == kind && menu.path == node.path) {
                    SidebarMenu(menu, pinned.any { samePath(it.path, node.path) }, actions)
                }
            }
            if (open) Rows(children[node.path].orEmpty(), depth + 1)
        }
    }
}

/**
 * `Pinned`: the places inside the roots the user keeps returning to, each with a
 * second line saying where it is. Absent, label and all, while nothing is pinned.
 */
@Composable
internal fun SidebarPinned(
    pinned: List<PinnedSession>,
    linked: List<String>,
    current: String?,
    menu: SidebarMenuRequest?,
    actions: SidebarActions
) {
    if (pinned.isEmpty()) return
    // Stat off the main thread; until it answers, every pin is assumed present.
    val present by produceState(pinned.map { it.path }.toSet(), pinned) {
        value = withContext(Dispatchers.IO) {
            pinned.map { it.path }.filter { File(it).isDirectory }.toSet()
        }
    }

    SectionLabel("Pinned")
    pinned.forEach { session ->
        Box {
            SidebarPinnedRow(
                session = session,
                label = pinnedParentLabel(session.path, linked),
                selected = samePath(session.path, current),
                exists = session.path in present,
                onClick = { actions.open(session.path) },
                onSecondaryClick = { at ->
                    actions.menu(SidebarMenuRequest(session.path, SidebarRowKind.Pinned, at))
                }
            )
            if (menu != null && menu.kind == SidebarRowKind.Pinned && menu.path == session.path) {
                SidebarMenu(menu, isPinned = true, actions = actions)
            }
        }
    }
}

/**
 * `.side-foot`: the one way in that is always visible, behind a hairline. While
 * nothing is linked it is [prominent] — filled with the accent, as the mockup's
 * first run draws it — so the sidebar and the Welcome card point at the same act.
 */
@Composable
internal fun SidebarFoot(prominent: Boolean, onLink: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
        Box(modifier = Modifier.padding(SidebarTokens.ListInset)) {
            val tone = if (prominent) Theme.colors.onAccent else Theme.colors.textSecondary
            val fill = if (!prominent) Modifier else Modifier.background(
                Theme.colors.accent, RoundedCornerShape(SidebarTokens.RowRadius)
            )
            Box(modifier = fill) {
                SidebarRowFrame(false, SidebarTokens.RowHeight, true, onLink, onSecondaryClick = {}) {
                    RowIcon(selected = false, tint = tone, icon = Icons.Static.Add)
                    RowLabel("Link Folder…", Modifier.weight(1f), tone)
                }
            }
        }
    }
}
