package com.woka.support

import com.woka.core.data.MediaKind
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolStage

/**
 * A complete [EditorTool] that exists only to be driven by tests — the shell's
 * specification before any real tool exists (ME-011).
 *
 * It declares one of every [ParamSpec] type ([FixtureParams]), so an inspector,
 * a rail entry, a shortcut table or a settings row can be built and asserted
 * against a tool that exercises every row renderer at once. Written *after* the
 * shell it would only confirm what the shell already does.
 *
 * **One class, six variants.** Cardinality and interaction are constructor
 * parameters rather than six subclasses (ME-011 step 1); the constructor is the
 * factory, so there is a single entry point and no second spelling of the manifest
 * to drift from this one.
 *
 * [produce] is the whole of what the tool "performs", and it defaults to null —
 * the no-op configuration the fold is supposed to skip. A fold test passes a real
 * stage; a registry test does not care and passes nothing.
 *
 * Deliberately **not** a `CanvasTool`: `ToolInteraction.Canvas` is a manifest
 * value and can be declared without an overlay, and nothing before the shell
 * dispatches to one. ME-101 is where an overlay fixture earns its place.
 */
class FixtureTool(
    id: String = "fixture",
    domain: ToolDomain = ToolDomain.Color,
    order: Int = 0,
    cardinality: ToolCardinality = ToolCardinality.Single,
    interaction: ToolInteraction = ToolInteraction.Continuous,
    supports: Set<MediaKind> = setOf(MediaKind.Image, MediaKind.Video),
    private val produce: (ParamSet) -> ToolStage? = { null }
) : EditorTool {

    override val manifest = ToolManifest(
        id = id,
        title = id,
        summary = "Fixture tool — one spec of every type",
        icon = Icons.Static.Close,
        domain = domain,
        order = order,
        supports = supports,
        cardinality = cardinality,
        interaction = interaction
    )

    override val params: List<ParamSpec<*>> = FixtureParams.all

    private val calls = mutableListOf<ParamSet>()

    /**
     * Every [ParamSet] [stage] was asked about, in call order.
     *
     * Recorded because "the fold reached this tool" and "the fold produced a
     * stage" are different claims: a tool that returns null is skipped, and
     * without this a test cannot tell that outcome from the tool never being
     * consulted at all. Copied out, so a caller cannot append to the record it is
     * asserting on.
     */
    val staged: List<ParamSet> get() = calls.toList()

    override fun stage(params: ParamSet): ToolStage? {
        calls += params
        return produce(params)
    }
}
