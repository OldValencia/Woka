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
 * The caption's faces, replaced whole — the third twin of [filled] and [cornered]
 * (ME-151).
 *
 * **Whole rather than one face toggled here**, because the row above it already holds the
 * answer: `ToggleSetParam`'s value *is* the set the user has lit, so this applies it and
 * has no opinion about which chip moved. Toggling inside the model would mean the panel
 * and the shape each keeping their own idea of what is on.
 */
internal fun MarkupShape.faced(faces: Set<TextFace>): MarkupShape =
    if (this is MarkupShape.Text) copy(faces = faces) else this

/**
 * The four faces type can wear at once (ME-151, asked for 2026-08-26).
 *
 * **A set on the shape, not four booleans.** They are independent — a caption may be bold
 * *and* underlined — which is what rules out `MarkupKind`'s `ChoiceParam` and what
 * `ToggleSetParam` was built for (ME-147). One field then follows from that: the row's
 * value is already a `Set<TextFace>`, so four booleans would be converted to and from one
 * at every boundary — the shape, the pen, the restyle and the factory — and each
 * conversion is a place the two spellings can disagree. One field is also one default,
 * one wire key and one `copy`.
 *
 * `Underline` and `Strikethrough` are one `TextDecoration` by the time they reach a
 * `TextStyle` (`markupTextStyle`), but they are two independent chips here, because that
 * is what they are to the person pressing them.
 */
enum class TextFace { Bold, Italic, Underline, Strikethrough }

/**
 * What the pen draws next — the inspector's chooser, **not** a field of a shape.
 *
 * A shape's type is its class, so nothing needs to store this alongside it; the enum
 * exists because a `ChoiceParam` is how the shell renders "one of a fixed set" — at five
 * options, as a chip row rather than a `SegmentedControl` (`ChoiceParamRow`).
 */
enum class MarkupKind { Arrow, Rectangle, Ellipse, Freehand, Text }
