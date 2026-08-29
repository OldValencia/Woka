package com.woka.feature.editor.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSpec

/**
 * Right-click a control, and the value it holds goes back to where the tool declared it
 * (asked for 2026-08-29).
 *
 * **One gesture for every control**, which is the only thing that makes it worth
 * learning: a colour, a level, a set of faces and a plate all answer it, so nothing has
 * to be remembered per row. It replaces nothing — `Reset` still sits in the value
 * popover for anyone who never right-clicks — and it costs no pixels, which is what a
 * panel drawn over a photograph is short of.
 */

/**
 * The press, taken on the way **down** rather than on release.
 *
 * `PointerEventType.Press` with the secondary button is the whole gesture; a
 * `detectTapGestures(onLongPress = …)`-style wait would put a delay on something that is
 * unambiguous the moment the button goes down. Consumed, so a right-click on a control
 * inside a popover does not also reach whatever is behind it.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.resetOnRightClick(onReset: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            val change = event.changes.first()
            if (!change.isConsumed) {
                change.consume()
                onReset()
            }
        }
    }

/**
 * Writes [spec]'s own default, whatever its type.
 *
 * The star projection is captured **here and nowhere else**: `writer.edit(spec, …)` on a
 * `ParamSpec<*>` does not typecheck, and every control that offers a reset would
 * otherwise spell out the same generic helper. The same trick `EditorTool.defaults()`
 * uses from the other side.
 *
 * **A `FloatParam` returns to its detent**, not its default. The two are the same for
 * every tool today, but the spec's own doc says a tool may open at one value and reset to
 * another — and the detent is the mark already drawn on the slider, so a reset that
 * landed anywhere else would visibly disagree with the track.
 */
internal fun ParamWriter.resetTo(spec: ParamSpec<*>) {
    when (spec) {
        is FloatParam -> edit(spec, spec.detent)
        else -> writeDefault(this, spec)
    }
}

private fun <T> writeDefault(writer: ParamWriter, spec: ParamSpec<T>) =
    writer.edit(spec, spec.default)
