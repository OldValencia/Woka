package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.shell.LocalCanvasOverride
import com.woka.app.shell.LocalTopBarAccessories
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.CanvasZoomPill

/**
 * The editor takes over the **shell's** top bar rather than drawing a second one (RD-201,
 * `#editor/photo`): Cancel, undo and redo on the left; the viewer's pill in the middle;
 * Original, `?`, the inspector's toggle, `Create a copy…` and Done on the right (RD-401). No Back chevron and no Revert —
 * Cancel is the way out, and Revert sits at the inspector's foot.
 *
 * The centre is **not** claimed. The viewer's pill is already there and the editor only
 * repoints it (`CanvasOverrideState`); opened straight from the grid there is no pill to
 * borrow, so it brings one.
 *
 * **Everything is saved and restored**, for the reason `EditorKeyEffect` documents: the
 * viewer registers its accessories in a `DisposableEffect(Unit)` that will not re-run when
 * this one overwrites them, so clearing to null on dispose would leave the viewer underneath
 * with no controls and a Back button that exits the folder.
 */
@Composable
internal fun EditorTopBarSetup(
    isEdited: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    peek: PeekState,
    panel: EditorPanelState,
    onLeave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    /** `?`: the help sheet, which the screen owns (RD-401). */
    onHelp: () -> Unit = {}
) {
    val accessories = LocalTopBarAccessories.current
    val override = LocalCanvasOverride.current

    // Registered once, reading live values — re-registering per keystroke would rebuild
    // the accessory while the user is looking at it.
    val curCanUndo by rememberUpdatedState(canUndo)
    val curCanRedo by rememberUpdatedState(canRedo)
    val curPeek by rememberUpdatedState(peek)
    val curPanel by rememberUpdatedState(panel)
    val curLeave by rememberUpdatedState(onLeave)
    val curUndo by rememberUpdatedState(onUndo)
    val curRedo by rememberUpdatedState(onRedo)
    val curDone by rememberUpdatedState(onDone)
    val curHelp by rememberUpdatedState(onHelp)
    val curChoices by rememberUpdatedState(LocalDoneChoices.current)

    // The edited mark lives in the Information panel (§4.4): **the document, not
    // `isDirty`** (2026-08-19) — an edited picture opened clean still reads as edited.
    SideEffect { override.edited = isEdited }

    DisposableEffect(Unit) {
        val previousLeading = accessories.leadingAccessory
        val previousCenter = accessories.centerAccessory
        val previousTrailing = accessories.centerTrailingAccessory
        val previousRight = accessories.rightAccessory
        val previousBack = accessories.backActionOverride

        override.editing = true
        accessories.leadingAccessory = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.controlGap)) {
                TopBarButton("Cancel", "editor-cancel", onClick = { curLeave() })
                TopBarIconButton(Icons.Static.Undo, "Undo", "editor-undo", enabled = curCanUndo, onClick = { curUndo() })
                TopBarIconButton(Icons.Static.Redo, "Redo", "editor-redo", enabled = curCanRedo, onClick = { curRedo() })
            }
        }
        // Empty centre means no viewer underneath — opened from the grid's context menu.
        if (previousCenter == null) accessories.centerAccessory = { CanvasZoomPill() }
        accessories.centerTrailingAccessory = null
        accessories.rightAccessory = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.controlGap)) {
                EditorPeekButton(curPeek)
                TopBarGlyphButton("?", "Tips for this task", "editor-help-button", onClick = { curHelp() })
                TopBarIconButton(Icons.Static.PanelToggle, "Show or hide the panel (F6)", "editor-panel-toggle", onClick = { curPanel.toggle() })
                HeaderDivider()
                CreateCopyButton { curChoices.onCopy() }
                DoneButton({ curDone() }, curChoices)
            }
        }
        // Cancel is the way out; a Back chevron beside it would be a second one.
        accessories.backActionOverride = null

        onDispose {
            override.editing = false
            override.edited = false
            accessories.leadingAccessory = previousLeading
            accessories.centerAccessory = previousCenter
            accessories.centerTrailingAccessory = previousTrailing
            accessories.rightAccessory = previousRight
            accessories.backActionOverride = previousBack
        }
    }
}

/** The hairline between the view controls and the ways out (`.header-actions .divider`). */
@Composable
private fun HeaderDivider() {
    Box(
        Modifier
            .padding(horizontal = EditorTokens.TopBar.dividerGap)
            .width(1.dp)
            .height(EditorTokens.TopBar.dividerHeight)
            .background(Theme.room.line)
    )
}
