package com.woka.feature.editor.tools.markup

import androidx.compose.runtime.Composable
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ObjectParam
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
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

    /** When each shape is on screen in a film (ME-263) — `MarkupTiming`. */
    val Windows = MarkupWindowsParam

    /** The pen first, then the timing, then the payloads the inspector draws nothing for. */
    override val params: List<ParamSpec<*>> =
        listOf(Kind, Color, Fill, Corner, Width, TextSize, Faces) + TIMING + listOf(Shapes, Windows)

    /** Which of those are worth drawing for the pen currently chosen — `MarkupRows`. */
    /** The two size specs re-defaulted for this picture (ME-159); everything else as declared. */
    override fun specs(sourceSize: IntSize): List<ParamSpec<*>> =
        params.map { it.sizedFor(sourceSize) }

    override fun rows(params: ParamSet, sourceSize: IntSize): List<ParamSpec<*>> =
        markupRows(params, sourceSize)

    /** Plus the timing rows, for a film with a shape selected (ME-263). */
    override fun rows(params: ParamSet, sourceSize: IntSize, selectedId: String?, timelineMs: Long?) =
        markupRows(params, sourceSize, selectedId, timelineMs)

    /** The pen on one line and its style on the next, both compact (ME-155). */
    override fun lines(rows: List<ParamSpec<*>>): List<List<ParamSpec<*>>> = markupLines(rows)

    /** And the plate's roundness inside the plate's own button — `markupFolded`. */
    override fun folded(spec: ParamSpec<*>, rows: List<ParamSpec<*>>): List<ParamSpec<*>> =
        markupFolded(spec, rows)

    /** Every shape in one stage, each at its own time on a film — `markupStage`. */
    override fun stage(params: ParamSet): ToolStage? = markupStage(params)

    /** Marks to touch with no tool open (ME-149) — none on a picture nobody has drawn on. */
    override fun selectableIn(document: EditDocument): Boolean =
        document.markupShapes().isNotEmpty()

    /** One shape removed, one undo step — the arithmetic is `deleteSelectedShape`. */
    override fun onDelete(scope: EditorToolScope): Boolean = scope.deleteSelectedShape()

    /** Colour and width apply to what is selected, too — see `restyleSelected`. */
    override fun restyled(spec: ParamSpec<*>, params: ParamSet, selectedId: String?): ParamSet? =
        restyleSelected(spec, params, selectedId)

    /** And a timing row to that shape's window (ME-263). */
    override fun restyled(spec: ParamSpec<*>, params: ParamSet, selectedId: String?, timelineMs: Long?) =
        restyleTiming(spec, params, selectedId, timelineMs) ?: restyleSelected(spec, params, selectedId)

    /** And the panel reports that shape rather than the pen — `displaySelected`. */
    override fun displayed(params: ParamSet, selectedId: String?): ParamSet =
        displayTiming(displaySelected(params, selectedId), selectedId)

    /** `Appear at play head` reads the shapes from the document, not from the action's blank set. */
    override fun atPlayHead(spec: ParamSpec<*>, params: ParamSet, playHeadMs: Long, document: EditDocument) =
        appearAtPlayHead(spec, playHeadMs, document)

    override fun spanOf(document: EditDocument, selectedId: String?) = windowOf(document, selectedId)

    @Composable
    override fun Overlay(scope: EditorToolScope) = MarkupOverlay(scope)
}
