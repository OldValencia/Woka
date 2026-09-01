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
 * The two groups the panel shows one at a time (ME-164), Photos' own split.
 *
 * Reported by hand at eight: what was wrong was not the length of the strip but that
 * Exposure sat beside Warmth for no reason a reader could see. **Detail** — Sharpness,
 * Definition, Noise Reduction, Vignette — is the third and arrives with ME-162.
 */
private const val LIGHT = "Light"
private const val COLOUR = "Colour"

/**
 * Adjustments — eight sliders, one colour matrix (ME-111, ME-142).
 *
 * **The first tool to produce a `Pixels` stage.** The fold has carried `multiply` and
 * the "collapse every colour stage into one `ColorFilter`" rule since ME-007 with
 * nothing to exercise it; this is what makes that path real. A brightness slider at 60
 * fps therefore costs one filter rebuild and no pixel work at all (§3.2).
 *
 * **There is no Compose here.** Eight declarations and a pure function: the sliders, their
 * detents, their signed readouts, their undo granularity — and, since ME-161, the fact
 * that eight of them draw as a chooser rather than a stack — all come from the schema and
 * the manifest (§3.4). If this file had needed a line of UI code, the architecture would
 * have been wrong rather than the tool.
 *
 * **Eight, and the count stops for an engine reason.** What is left — Highlights,
 * Shadows, Sharpness, Definition, Noise Reduction, Vignette, Brilliance, and vibrance as
 * Photos actually means it — is per-luminance or per-neighbourhood, and a `ColorMatrix`
 * is an affine map. That wants a fourth fold primitive against §3.2's three, which is an
 * engine decision rather than a tool's: ME-162, and `AdjustMatrixTone` carries the
 * argument.
 *
 * **Auto is deliberately absent.** An automatic correction reads the *picture* — a
 * histogram, a black point — and a tool receives parameters and nothing else;
 * `EditorTool.resolve` hands over the frame, never the pixels. It waits in the backlog
 * for a hook that something actually asks for.
 */
object AdjustTool : EditorTool {

    val Exposure =
        FloatParam(
            "exposure", "Exposure", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Exposure
        )
    val Brightness =
        FloatParam(
            "brightness", "Brightness", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Brightness
        )
    val Contrast =
        FloatParam(
            "contrast", "Contrast", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Contrast
        )
    val BlackPoint =
        FloatParam(
            "blackPoint", "Black Point", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.BlackPoint
        )
    val Saturation =
        FloatParam(
            "saturation", "Saturation", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Saturation
        )
    val Vibrance =
        FloatParam(
            "vibrance", "Vibrance", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Vibrance
        )
    val Warmth =
        FloatParam(
            "warmth", "Warmth", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Warmth
        )
    val Tint =
        FloatParam(
            "tint", "Tint", SPAN,
            default = 0f, section = COLOUR, icon = Icons.Static.Tint
        )

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

    // Declared in the order the panel reads in (§3.4), which is also the order the
    // chooser's chips appear in: each new one beside the adjustment it qualifies.
    override val params: List<ParamSpec<*>> =
        listOf(Exposure, Brightness, Contrast, BlackPoint, Saturation, Vibrance, Warmth, Tint)

    /**
     * One stage for all eight, and **none at all when they are neutral** — the same rule a
     * rotation of 0° follows. An identity filter is still a filter the GPU runs over
     * every pixel of every frame, which is what `adjustmentMatrix` returning null avoids.
     */
    override fun stage(params: ParamSet): ToolStage? =
        adjustmentMatrix(params)?.let { RasterStage.Pixels(it) }
}
