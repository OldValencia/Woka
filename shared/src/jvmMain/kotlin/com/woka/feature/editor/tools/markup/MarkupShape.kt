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
 * of every document already saved.
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

    /**
     * A block of type, with [at] its top-left corner in source pixels (ME-122).
     *
     * **[width] is the font size**: type has no outline to thicken, and a sixth field
     * beside a `width` nothing reads is one every `when` here would have to ignore. It is
     * written by `MarkupTextSizeParam`, not by the stroke row, whose 1-64 px is nowhere
     * near enough for type on a 6000 px photograph. An empty string draws nothing and
     * hits nothing (`MarkupText`), so a shape nobody typed into leaves no mark.
     *
     * **Both fields below default to zero, and zero is the wire contract** (ME-146). A
     * document written by ME-122 or ME-145 carries neither key, decodes to both defaults,
     * and goes on meaning exactly what it meant — one unwrapped line, no plate. There is
     * no migration and no version field because there is nothing to migrate.
     */
    @Immutable
    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String,
        override val color: Long,
        override val width: Float,
        val at: MarkupPoint,
        val text: String,
        /**
         * The width the words wrap against, in source pixels — **0 for unconstrained**,
         * which lays the string out on one line however long it is.
         *
         * Never set by the user: it is inherited from the picture when the caret is
         * placed (`wrapAt`) and multiplied by a corner drag (ME-148). A caption therefore
         * carries a width it was given and a scale factor, never one it typed.
         */
        val wrap: Float = 0f,
        /**
         * The plate behind the glyphs, as packed ARGB — **0 for none**, which is the
         * common case and is why it is the default rather than a `null`.
         *
         * A second colour beside [color] rather than a `Boolean` plus a shared one: the
         * plate is picked against the photograph the same way the type is, and the two
         * are rarely the same colour.
         */
        val fill: Long = 0L,
        /**
         * How round the plate's corners are, as a **per cent of the type's size** — so a
         * caption scaled by a corner drag (ME-148) keeps its roundness without the ratio
         * touching a second field. 0 is a square corner, 100 a capsule.
         *
         * Its default is the constant the plate shipped with earlier the same day, which
         * is why a caption written between the two looks identical either way.
         */
        val corner: Float = 25f,
        /**
         * Bold, italic, underline, strikethrough — the ones that are on (ME-151). **Empty
         * is the wire contract**, as [wrap] and [fill] are zero; one set rather than four
         * fields, for the reason spelled beside `faced`.
         */
        val faces: Set<TextFace> = emptySet()
    ) : MarkupShape
}
