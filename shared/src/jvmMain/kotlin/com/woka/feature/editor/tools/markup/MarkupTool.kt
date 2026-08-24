package com.woka.feature.editor.tools.markup

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
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
 * The seven offered colours: the editor's own accent first, then the five the rest of
 * the app already speaks (`Theme.Colors`), then white for a dark picture.
 *
 * `Long` ARGB rather than `Color`, because a swatch is a document value before it is a
 * pixel (`ParamValue.ColorValue`).
 */
private val SWATCHES = listOf(
    0xFFFF9F0AL,
    0xFFFF3B30L,
    0xFFFFD60AL,
    0xFF30D158L,
    0xFF0A84FFL,
    0xFF000000L,
    0xFFFFFFFFL
)

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
 * **The pen is not the shapes.** [Kind], [Color] and [Width] describe the *next* shape;
 * each shape stores its own style, copied at creation. They share a `ParamSet` with the
 * list, which is safe because `paramsOf` hands the whole set to `ParamWriter` — writing a
 * colour carries the shapes through untouched, the way Crop's aspect chip carries its rect.
 *
 * **Not a `CanvasTool` yet.** `ToolInteraction.Canvas` is a manifest claim the type does
 * not have to back — `FixtureTool` documents exactly that — so this ships a tool that
 * renders a document it cannot yet create. Drawing one is ME-114.
 */
object MarkupTool : EditorTool {

    /**
     * The shapes, in the order they were drawn and are painted.
     *
     * The one `ObjectParam` the spec was written for: the shell renders nothing for it,
     * which is the point — a value no generic row can draw says so in its type instead of
     * reaching a renderer that then has to guess.
     */
    val Shapes = ObjectParam("shapes", ListSerializer(MarkupShape.serializer()), emptyList())

    val Kind = ChoiceParam(
        key = "kind",
        label = "Shape",
        options = MarkupKind.entries.toList(),
        default = MarkupKind.Arrow
    )

    val Color = ColorParam(
        key = "color",
        label = "Color",
        swatches = SWATCHES,
        // The editor accent, which is what the design log settled markup's colour on
        // (2026-08-18) and what every other editor control already uses.
        default = 0xFFFF9F0AL
    )

    /**
     * Stroke width in **source** pixels, so a mark keeps its size relative to the picture
     * rather than to the window it was drawn in. 6 px reads on a photograph and is thin
     * enough to point at something on a screenshot.
     */
    val Width = FloatParam("width", "Width", 1f..64f, default = 6f, unit = "px")

    override val manifest = ToolManifest(
        id = "markup",
        title = "Markup",
        summary = "Arrows, boxes and freehand",
        icon = Icons.Static.PencilLine,
        domain = ToolDomain.Annotate,
        order = 0,
        cardinality = ToolCardinality.Single,
        // The canvas decides what a press means; the inspector's rows only set the pen.
        interaction = ToolInteraction.Canvas
    )

    /** The pen first, then the payload the inspector deliberately draws nothing for. */
    override val params: List<ParamSpec<*>> = listOf(Kind, Color, Width, Shapes)

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
        val shapes = params[Shapes]
        if (shapes.isEmpty()) return null

        return RasterStage.Paint { shapes.forEach { shape -> drawMarkup(shape) } }
    }
}
