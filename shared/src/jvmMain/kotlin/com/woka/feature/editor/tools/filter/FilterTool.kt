package com.woka.feature.editor.tools.filter

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/**
 * Filter presets — eight looks, chosen from a strip that shows each of them (ME-112).
 *
 * **The tool that finally uses `PreviewChoiceParam`.** That spec has existed since
 * ME-003 and its renderer was the one row the shell answered with `Unit`, deferred in
 * ME-102 with the reason in the code: built before filters existed it would have drawn a
 * strip of identical thumbnails and proved nothing.
 *
 * **A filter is one matrix**, like an adjustment — so the two collapse into a single
 * `ColorFilter` when both are set, which is what `multiply` in the fold is for. Choosing
 * a look costs a filter rebuild and no pixel work at all (§3.2).
 *
 * `order = 1` puts it after Adjustments in the rail; application order is the fold's
 * domain rank, and both are `Color`, so a look and a correction compose in the order the
 * user made them.
 */
object FilterTool : EditorTool {

    val Filter = PreviewChoiceParam(
        key = "filter",
        options = PhotoFilter.entries.toList(),
        default = PhotoFilter.Original,
        labelOf = PhotoFilter::label
    )

    val Intensity = FloatParam(
        key = "intensity",
        label = "Intensity",
        range = 0f..100f,
        default = 100f,
        detent = 100f,
        unit = "%"
    )

    override val manifest = ToolManifest(
        id = "filter",
        title = "Filters",
        summary = "Eight looks, at any strength",
        // Its own glyph: a rail of identical squares is a mistake made twice already.
        icon = Icons.Static.MagicWand,
        domain = ToolDomain.Color,
        order = 1,
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Continuous
    )

    override val params: List<ParamSpec<*>> = listOf(Filter, Intensity)

    /**
     * Nothing at `Original`, and nothing at zero intensity — the same rule every other
     * tool follows, so a panel opened and closed leaves the document untouched.
     */
    override fun stage(params: ParamSet): ToolStage? =
        filterMatrix(params[Filter], params[Intensity])?.let { RasterStage.Pixels(it) }
}
