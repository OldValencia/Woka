package com.woka.feature.editor.tools.adjust

import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage

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
 * **Auto reads the picture, and only four of these follow from it.** ME-166 gave
 * `resolve` a histogram — numbers, never pixels — and `AdjustAuto` turns it into Black
 * Point, Exposure, Shadows and Highlights. Contrast, the four colour sliders, Noise and
 * Sharpness are left where the user put them, for reasons that file states.
 */
object AdjustTool : AdjustParams(), EditorTool {

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
        Auto,
        Exposure, Brightness, Contrast, Highlights, Shadows, BlackPoint,
        Saturation, Vibrance, Warmth, Tint,
        Noise, Sharpness
    )

    /** What it performs is `AdjustStage`'s, which is where the order is argued. */
    override fun stage(params: ParamSet): ToolStage? = adjustStage(params)

    /** Auto, and nothing else — the maths and the reasoning are `AdjustAuto`'s. */
    override fun resolve(
        spec: ParamSpec<*>,
        params: ParamSet,
        frame: IntSize,
        stats: SourceStats?
    ): ParamSet? = if (spec == Auto && stats != null) autoAdjustments(stats, params) else null
}
