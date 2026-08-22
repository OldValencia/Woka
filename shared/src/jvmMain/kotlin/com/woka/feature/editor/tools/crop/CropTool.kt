package com.woka.feature.editor.tools.crop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.ChoiceParam
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

/**
 * Crop — the second real tool, and the **first that addresses pixels** (ME-109).
 *
 * That is what made it more than a tool: rotate and flip are scale-free, so every
 * reader had been folding recipes against whatever bitmap it happened to hold. A rect
 * in source pixels does not survive that, and `RecipeStillEdits` now folds against the
 * file's native size and scales the finished recipe down (`RasterRecipe.scaledBy`).
 *
 * **`Single`.** A second crop replaces the first rather than compounding: two crops in
 * a row are one region chosen twice, and stacking them would make the stored rect mean
 * "inside the previous crop" — a coordinate space nothing else in the app speaks.
 *
 * **The one stage that names its own frame.** A crop chooses the output size and says
 * so; a rotation cannot, which is the whole of `RasterStage.Geometry.outputSize` being
 * nullable (`DerivedFrame`). Here it is `IntSize(width, height)` and the matrix is the
 * translation that brings the rect's corner to the origin.
 */
object CropTool : CanvasTool {

    val Rect = ObjectParam("rect", CropRect.serializer(), CropRect.Unset)

    val Aspect = ChoiceParam(
        key = "aspect",
        label = "Aspect",
        options = CropAspect.entries.toList(),
        default = CropAspect.Free,
        // `16:9`, not `SixteenNine`: the chips are read, not compiled.
        labelOf = CropAspect::label
    )

    /**
     * The ratio on its side — 16:9 becomes 9:16.
     *
     * **A switch, not a press**, and that is a correction (2026-08-22). It was
     * `ActionParam("flip", "Flip ratio")`, which does nothing anybody can see: an
     * action is built from `tool.defaults()` by design, so pressing it would have
     * discarded the rect it was meant to turn. It also said nothing about what it did.
     */
    val Portrait = BoolParam("portrait", "Portrait")

    override val manifest = ToolManifest(
        id = "crop",
        title = "Crop",
        summary = "Choose the region to keep",
        icon = Icons.Static.Crop,
        domain = ToolDomain.Geometry,
        // Where it sits in the rail, beside Transform. It is **not** what decides
        // application order: the fold ranks by domain and keeps the order the user
        // made things in, so turning after a crop turns the cropped picture and
        // cropping after a turn cuts the turned one (§3.4).
        order = 1,
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Canvas
    )

    override val params: List<ParamSpec<*>> = listOf(Rect, Aspect, Portrait)

    /**
     * **Null until a rect has actually been chosen**, so an untouched crop composes
     * nothing: opening the tool, looking at the picture and closing it leaves the
     * document exactly as it was found.
     *
     * The ticket said "null for a rect covering the whole frame", and `stage` cannot
     * answer that — it receives parameters and never the frame (§3.4), which is the
     * same limit that makes a rotation unable to report its own output size. So the
     * unchosen state is carried by the rect instead: [CropRect.Unset] has no area, and
     * the overlay writes a real one only once the user has moved something.
     */
    override fun stage(params: ParamSet): ToolStage? {
        val rect = params[Rect]
        if (rect.width <= 0 || rect.height <= 0) return null

        return RasterStage.Geometry(
            matrix = Matrix().apply { translate(-rect.left.toFloat(), -rect.top.toFloat()) },
            outputSize = IntSize(rect.width, rect.height)
        )
    }

    /** The rectangle *is* the preview; folding it in as well makes it measure itself. */
    override val previewsThroughOverlay = true

    @Composable
    override fun Overlay(scope: EditorToolScope) = CropOverlay(scope)
}
