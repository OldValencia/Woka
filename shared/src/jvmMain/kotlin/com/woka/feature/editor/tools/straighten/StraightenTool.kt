package com.woka.feature.editor.tools.straighten

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
import com.woka.feature.editor.engine.image.straightened

/**
 * Straighten — level a tilted picture by any angle in ±45° (RD-205, `#editor/crop`).
 *
 * **Out of the image editor's plan and in this one as an engine feature**: an arbitrary angle
 * is one more matrix, so no reader learns it exists — the editor's canvas, the viewer, the
 * grid, a save, a GIF and a film's render all draw `RasterRecipe.matrix`. What it needed was
 * the frame, which `stage()` is never told; `RasterStage.Framed` asks the fold for it.
 *
 * **Its own tool, not a parameter of Crop.** The angle belongs to the original picture and
 * folds before every turn and flip ([ToolManifest.foldsFirst]); a crop is cut from what the
 * user sees, after them. One operation cannot sit in both places. A flip after levelling
 * therefore mirrors a level picture and leaves the dial's value as written — the author's
 * choice over Photos' sign change, 2026-11-26: no control rewrites another's value.
 *
 * **±45°**, as the prototype's dial; the backlog's ±15° misread it. Past 45° a quarter turn
 * plus a small angle is the same picture.
 */
object StraightenTool : EditorTool {

    val Angle = FloatParam(
        key = "degrees",
        label = "Straighten",
        range = -45f..45f,
        default = 0f,
        unit = "°",
        // An arrow press, as the prototype's dial (`instruments.js`); Shift is ten of them.
        step = 0.5f,
        dial = true
    )

    override val manifest = ToolManifest(
        id = "straighten",
        title = "Straighten",
        summary = "Level a tilted horizon",
        icon = Icons.Static.Straighten,
        domain = ToolDomain.Geometry,
        // After Crop in Transform's tab: Shape, Orientation, the dial, then rotate and flip.
        order = 2,
        joins = "transform",
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Continuous,
        foldsFirst = true
    )

    override val params: List<ParamSpec<*>> = listOf(Angle)

    /** Nothing at 0°: a level picture is the no-op the fold drops, not an identity turn. */
    override fun stage(params: ParamSet): ToolStage? {
        val degrees = params[Angle]
        if (degrees == 0f) return null
        return RasterStage.Framed { frame -> straightened(frame, degrees) }
    }
}
