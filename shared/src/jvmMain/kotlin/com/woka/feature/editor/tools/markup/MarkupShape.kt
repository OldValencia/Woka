package com.woka.feature.editor.tools.markup

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One drawn annotation, in **source** pixels (ME-113).
 *
 * Source, never canvas: the same arrow has to land on the same part of the picture when
 * the editor draws it over a 2560 px proxy, when a grid cover folds it into 400 px and
 * when the save runs it against the native bitmap. That is the same rule
 * `CropRect` follows, and for the same reason — `RasterRecipe` is folded in source
 * coordinates and scaled afterwards.
 *
 * **A list of these is one operation, not one operation each** (§ decision 2026-08-22).
 * The backlog planned `ToolCardinality.Repeating`, one shape per operation, and moving a
 * shape is what disproved it: a drawn arrow would be *committed*, and `EditorSession`
 * offers `preview`, `commit`, `undo`, `redo` and `reset` — nothing that edits history.
 * With the list inside one `Single` operation, adding, moving, resizing and deleting are
 * all "preview a different list, commit on release", which is the mechanism Crop already
 * uses. Three arrows are still three arrows: three entries, and one `RasterStage.Paint`.
 *
 * [id] is the shape's own, minted where the shape is created (ME-114) and never reused —
 * selection is keyed on it, and it must survive a move, which replaces the instance.
 * [color] is packed ARGB and [width] is the stroke's width in source pixels; both are
 * copied from the pen at creation, so restyling the pen never rewrites what is drawn.
 *
 * The `@SerialName`s are the **wire format**: the discriminator kotlinx would derive is
 * the fully qualified class name, which would make moving this file rewrite the meaning
 * of every document already saved. Text is ME-122 and is deliberately not here yet.
 */
@Immutable
@Serializable
sealed interface MarkupShape {

    val id: String
    val color: Long
    val width: Float

    @Immutable
    @Serializable
    @SerialName("arrow")
    data class Arrow(
        override val id: String,
        override val color: Long,
        override val width: Float,
        val from: MarkupPoint,
        val to: MarkupPoint
    ) : MarkupShape

    @Immutable
    @Serializable
    @SerialName("rectangle")
    data class Rectangle(
        override val id: String,
        override val color: Long,
        override val width: Float,
        val from: MarkupPoint,
        val to: MarkupPoint
    ) : MarkupShape

    @Immutable
    @Serializable
    @SerialName("ellipse")
    data class Ellipse(
        override val id: String,
        override val color: Long,
        override val width: Float,
        val from: MarkupPoint,
        val to: MarkupPoint
    ) : MarkupShape

    /**
     * A freehand stroke, as the points it was dragged through.
     *
     * The list is the shape — there is no box to resize and therefore no handle
     * (`MarkupGeometry.handles`). Thinning the points as they arrive is ME-114's, where
     * the gesture is: a 6000 px photograph at 60 fps otherwise stores a thousand points
     * for one stroke, and every one of them reaches the file.
     */
    @Immutable
    @Serializable
    @SerialName("freehand")
    data class Freehand(
        override val id: String,
        override val color: Long,
        override val width: Float,
        val points: List<MarkupPoint>
    ) : MarkupShape
}

/**
 * The same shape in a different pen (ME-114).
 *
 * A `when` over the four rather than a field on the interface: `color` and `width` are
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
    }

/**
 * A point of a shape, in source pixels.
 *
 * Its own type rather than `Offset` for two reasons that both have to hold: `Offset` is
 * not serialisable, and it is a Compose value class over packed floats whose layout is
 * Compose's to change — which `code-style/model.md` keeps out of anything written to a
 * document. Float, not Int: a shape is dragged, and rounding every drag to whole pixels
 * makes a slow diagonal move in steps.
 */
@Immutable
@Serializable
data class MarkupPoint(val x: Float, val y: Float)

/**
 * What the pen draws next — the inspector's chooser, **not** a field of a shape.
 *
 * A shape's type is its class, so nothing needs to store this alongside it; the enum
 * exists because a `ChoiceParam` is how the shell renders "one of a fixed set", and it
 * names the four constants in the order that row draws them.
 */
enum class MarkupKind { Arrow, Rectangle, Ellipse, Freehand }
