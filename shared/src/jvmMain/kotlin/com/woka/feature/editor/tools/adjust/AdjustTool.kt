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

/** Every adjustment runs the same span, so a slider means the same thing in each row. */
private val SPAN = -100f..100f

/**
 * The three groups the panel shows one at a time (ME-164), Photos' own split.
 *
 * Reported by hand at eight: what was wrong was not the length of the strip but that
 * Exposure sat beside Warmth for no reason a reader could see. **Detail** arrived with
 * ME-162. `ParamRowStrip` derives its segmented control from the sections the specs
 * declare, so a third group is a third constant here and no UI change anywhere.
 */
private const val LIGHT = "Light"
private const val COLOUR = "Colour"
private const val DETAIL = "Detail"

/**
 * Adjustments — twelve sliders, one colour matrix and three shaders (ME-111, ME-142,
 * ME-162).
 *
 * **The first tool to produce a `Pixels` stage.** The fold has carried `multiply` and
 * the "collapse every colour stage into one `ColorFilter`" rule since ME-007 with
 * nothing to exercise it; this is what makes that path real. A brightness slider at 60
 * fps therefore costs one filter rebuild and no pixel work at all (§3.2).
 *
 * **There is no Compose here.** Twelve declarations and pure functions: the sliders,
 * their detents, their readouts, their undo granularity — and, since ME-161, the fact
 * that they draw as a chooser rather than a stack — all come from the schema and the
 * manifest (§3.4). A line of UI code here would mean the architecture was wrong.
 *
 * **Twelve, and the four newest are not matrices.** Highlights and Shadows are
 * per-luminance, Sharpness and Noise per-neighbourhood, and a `ColorMatrix` is
 * affine — so all four are `RasterStage.Shader`, the primitive ME-165 added. Definition,
 * Vignette, Brilliance and the real Vibrance are each a shader and a `FloatParam` now,
 * and wait in the backlog on the panel rather than on the engine.
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
    val Highlights =
        FloatParam(
            "highlights", "Highlights", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Highlights
        )
    val Shadows =
        FloatParam(
            "shadows", "Shadows", SPAN,
            default = 0f, section = LIGHT, icon = Icons.Static.Shadows
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
    val Noise =
        FloatParam(
            "noise", "Noise", SPAN,
            default = 0f, section = DETAIL, icon = Icons.Static.Noise
        )
    val Sharpness =
        FloatParam(
            "sharpness", "Sharpness", SPAN,
            default = 0f, section = DETAIL, icon = Icons.Static.Sharpness
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
    override val params: List<ParamSpec<*>> = listOf(
        Exposure, Brightness, Contrast, Highlights, Shadows, BlackPoint,
        Saturation, Vibrance, Warmth, Tint,
        Noise, Sharpness
    )

    /** What it performs is `AdjustStage`'s, which is where the order is argued. */
    override fun stage(params: ParamSet): ToolStage? = adjustStage(params)
}
