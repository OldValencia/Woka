package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames

/**
 * Everything `EditorScreen` needs in order to draw a file that holds more than one
 * picture, as one value (ME-156).
 *
 * A data class rather than four parameters, for the reason `ViewerContext` and
 * `ViewerEvents` are: the screen already takes nine, the frame band adds four more
 * that always travel together, and the type is what keeps the diff readable when a
 * fifth is needed. `EditorHost` owns every field.
 *
 * **Null at the call site means "one picture"**, which is the overwhelmingly common
 * case and must cost nothing — no strip, no measurement, no extra decode.
 */
@Immutable
internal data class EditorFrameControls(
    val frames: EditorFrames,
    /** The scope `Done` will write into, held for the session by `EditorHost`. */
    val scope: EditScope,
    /** Which picture inside the file is being edited. */
    val frameIndex: Int,
    val onScopeChange: (EditScope) -> Unit,
    /** Walking to another picture without leaving the editor (ME-158). */
    val onSelectFrame: (Int) -> Unit
)
