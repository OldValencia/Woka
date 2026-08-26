package com.woka.feature.editor.tools.markup

/**
 * What the pen does to a shape, and what the pen's chooser offers (ME-146).
 *
 * Split out of `MarkupShape.kt` at 150 lines, at the seam that was already there: the
 * shape and its wire format on that side, the two restyling functions and the chooser on
 * this one. Nothing moved changed.
 */

/**
 * The same shape in a different pen (ME-114).
 *
 * A `when` over the five rather than a field on the interface: `color` and `width` are
 * `val`s on an immutable hierarchy, so restyling is `copy`, and `copy` is per class. The
 * alternative — a mutable style object shared by reference — is how two arrows drawn a
 * minute apart end up changing colour together.
 */
internal fun MarkupShape.styled(color: Long = this.color, width: Float = this.width): MarkupShape =
    when (this) {
        is MarkupShape.Arrow -> copy(color = color, width = width)
        is MarkupShape.Rectangle -> copy(color = color, width = width)
        is MarkupShape.Ellipse -> copy(color = color, width = width)
        is MarkupShape.Freehand -> copy(color = color, width = width)
        is MarkupShape.Text -> copy(color = color, width = width)
    }

/**
 * The caption's plate, recoloured — and every other shape returned exactly as it was
 * (ME-146).
 *
 * Its own function rather than a parameter of [styled], because the two are different
 * questions: `color` is the pen's, shared by all five shapes, and `fill` belongs to the
 * one shape that has a background to fill. Folding it in would give an arrow a field it
 * cannot draw and make `restyleSelected` decide which half of one call applies.
 */
internal fun MarkupShape.filled(fill: Long): MarkupShape =
    if (this is MarkupShape.Text) copy(fill = fill) else this

/** The plate's roundness, on the one shape that has a plate — the twin of [filled]. */
internal fun MarkupShape.cornered(corner: Float): MarkupShape =
    if (this is MarkupShape.Text) copy(corner = corner) else this

/**
 * What the pen draws next — the inspector's chooser, **not** a field of a shape.
 *
 * A shape's type is its class, so nothing needs to store this alongside it; the enum
 * exists because a `ChoiceParam` is how the shell renders "one of a fixed set" — at five
 * options, as a chip row rather than a `SegmentedControl` (`ChoiceParamRow`).
 */
enum class MarkupKind { Arrow, Rectangle, Ellipse, Freehand, Text }
