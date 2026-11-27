package com.woka.feature.editor.tools.markup

/**
 * A shape's name where it stands for itself: its bar in the Markup lane (RD-301) and the scope
 * line's crumb when it is selected (RD-302) — a text by its words, the rest by kind.
 */
val MarkupShape.label: String
    get() = when (this) {
        is MarkupShape.Text -> text.ifBlank { "Text" }
        is MarkupShape.Arrow -> "Arrow"
        is MarkupShape.Rectangle -> "Rectangle"
        is MarkupShape.Ellipse -> "Ellipse"
        is MarkupShape.Freehand -> "Drawing"
    }

/** The Undo toast's sentence once this shape is gone (RD-401): "Text removed.", "Arrow removed." */
val MarkupShape.removedNotice: String
    get() = (if (this is MarkupShape.Text) "Text" else label) + " removed."
