package com.woka.feature.editor.tools.filter

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamPoint
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
 * Styles — an undertone, a look, and a pad for tone and colour (ME-112, ME-141).
 *
 * **The id stays `filter`.** The title and the summary changed and the panel was
 * rebuilt, but the manifest id is the settings key, the owner of every operation and
 * what is already written into every file that has been edited. Renaming it would
 * orphan every saved document; the new parameters get new keys and old documents fill
 * them from the defaults, which `ParamSet` already does. A rename, not a migration.
 *
 * **Four contributions, still one `ColorFilter`.** Every one of them is a
 * `ColorMatrix` and colour matrices multiply, so nothing about §3.2 changes and the
 * engine needed no edit at all: a whole style costs a filter rebuild and no pixel work.
 *
 * `order = 1` puts it after Adjustments in the rail; application order is the fold's
 * domain rank, and both are `Color`, so a style and a correction compose in the order
 * the user made them.
 */
object FilterTool : EditorTool {

    /** The undertone's own strength. The look's is [Intensity] — see `undertoneMatrix`. */
    val Palette = FloatParam(
        key = "palette",
        label = "Palette",
        range = 0f..100f,
        default = 100f,
        detent = 100f,
        unit = "%"
    )

    val Intensity = FloatParam(
        key = "intensity",
        label = "Intensity",
        range = 0f..100f,
        default = 100f,
        detent = 100f,
        unit = "%"
    )

    val Undertone = PreviewChoiceParam(
        key = "undertone",
        options = PhotoUndertone.entries.toList(),
        default = PhotoUndertone.Neutral,
        labelOf = PhotoUndertone::label,
        section = "Undertone",
        strength = Palette
    )

    val Filter = PreviewChoiceParam(
        key = "filter",
        options = PhotoFilter.entries.toList(),
        default = PhotoFilter.Original,
        labelOf = PhotoFilter::label,
        section = "Look",
        strength = Intensity
    )

    /** Tone is exposure and Colour is warmth — both matrices already exist in `adjust`. */
    val Tone = PadParam(
        key = "tone",
        label = "Tone & Colour",
        xLabel = "Colour",
        yLabel = "Tone",
        xRange = -100f..100f,
        yRange = -100f..100f,
        default = ParamPoint(0f, 0f)
    )

    override val manifest = ToolManifest(
        id = "filter",
        title = "Styles",
        summary = "Undertones, looks and a tone pad",
        // Its own glyph: a rail of identical squares is a mistake made twice already.
        icon = Icons.Static.MagicWand,
        domain = ToolDomain.Color,
        order = 1,
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Continuous
    )

    /** One scrolling line of fourteen squares uses every pixel the dock is wide. */
    override val wideInspector = true

    /**
     * Every parameter the document carries. The two strengths are declared here and
     * **not** drawn as rows of their own — see [rows].
     */
    override val params: List<ParamSpec<*>> =
        listOf(Undertone, Filter, Palette, Intensity, Tone)

    /**
     * The two strips, then the pad — and neither strength.
     *
     * `Palette` and `Intensity` are drawn *inside* the square they scale, so a row for
     * each would be the same number twice. Hidden rather than removed: the values are
     * still declared, still written and still folded, which is the whole contract
     * [rows] carries (§3.4). The strips are adjacent so they share one scrolling line.
     *
     * **The pad is always shown**, including on an untouched panel (reversed by hand
     * 2026-08-28, having been hidden on defaults for one sitting). It costs 40 dp at
     * the end of a line that is there anyway, and hiding it made the panel's width
     * change the moment a look was picked — a control that appears is a control nobody
     * knew was there.
     */
    override fun rows(params: ParamSet): List<ParamSpec<*>> = listOf(Undertone, Filter, Tone)

    /**
     * Nothing when none of the four parts does anything — the rule every tool follows,
     * so a panel opened and closed leaves the document untouched. The arithmetic is
     * `styleMatrix`, beside the matrices it multiplies.
     */
    override fun stage(params: ParamSet): ToolStage? =
        styleMatrix(params)?.let { RasterStage.Pixels(it) }
}
