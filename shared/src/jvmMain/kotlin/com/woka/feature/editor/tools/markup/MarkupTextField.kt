package com.woka.feature.editor.tools.markup

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the caret is, for the UI tests. */
internal const val MARKUP_TEXT_FIELD_TAG = "markup-text-field"

/**
 * The caret on the picture (ME-145).
 *
 * **It draws no text — only the caret.** The string is in the document from the moment
 * the caret is placed, so the *canvas* draws it, through the fold, in the same routine
 * that will rasterise it on save. This field is transparent and sits exactly over it,
 * contributing a caret, a selection and the keyboard. Drawing it here as well was the
 * first shape of this and it cost the style rows: a caption outside the document is a
 * caption `restyleSelected` cannot find (`MarkupTextEdits`).
 *
 * It is positioned and sized entirely from [sourceToCanvas], which is what keeps the caret
 * on the glyphs at any zoom: the shape's size is a count of **source** pixels, so the font
 * size here is that number through the canvas scale and back into `sp` at the display's
 * own density.
 *
 * `abs` on that scale is not decoration — with a flip in the preview the mapping is
 * mirrored, and a negative font size is a field with nothing in it (`CropOverlay` made
 * the same mistake first, 2026-08-22).
 */
@Composable
internal fun MarkupTextField(
    edit: MarkupTextEdit,
    sourceToCanvas: Matrix,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    val focus = remember { FocusRequester() }
    // **Blur only counts after focus arrived.** `onFocusChanged` reports *not focused* on
    // the frame the field is first composed, before the request below lands — so a handler
    // that took any unfocused report as "clicked away" committed the edit on the frame it
    // opened, and the caret vanished as fast as it appeared. Found by hand, 2026-08-26.
    var everFocused by remember(edit.shape.id) { mutableStateOf(false) }
    // And the commit runs once: `Enter` finishes the edit, which removes the field, which
    // reports a blur that must not commit a second time.
    var finished by remember(edit.shape.id) { mutableStateOf(false) }

    LaunchedEffect(edit.shape.id) { focus.requestFocus() }

    val origin = sourceToCanvas.map(Offset(edit.shape.at.x, edit.shape.at.y))
    val scale = abs(sourceToCanvas.map(Offset(1f, 0f)).x - sourceToCanvas.map(Offset.Zero).x)
    val density = LocalDensity.current

    BasicTextField(
        value = edit.text,
        onValueChange = onChange,
        // **Not single-line since ME-146.** The canvas under this wraps the string at the
        // caption's own `wrap`, and a single-line field lays the same string out on one
        // line — so the caret walked off to the right while the glyphs went down a line.
        // `Enter` is still consumed below, so this can never insert a newline.
        singleLine = false,
        // **The caption's own style, at the canvas's scale** (ME-151). Transparent,
        // because the canvas underneath is drawing this very string — but everything else
        // has to match it: a bold advance is wider than a regular one, so a field measured
        // in the default face puts the caret where the glyphs are not.
        textStyle = markupTextStyle(
            shape = edit.shape,
            fontSize = with(density) { (edit.shape.width * scale).toSp() },
            color = Color.Transparent
        ),
        // The caret takes the caption's own colour, so it is legible for the same reason
        // the text is: the user picked it against this photograph.
        cursorBrush = SolidColor(Color(edit.shape.color)),
        modifier = Modifier
            .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            // The caption's wrap width, through the same canvas scale as its font size, so
            // the field breaks its lines exactly where the fold breaks the glyphs beneath.
            // Zero means unconstrained — an ME-122 document — and constrains nothing.
            .then(
                if (edit.shape.wrap > 0f) {
                    Modifier.width(with(density) { (edit.shape.wrap * scale).toDp() })
                } else {
                    Modifier
                }
            )
            .focusRequester(focus)
            .onFocusChanged { state ->
                // Clicking away commits, which is the rule for every inline editor
                // people already use.
                if (state.isFocused) {
                    everFocused = true
                } else if (everFocused && !finished) {
                    finished = true
                    onCommit()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // **`Enter` is a line break, not a commit** (asked for 2026-08-27). It
                // shipped as the commit with ME-145, when a caption was one line and had
                // nowhere to break to; ME-146 gave it a box and a wrap, so the key that
                // ends a paragraph has to end a paragraph. The edit is finished by
                // clicking elsewhere — the blur below, and the press handler in
                // `MarkupPointer` for the canvas, which is not focusable.
                //
                // Only `Esc` is consumed here, and here rather than in `EditorKeys`, which
                // stands aside entirely while a field is open (`CanvasKeys.typing`).
                if (event.key == Key.Escape) finish(onCancel) { finished = true } else false
            }
            .testTag(MARKUP_TEXT_FIELD_TAG)
    )
}

/** Marks the edit over and runs [action], consuming the key either way. */
private inline fun finish(action: () -> Unit, mark: () -> Unit): Boolean {
    mark()
    action()
    return true
}
