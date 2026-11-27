package com.woka.feature.editor.tools.resize

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/**
 * Resize — the last thing that happens to a picture, and the first tool that is **pure
 * data** (ME-110). No overlay, no canvas gesture: a manifest, three parameters and a
 * scaling matrix.
 *
 * **A percentage, or since RD-401 a pair of pixel dimensions.** The percentage needs no
 * frame and survives a crop; `Custom` in `Save your changes?` names exact pixels, either axis
 * free, which only the fold can turn into a matrix — `RasterStage.Framed` asks it for the
 * frame ([explicitResize]). [Width] and [Height] at zero mean "no pixels named".
 *
 * Held in **percent** rather than a 0..1 factor so the inspector's existing readout
 * prints `50%` without the shell learning a special case; `stage()` divides by a hundred
 * and nothing else in the app sees the unit.
 *
 * **`ToolDomain.Output`**, which is what puts it after every geometry and colour stage
 * however the user ordered their work — a resize is the last stage before encoding, and
 * the fold's domain rank is what makes that true rather than a convention.
 */
object ResizeTool : EditorTool {

    val Scale = FloatParam(
        key = "scale",
        label = "Scale",
        range = MIN_RESIZE_PERCENT..FULL_PERCENT,
        default = FULL_PERCENT,
        detent = FULL_PERCENT,
        unit = "%"
    )

    val Preset = ChoiceParam(
        key = "preset",
        label = "Preset",
        options = ResizePreset.entries.toList(),
        default = ResizePreset.Full,
        labelOf = ResizePreset::label
    )

    val LongEdge = ChoiceParam(
        key = "longEdge",
        label = "Long edge",
        options = ResizeLongEdge.entries.toList(),
        default = ResizeLongEdge.Uhd,
        labelOf = ResizeLongEdge::label
    )

    override val manifest = ToolManifest(
        id = "resize",
        title = "Resize",
        summary = "Make the picture smaller",
        // Not `ScaleFit`: that is the glyph Crop already carries, and two identical
        // squares in the rail is the mistake this project made once already.
        icon = Icons.Static.Collapse,
        domain = ToolDomain.Output,
        order = 0,
        tabbed = false,
        cardinality = ToolCardinality.Single,
        // The slider previews while it moves and commits on release, so one drag is one
        // undo step — decided by the manifest, never by the row (`ParamWriter`).
        interaction = ToolInteraction.Continuous
    )

    /** `Custom`'s pixels (RD-401); zero while a percentage rules. */
    val Width = IntParam(key = "width", label = "Width", range = 0..MAX_PHOTO_SIDE, default = 0)
    val Height = IntParam(key = "height", label = "Height", range = 0..MAX_PHOTO_SIDE, default = 0)

    /** `Keep proportions`, remembered so the dialog reopens as it was left. Draws nothing. */
    val Proportional = BoolParam(key = "keepProportions", label = "Keep proportions", default = true)

    override val params: List<ParamSpec<*>> = listOf(Scale, Preset, LongEdge, Width, Height, Proportional)

    /**
     * A picture at full size is not resized, so it composes nothing at all — the same
     * rule a rotation of 0° follows.
     *
     * No output size: a scale cannot know the frame it produces, so the fold derives it
     * (`RasterStage.Geometry.outputSize`, `DerivedFrame`). That is also what makes a
     * resize *after* a crop apply to the cropped frame rather than the original.
     */
    override fun stage(params: ParamSet): ToolStage? {
        explicitResize(params[Width], params[Height])?.let { return it }
        val percent = params[Scale]
        if (percent >= FULL_PERCENT) return null
        val factor = percent / 100f
        return RasterStage.Geometry(Matrix().apply { scale(factor, factor) })
    }

    /** `4000 × 3000 → 2000 × 1500`, and nothing at all while nothing is being resized. */
    override fun footnote(params: ParamSet, frame: IntSize): String? =
        resizeFootnote(params[Scale], frame)

    /**
     * A chip that rewrites the slider.
     *
     * Both choices mean "set the scale to this", and one of them can only be worked out
     * against the picture's own size. The frame arrives here — at the moment of the
     * click, from the shell, which knows it — rather than reaching [stage], so the fold
     * stays a pure function of parameters (§3.4).
     */
    override fun resolve(
        spec: ParamSpec<*>,
        params: ParamSet,
        frame: IntSize,
        stats: SourceStats?
    ): ParamSet? = when (spec) {
        Preset -> params.with(Scale, params[Preset].percent)
        LongEdge -> params.with(Scale, scaleForLongEdge(params[LongEdge].pixels, frame))
        else -> null
    }
}
