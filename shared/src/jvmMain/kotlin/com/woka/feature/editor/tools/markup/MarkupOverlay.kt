package com.woka.feature.editor.tools.markup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.feature.editor.core.EditorToolScope

/** Where the overlay's gestures land, for the UI tests. */
internal const val MARKUP_OVERLAY_TAG = "markup-overlay"

/**
 * Drawing, selecting, moving, resizing and typing on the canvas (ME-114, ME-145).
 *
 * **One undo step per gesture** (§3.1): every pointer event previews and the release
 * commits. Adding, moving and resizing are all "preview a different shape list" — the
 * mechanism Crop already uses, and the whole reason ME-113 made this tool `Single` with
 * one operation carrying the list. What each press *means* is `MarkupPointer`; this
 * composable owns the three things a recogniser cannot: the string being typed, the field
 * it is typed into, and the keyboard the editor has to lend it.
 *
 * **Type is the one shape a press places rather than a drag draws** (ME-145). A click on
 * empty picture with the text pen out opens a caret; a second click on a caption reopens
 * it. The string lives here until it is committed and the shape is previewed **empty**
 * meanwhile (`hideWhileEditing`), so the field is the only copy on screen rather than the
 * second one.
 */
@Composable
internal fun MarkupOverlay(scope: EditorToolScope) {
    val current by rememberUpdatedState(scope)
    val frame = scope.sourceSize
    var edit by remember(frame) { mutableStateOf<MarkupTextEdit?>(null) }
    val selected = scope.document.markupShapes().firstOrNull { it.id == scope.selectedId }

    // The editor's key handler stands aside for exactly as long as a caret is on the
    // picture, or `Backspace` deletes the caption being typed into (`EditorKeys`).
    SideEffect { scope.captureKeys(edit != null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MarkupCanvas(
            selected = selected,
            sourceToCanvas = scope.sourceToCanvas,
            modifier = Modifier
                .fillMaxSize()
                .testTag(MARKUP_OVERLAY_TAG)
                .markupGestures(
                    frame = frame,
                    scope = { current },
                    editing = { edit },
                    onEdit = { open -> edit = open }
                )
        )

        edit?.let { open ->
            MarkupTextField(
                edit = scope.liveEdit(open),
                sourceToCanvas = scope.sourceToCanvas,
                onChange = { typedText ->
                    edit = open.copy(text = typedText)
                    // A preview per keystroke, which is not an undo step (§3.1) and is what
                    // puts the string on the canvas as it is typed.
                    current.previewTextEdit(open.shape, typedText)
                },
                onCommit = {
                    edit = null
                    current.commitTextEdit(open)
                },
                onCancel = {
                    edit = null
                    current.abandonTextEdit()
                }
            )
        }
    }
}
