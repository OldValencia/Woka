package com.woka.feature.editor.tools.markup

import androidx.compose.runtime.Composable
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage
import kotlinx.serialization.builtins.ListSerializer

/**
 * Markup — the first `Annotate` tool, and the first thing in the app that ever produced
 * a `RasterStage.Paint` (ME-113).
 *
 * That stage has existed since ME-007 and both draw paths have applied the paint list
 * *outside* the matrix since then, each with a comment saying a crop must clip an arrow
 * rather than move it (`RasterSurface`, `renderAtNativeScale`). Nothing had ever exercised
 * it. This tool is what makes that code true rather than intended.
 *
 * **`Single`, with one operation carrying the whole ordered list** — reversing what the
 * backlog, `ToolCardinality` and `EditorToolScopeImpl` all said before anything had to
 * *move* a shape (decision 2026-08-22, `MarkupShape`). Under `Repeating` a drawn arrow is
 * committed, and `EditorSession` has no way to edit a committed operation; worse,
 * previewing the moved copy while the original sat in `ops` would draw the arrow **twice**
 * for the length of every drag. Undo granularity is unchanged: one gesture, one entry.
 *
 * **The pen is not the shapes.** [Kind], [Color], [Width] and [TextSize] describe the *next*
 * shape;
 * each shape stores its own style, copied at creation. They share a `ParamSet` with the
 * list, which is safe because `paramsOf` hands the whole set to `ParamWriter` — writing a
 * colour carries the shapes through untouched, the way Crop's aspect chip carries its rect.
 *
 * **A `CanvasTool` since ME-114**, which added the overlay that draws, selects, moves,
 * resizes and deletes. `previewsThroughOverlay` stays false — the default, and the case
 * its doc comment was written for: a stroke has to be visible as it is drawn, so the
 * fold must go on applying the list rather than the overlay drawing a second copy.
 */
object MarkupTool : CanvasTool {

    /**
     * The shapes, in the order they were drawn and are painted.
     *
     * The one `ObjectParam` the spec was written for: the shell renders nothing for it,
     * which is the point — a value no generic row can draw says so in its type instead of
     * reaching a renderer that then has to guess.
     */
    val Shapes = ObjectParam("shapes", ListSerializer(MarkupShape.serializer()), emptyList())

    /** The pen — declared in `MarkupPen`, named here because a tool's specs are its own. */
    val Kind = MarkupKindParam
    val Color = MarkupColorParam
    val Width = MarkupWidthParam

    /** The plate behind a caption, `0L` for none (ME-146) — shown for the text pen alone. */
    val Fill = MarkupFillParam

    /** How round that plate is — shown only once there **is** a plate to round. */
    val Corner = MarkupCornerParam

    /**
     * Type's own size, because a stroke's 1-64 px is nowhere near it (ME-122). Shown for
     * every kind: a row that appeared and vanished with the chooser would need a
     * visibility rule the schema deliberately does not have (§3.4).
     */
    val TextSize = MarkupTextSizeParam

    override val manifest = ToolManifest(
        id = "markup",
        title = "Markup",
        summary = "Arrows, boxes, freehand and text",
        icon = Icons.Static.PencilLine,
        domain = ToolDomain.Annotate,
        order = 0,
        cardinality = ToolCardinality.Single,
        // The canvas decides what a press means; the inspector's rows only set the pen.
        interaction = ToolInteraction.Canvas
    )

    /**
     * Type's four faces, any number of them at once (ME-151) — the one row here that is a
     * `ToggleSetParam`, because a caption may be bold *and* underlined.
     */
    val Faces = MarkupFacesParam

    /** The pen first, then the payload the inspector deliberately draws nothing for. */
    override val params: List<ParamSpec<*>> =
        listOf(Kind, Color, Fill, Corner, Width, TextSize, Faces, Shapes)

    /** Which of those are worth drawing for the pen currently chosen — `MarkupRows`. */
    override fun rows(params: ParamSet): List<ParamSpec<*>> = markupRows(params)

    /**
     * **One stage for every shape**, drawn in list order.
     *
     * One rather than one each, because the fold collapses paints into a list it walks
     * per frame: a hundred arrows would otherwise be a hundred stages, sorted and mapped,
     * to produce the same hundred draw calls.
     *
     * Null for an empty list — the rule every tool follows, so opening the panel and
     * closing it again leaves the document exactly as it was found.
     */
    override fun stage(params: ParamSet): ToolStage? {
        // A text shape nobody typed into draws nothing, so a list holding only those is a
        // list of nothing — and a stage that paints no pixel is still a stage the fold
        // walks and sorts on every frame.
        val shapes = params[Shapes].filterNot { it is MarkupShape.Text && it.text.isBlank() }
        if (shapes.isEmpty()) return null

        return RasterStage.Paint { shapes.forEach { shape -> drawMarkup(shape) } }
    }

    /** One shape removed, one undo step — the arithmetic is `deleteSelectedShape`. */
    override fun onDelete(scope: EditorToolScope): Boolean = scope.deleteSelectedShape()

    /** Colour and width apply to what is selected, too — see `restyleSelected`. */
    override fun restyled(spec: ParamSpec<*>, params: ParamSet, selectedId: String?): ParamSet? =
        restyleSelected(spec, params, selectedId)

    @Composable
    override fun Overlay(scope: EditorToolScope) = MarkupOverlay(scope)
}
