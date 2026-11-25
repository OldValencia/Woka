package com.woka.feature.editor.tools.filter

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamPoint
import androidx.compose.ui.unit.IntSize
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
 * `order = 0` puts Styles before Adjust in the inspector's tabs, as the prototype has
 * them (RD-201); application order is the fold's domain rank, and both are `Color`, so a
 * style and a correction compose in the order the user made them.
 */
object FilterTool : EditorTool {

    /** The undertone's own strength. The look's is [Intensity] — see `undertoneMatrix`. */
    val Palette = FloatParam(
        key = "palette",
        label = "Palette",
        range = 0f..100f,
        default = 100f,
        detent = 100f,
        unit = "%",
        icon = Icons.Static.Tint
    )

    val Intensity = FloatParam(
        key = "intensity",
        label = "Intensity",
        range = 0f..100f,
        default = 100f,
        detent = 100f,
        unit = "%",
        icon = Icons.Static.Vibrance
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

    /**
     * The two axes shape the **chosen look** — never the photograph — and do nothing at
     * [PhotoFilter.Original], where there is no look to shape (ME-150). The key stays
     * `tone`: it is in every document written under ME-141, holding the same point.
     */
    val Tone = PadParam(
        key = "tone",
        label = "Look shape",
        xLabel = "Vividness",
        yLabel = "Brightness",
        xRange = -100f..100f,
        yRange = -100f..100f,
        default = ParamPoint(0f, 0f),
        xIcon = Icons.Static.Saturation,
        yIcon = Icons.Static.Brightness
    )

    override val manifest = ToolManifest(
        id = "filter",
        title = "Styles",
        summary = "Undertones, looks and a tone pad",
        // Its own glyph: a rail of identical squares is a mistake made twice already.
        // The wand went to Auto, which is what a wand means (ME-174).
        icon = Icons.Static.Palette,
        domain = ToolDomain.Color,
        order = 0,
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Continuous
    )

    /** Every parameter the document carries, in the order the panel reads in. */
    override val params: List<ParamSpec<*>> =
        listOf(Undertone, Filter, Palette, Intensity, Tone)

    /**
     * Both rows of tiles, then the well — only what can do something (RD-203, decided by the
     * author 2026-11-25). Palette scales the undertone, so it shows once one is chosen;
     * Intensity and the look's shape (Vividness, Brightness — the pad's two axes as scrub
     * rows) act on the look, so they show once a look is chosen. At Neutral and Original
     * the panel is the two rows of tiles and nothing else.
     */
    override fun rows(params: ParamSet, sourceSize: IntSize): List<ParamSpec<*>> =
        listOfNotNull(
            Undertone,
            Filter,
            Palette.takeIf { params[Undertone] != Undertone.default },
            Intensity.takeIf { params[Filter] != Filter.default },
            Tone.takeIf { params[Filter] != Filter.default }
        )

    /**
     * Nothing when none of the four parts does anything — the rule every tool follows,
     * so a panel opened and closed leaves the document untouched. The arithmetic is
     * `styleMatrix`, beside the matrices it multiplies.
     */
    override fun stage(params: ParamSet): ToolStage? =
        styleMatrix(params)?.let { RasterStage.Pixels(it) }
}
