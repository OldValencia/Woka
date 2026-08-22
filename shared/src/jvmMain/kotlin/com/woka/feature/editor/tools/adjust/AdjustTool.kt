package com.woka.feature.editor.tools.adjust

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/** Every adjustment runs the same span, so a slider means the same thing in each row. */
private val SPAN = -100f..100f

/**
 * Adjustments — six sliders, one colour matrix (ME-111).
 *
 * **The first tool to produce a `Pixels` stage.** The fold has carried `multiply` and
 * the "collapse every colour stage into one `ColorFilter`" rule since ME-007 with
 * nothing to exercise it; this is what makes that path real. A brightness slider at 60
 * fps therefore costs one filter rebuild and no pixel work at all (§3.2).
 *
 * **There is no Compose here.** Six declarations and a pure function: the sliders, their
 * detents, their signed readouts and their undo granularity all come from the schema and
 * the manifest (§3.4). If this file had needed a line of UI code, the architecture would
 * have been wrong rather than the tool.
 *
 * **Six, not eight.** Highlights and Shadows want a per-luminance curve, which is a
 * fourth fold primitive against §3.2's three — an engine decision, not a tool's (§9).
 *
 * **Auto is deliberately absent.** An automatic correction reads the *picture* — a
 * histogram, a black point — and a tool receives parameters and nothing else;
 * `EditorTool.resolve` hands over the frame, never the pixels. It waits in the backlog
 * for a hook that something actually asks for.
 */
object AdjustTool : EditorTool {

    val Exposure = FloatParam("exposure", "Exposure", SPAN, default = 0f)
    val Brightness = FloatParam("brightness", "Brightness", SPAN, default = 0f)
    val Contrast = FloatParam("contrast", "Contrast", SPAN, default = 0f)
    val Saturation = FloatParam("saturation", "Saturation", SPAN, default = 0f)
    val Warmth = FloatParam("warmth", "Warmth", SPAN, default = 0f)
    val Tint = FloatParam("tint", "Tint", SPAN, default = 0f)

    override val manifest = ToolManifest(
        id = "adjust",
        title = "Adjust",
        summary = "Exposure, colour and contrast",
        // Its own glyph. Two tools sharing one is a mistake this rail has made twice.
        icon = Icons.Static.Adjust,
        domain = ToolDomain.Color,
        order = 0,
        cardinality = ToolCardinality.Single,
        // Preview while the knob moves, one history entry on release — declared here so
        // a slider physically cannot mint two hundred undo steps (`ParamWriter`).
        interaction = ToolInteraction.Continuous
    )

    override val params: List<ParamSpec<*>> =
        listOf(Exposure, Brightness, Contrast, Saturation, Warmth, Tint)

    /**
     * One stage for all six, and **none at all when they are neutral** — the same rule a
     * rotation of 0° follows. An identity filter is still a filter the GPU runs over
     * every pixel of every frame, which is what `adjustmentMatrix` returning null avoids.
     */
    override fun stage(params: ParamSet): ToolStage? =
        adjustmentMatrix(params)?.let { RasterStage.Pixels(it) }
}
