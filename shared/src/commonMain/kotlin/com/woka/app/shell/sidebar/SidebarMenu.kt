package com.woka.app.shell.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import com.woka.core.ui.components.ContextMenuPopup
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.theme.Theme

/** Which of the sidebar's three kinds of row was right-clicked. */
internal enum class SidebarRowKind { Root, Folder, Pinned }

/**
 * An open right-click menu: the row it belongs to and where the press landed, in
 * that row's own coordinates. The menu is drawn inside the row, which is what makes
 * a row-local offset the right one (`ContextMenuPopup` anchors to its parent).
 */
@Immutable
internal data class SidebarMenuRequest(
    val path: String,
    val kind: SidebarRowKind,
    val offset: IntOffset
)

/** Everything the sidebar can do, gathered so a row and its menu take one value. */
@Immutable
internal data class SidebarActions(
    val open: (String) -> Unit,
    val toggle: (String) -> Unit,
    val pin: (String) -> Unit,
    val unpin: (String) -> Unit,
    val unlink: (String) -> Unit,
    val menu: (SidebarMenuRequest?) -> Unit
)

/**
 * The right-click menu of a sidebar row. Each kind answers the one question it can:
 * a root can be unlinked, a folder inside one pinned or unpinned, a pin unpinned.
 *
 * **Unlink takes effect at once and is red**, as it was on Home's card. It also
 * sweeps the watch history and remembered tracks under the root; SIDE-007 gives it
 * an undo rather than a confirm dialog, which would tax every use to guard the rare
 * mistake (`home-rethink`, 2026-09-20).
 */
@Composable
internal fun SidebarMenu(
    request: SidebarMenuRequest,
    isPinned: Boolean,
    actions: SidebarActions
) {
    val dismiss = { actions.menu(null) }
    ContextMenuPopup(offset = request.offset, onDismiss = dismiss) {
        when (request.kind) {
            SidebarRowKind.Root -> DropdownItem("Unlink Folder", textColor = Theme.colors.danger) {
                dismiss()
                actions.unlink(request.path)
            }
            SidebarRowKind.Folder -> if (isPinned) {
                DropdownItem("Unpin") { dismiss(); actions.unpin(request.path) }
            } else {
                DropdownItem("Pin") { dismiss(); actions.pin(request.path) }
            }
            SidebarRowKind.Pinned -> DropdownItem("Unpin") {
                dismiss()
                actions.unpin(request.path)
            }
        }
    }
}
