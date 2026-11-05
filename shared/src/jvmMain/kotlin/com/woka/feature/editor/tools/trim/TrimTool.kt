package com.woka.feature.editor.tools.trim

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.core.ui.icons.Icons
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
import com.woka.feature.viewer.video.formatTime
import kotlinx.serialization.builtins.ListSerializer

/**
 * `✂ Clips` — the whole cut as one ordered clip list (ME-210, `timeline.md` §3.1). Named
 * `Trim` until 2026-11-05, when the author renamed it for all it does; the id stays `trim`,
 * because every film edited before carries it in its recipe.
 *
 * **One tool, not three** (decision 2026-09-03): split, delete and reorder are gestures on
 * one object, the strip, and three rail entries for one object is the overload the feature
 * is defined against. So the tool owns one [ObjectParam] and the strip writes it — the
 * shell renders nothing for that spec, which is what the type exists to say.
 *
 * **`Single`**, for markup's reason: a new list replaces the old one, so undo is one entry
 * per gesture and a committed cut can still be edited. **Video only**, the second override
 * of the manifest's default after HDR's — a still has no time to cut.
 *
 * [stage] returns a [ClipStage], which is not a `RasterStage`: the raster fold drops it
 * with the `as?` it already ends in, and the render reads it. That is the 2026-08-16
 * decision that `stage()` returns a marker rather than a matrix, finally used.
 */
object TrimTool : EditorTool {

    val Clips = ObjectParam("clips", ListSerializer(TrimClip.serializer()), emptyList())

    override val manifest = ToolManifest(
        id = "trim",
        title = "Clips",
        summary = "Cut, order, speed and freeze",
        icon = Icons.Static.Trim,
        domain = ToolDomain.Time,
        order = 0,
        supports = setOf(MediaKind.Video),
        cardinality = ToolCardinality.Single,
        interaction = ToolInteraction.Instant
    )

    override val params: List<ParamSpec<*>> = listOf(Clips)

    override fun stage(params: ParamSet): ToolStage? =
        params[Clips].takeIf { it.isNotEmpty() }?.let { ClipStage(it) }

    /**
     * The only line the inspector has for this tool: how long the result is and how many
     * pieces it is in, plus the two gestures nobody would guess. The actions themselves
     * live on the strip, where the play head and the selection are (§4.2).
     */
    override fun footnote(params: ParamSet, frame: IntSize): String {
        val clips = params[Clips]
        if (clips.isEmpty()) return "Split at the play head · drag a clip to move it"
        val count = if (clips.size == 1) "one clip" else "${clips.size} clips"
        return "${formatTime(clips.totalMs())} · $count · right-click the strip to reset"
    }

    /** The op the strip commits for [clips]. */
    fun opFor(clips: List<TrimClip>): ToolOp =
        ToolOp(manifest.id, ParamSet().with(Clips, clips))

    /**
     * The clip list [document] describes — the one in flight first, then the committed
     * one — or null for a document nothing has cut, which plays the whole file.
     */
    fun clipsIn(document: EditDocument): List<TrimClip>? {
        val op = document.pending?.takeIf { it.toolId == manifest.id }
            ?: document.opsOf(manifest.id).lastOrNull()
            ?: return null
        return op.params[Clips].takeIf { it.isNotEmpty() }
    }
}

/** What a cut folds to: the clip list, for the render and the timeline walk to read. */
data class ClipStage(val clips: List<TrimClip>) : ToolStage
