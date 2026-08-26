package com.woka.feature.editor.tools.markup

import androidx.compose.runtime.Immutable

/**
 * One caption being typed: the shape as it was found, and the string as it stands now
 * (ME-145).
 *
 * Two values rather than one mutated shape, because both are needed at the end —
 * `commitTextEdit` compares them to decide whether anything happened at all, and a
 * caption reopened and closed unchanged must cost no undo step.
 *
 * Its own file since ME-146, which took `MarkupTextField` to the size mandate: the field
 * is a composable and this is what it is handed, and the two are read at different times.
 */
@Immutable
internal data class MarkupTextEdit(
    val shape: MarkupShape.Text,
    val text: String,
    /**
     * Whether the document already held this caption when the edit began — the one thing
     * that cannot be worked out at the end, because the caption is previewed **into** the
     * list the moment the caret is placed (`MarkupTextEdits`). Without it, "typed nothing
     * into a new caret" and "emptied a caption that was there" look identical.
     */
    val existed: Boolean
)
