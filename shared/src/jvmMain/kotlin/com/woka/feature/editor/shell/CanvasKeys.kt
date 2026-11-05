package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key

/**
 * The one slot a canvas tool's `Delete` handler lives in, while that tool is open
 * (ME-114).
 *
 * **The editor's keyboard is a single mutable slot**, and this is the bridge across it.
 * `GlobalShortcutState.onPreviewKey` holds one handler; `EditorKeyEffect` installs the
 * editor's in `EditorHost` and restores the previous one on dispose, and `EditorKeys`
 * documents at length why a second claimant is how the viewer's arrow keys died. So the
 * overlay must not register its own — and it could not anyway: the active tool lives two
 * composables below the handler.
 *
 * A slot rather than a lookup, mirroring `ToolSelection`: "which tool is open" is
 * `EditorScreen`'s state, and the key handler must not learn it. Built in `EditorHost`
 * so its lifetime is the editor's, written by `ActiveToolOverlay` and cleared on dispose.
 */
@Stable
internal class CanvasKeys {

    /** True when the press was used — see `CanvasTool.onDelete`. */
    var onDelete: (() -> Boolean)? by mutableStateOf(null)

    /**
     * Whether the open tool is taking typed input, in which case the editor's handler
     * declines **every** key (ME-145).
     *
     * The same slot pattern as [onDelete] and for the same reason: the field that wants
     * the keyboard lives inside the active tool's overlay, and the handler that has to
     * stand aside is registered two composables above it.
     */
    var typing: Boolean by mutableStateOf(false)

    /**
     * Whether an inspector value field has focus (1.0.6 review). Its own flag, because the
     * markup overlay rewrites [typing] on every composition: in a field, ←/→ moved the play
     * head and Backspace reached the canvas instead of the caret and the text.
     */
    var fieldFocused: Boolean by mutableStateOf(false)

    /**
     * Whether [key] is the focused field's rather than the editor's: every key while typing,
     * only the caret's while a value field has focus — Space, `Ctrl+Z` and `Esc` stay the
     * editor's, since that focus can linger after a click on the picture.
     */
    fun yields(key: Key): Boolean = typing || (fieldFocused && key in CARET_KEYS)
}

private val CARET_KEYS = setOf(
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.Backspace,
    Key.Delete,
    Key.MoveHome,
    Key.MoveEnd
)

/** The editor's [CanvasKeys], for a field below `EditorHost` that has to claim the keyboard. */
internal val LocalCanvasKeys = staticCompositionLocalOf<CanvasKeys?> { null }
