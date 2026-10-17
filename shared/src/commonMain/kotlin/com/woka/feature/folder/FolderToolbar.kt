package com.woka.feature.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.dp
import com.woka.app.shell.LocalTopBarAccessories
import com.woka.app.shell.ToolbarIconButton
import com.woka.core.ui.components.MenuSurface
import com.woka.core.ui.icons.Icons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The folder's controls in the toolbar: **View** and **Pin**, nothing else.
 *
 * The first cut (SIDE-003, 2026-10-17) put the filter, sort and a size slider in the
 * bar itself, and from the running app the bar read as crowded: at the default
 * window the filter alone ran over the path, and every control competed with the
 * folder's name. They share one button now, the way Finder's View Options do —
 * each is set rarely and read never, so none earns a place in the bar.
 *
 * Claimed by key on [path] and registered once; the values are read live through
 * `rememberUpdatedState`, so a filter change recomposes the controls rather than
 * tearing the slot down and building it again.
 */
@Composable
internal fun FolderToolbarSetup(
    path: String,
    state: FolderState,
    isPinned: Boolean,
    onTogglePin: () -> Unit
) {
    val accessories = LocalTopBarAccessories.current
    val current by rememberUpdatedState(state)
    val pinned by rememberUpdatedState(isPinned)
    val togglePin by rememberUpdatedState(onTogglePin)

    DisposableEffect(path) {
        val key = "folder_$path"
        accessories.setRight(key) {
            FolderToolbar(current, pinned, path, togglePin)
        }
        onDispose { accessories.clearRight(key) }
    }
}

@Composable
private fun FolderToolbar(
    state: FolderState,
    isPinned: Boolean,
    path: String,
    onTogglePin: () -> Unit
) {
    var viewOpen by remember { mutableStateOf(false) }
    // A click on the button while the panel is open is a click outside the panel:
    // the popup dismisses first, then the click lands on the button and reopens it.
    // A dismissal that just happened therefore swallows the click that caused it —
    // the guard `LibraryLeftAccessory` used for the dock's filter menu (2026-10-17).
    var justDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            ToolbarIconButton(
                icon = Icons.Static.Filter,
                description = "View",
                onClick = { if (!justDismissed) viewOpen = !viewOpen },
                active = viewOpen
            )
            if (viewOpen) {
                // Not `ContextMenuPopup`: its surface is the 180.dp of a context menu,
                // and the three segmented rows need the panel's own width.
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, 34),
                    onDismissRequest = {
                        viewOpen = false
                        justDismissed = true
                        scope.launch {
                            delay(150.milliseconds)
                            justDismissed = false
                        }
                    }
                ) {
                    MenuSurface(Modifier.width(VIEW_OPTIONS_WIDTH)) {
                        FolderViewOptions(path, state)
                    }
                }
            }
        }
        ToolbarIconButton(
            icon = Icons.Static.Pin,
            description = if (isPinned) "Unpin" else "Pin",
            onClick = onTogglePin,
            active = isPinned
        )
    }
}
