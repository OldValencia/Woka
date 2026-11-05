package com.woka.feature.editor.tools.cover

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolManifest
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.viewer.video.formatTime
import kotlinx.serialization.builtins.serializer

/** No frame chosen: the grid keeps guessing (`VideoThumbnailHeuristics`). */
private const val NONE = -1L

/**
 * `Cover` — the frame the grid tile shows (ME-287, `save.md` §3.6).
 *
 * **Pinned to the clip under the play head**: [Clip] and [SourceAt] say which frame of which
 * clip it was, so a reorder or a trim of another clip keeps it on what the author saw — kept
 * as a timeline position alone, it slid onto whatever moved under it (1.0.6 review). [At], the
 * timeline position, is the fallback for a clip cut away and for edits written before. The tile
 * draws it **through the fold** — a frozen first frame with a title on it is a title card
 * on the grid, which is the point (`backlog.md` §950).
 *
 * The play head reaches the button through [atPlayHead], at the moment of the click, so the
 * document stores a number and [stage] stays a pure function of it. `Clear` writes the
 * defaults, which is no cover. Its stage is a [CoverStage], dropped by the picture's fold
 * and read by `RecipeVideoEdits` through [coverIn].
 */
object CoverTool : EditorTool {

    val At = ObjectParam("atMs", Long.serializer(), NONE)
    val Clip = ObjectParam("clip", String.serializer(), "")
    val SourceAt = ObjectParam("sourceMs", Long.serializer(), NONE)
    val Use = ActionParam("use", "Use this frame")
    val Clear = ActionParam("clear", "Pick automatically")

    override val manifest = ToolManifest(
        id = "cover",
        title = "Cover",
        summary = "The frame the grid shows",
        icon = Icons.Static.Image,
        domain = ToolDomain.Output,
        order = 2,
        supports = setOf(MediaKind.Video),
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Instant
    )

    override val params: List<ParamSpec<*>> = listOf(At, Clip, SourceAt, Use, Clear)

    override fun stage(params: ParamSet): ToolStage? = coverOf(params)?.let { CoverStage(it) }

    override fun atPlayHead(spec: ParamSpec<*>, params: ParamSet, playHeadMs: Long, document: EditDocument): ParamSet? {
        if (spec != Use) return null
        val at = playHeadMs.coerceAtLeast(0L)
        val clips = TrimTool.clipsIn(document).orEmpty()
        val under = clips.atTimeline(at)
        val clip = under?.let { clips[it.index] }
        return params.with(At, at).with(Clip, clip?.id.orEmpty()).with(SourceAt, under?.sourceMs ?: NONE)
    }

    override fun footnote(params: ParamSet, frame: IntSize): String =
        coverOf(params)?.let { "The grid shows ${formatTime(it)}" }
            ?: "Picked automatically · put the play head on a frame and use it"

    /** The cover the last of [ops] sets, in timeline time, or null for none. */
    fun coverIn(ops: List<ToolOp>): Long? =
        ops.lastOrNull { it.toolId == manifest.id }?.let { coverOf(it.params) }

    /** The clip id and source millisecond the cover was pinned to, or null for none. */
    fun anchorIn(ops: List<ToolOp>): Pair<String, Long>? {
        val params = ops.lastOrNull { it.toolId == manifest.id }?.params ?: return null
        val clip = params[Clip].takeIf { it.isNotEmpty() } ?: return null
        return params[SourceAt].takeIf { it >= 0L }?.let { clip to it }
    }

    private fun coverOf(params: ParamSet): Long? = params[At].takeIf { it >= 0L }
}

/** What the cover folds to: a timeline position, for the grid's poster to read. */
data class CoverStage(val atMs: Long) : ToolStage
