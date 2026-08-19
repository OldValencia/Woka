package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import com.woka.app.shell.LocalCanvasOverride
import com.woka.app.shell.LocalTopBarAccessories
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.CanvasZoomPill

/**
 * The editor takes over the **shell's** top bar rather than drawing a second one.
 *
 * It used to draw its own, which put two bars on screen at once and left the shell's
 * showing the folder path — a breadcrumb trail that did nothing when clicked, above an
 * editor that had already said which file it was editing.
 *
 * The centre is **not** claimed. The viewer's pill is already there and the editor only
 * repoints it (`CanvasOverrideState`): the size control drives this canvas, and the
 * carousel eye and `Edit` retract, because a slot filled wholesale is a different
 * subtree at the same position — Compose disposes it and there is nothing to animate
 * between. Opened straight from the grid there is no pill to borrow, so it brings one.
 *
 * **Everything is saved and restored**, for the reason `EditorKeyEffect` documents: the
 * viewer registers its accessories in a `DisposableEffect(Unit)` that will not re-run
 * when this one overwrites them, so clearing to null on dispose would leave the viewer
 * underneath with no controls and a Back button that exits the folder.
 *
 * `Cancel` is gone with the second bar. Back *is* cancel now — one control, one
 * meaning, which is what the viewer already does.
 */
@Composable
internal fun EditorTopBarSetup(
    isEdited: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    canRevert: Boolean,
    onLeave: () -> Unit,
    onRevert: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    val accessories = LocalTopBarAccessories.current
    val override = LocalCanvasOverride.current

    // Registered once, reading live values — re-registering per keystroke would
    // rebuild the accessory while the user is looking at it.
    val curCanUndo by rememberUpdatedState(canUndo)
    val curCanRevert by rememberUpdatedState(canRevert)
    val curRevert by rememberUpdatedState(onRevert)
    val curCanRedo by rememberUpdatedState(canRedo)
    val curLeave by rememberUpdatedState(onLeave)
    val curUndo by rememberUpdatedState(onUndo)
    val curRedo by rememberUpdatedState(onRedo)
    val curDone by rememberUpdatedState(onDone)

    // Where the edited mark went when the file name left the top bar: the pill has no
    // room for a word, and Information is the panel that already answers "what is this
    // file" (§4.4, 2026-08-18).
    //
    // **The document, not `isDirty`** (decided by hand 2026-08-19). Since ME-134 a
    // session opened on an edited photograph is *clean* — it matches the file — and
    // marking only unsaved work left an edited picture reading as an original one. The
    // discard dialog keeps the other meaning; these are two different questions.
    SideEffect { override.edited = isEdited }

    DisposableEffect(Unit) {
        val previousLeading = accessories.leadingAccessory
        val previousCenter = accessories.centerAccessory
        val previousRight = accessories.rightAccessory
        val previousBack = accessories.backActionOverride

        override.editing = true
        // Beside Back, not beside Undo/Redo: those two give work back and this one
        // throws it away, and three controls in a row read as three steps of one kind
        // (ME-107). Always drawn, disabled when there is nothing to remove — a control
        // that appears and vanishes is a control nobody learns the position of.
        accessories.leadingAccessory = {
            TopBarButton(
                label = "Revert",
                tag = "editor-revert",
                enabled = curCanRevert,
                color = Theme.colors.danger,
                onClick = { curRevert() }
            )
        }
        // Empty centre means no viewer underneath — opened from the grid's context menu.
        if (previousCenter == null) {
            accessories.centerAccessory = { CanvasZoomPill() }
        }
        accessories.rightAccessory = {
            EditorActions(
                canUndo = curCanUndo,
                canRedo = curCanRedo,
                onUndo = { curUndo() },
                onRedo = { curRedo() },
                onDone = { curDone() }
            )
        }
        accessories.backActionOverride = { curLeave() }

        onDispose {
            override.editing = false
            override.edited = false
            accessories.leadingAccessory = previousLeading
            accessories.centerAccessory = previousCenter
            accessories.rightAccessory = previousRight
            accessories.backActionOverride = previousBack
        }
    }
}

@Composable
private fun EditorActions(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.controlGap)
    ) {
        TopBarButton("↶", "editor-undo", enabled = canUndo, onClick = onUndo)
        TopBarButton("↷", "editor-redo", enabled = canRedo, onClick = onRedo)
        DoneButton(onDone)
    }
}
