package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import com.woka.feature.editor.core.ClipScope
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.tools.trim.TrimTool
import java.io.File

/**
 * The video engine's answers about **clips** (ME-215): what each carries, where a sitting
 * opens, how many there are, and what This Clip may edit. Between [VideoEditorOutputs] and
 * `VideoEditorEngine`, split out when ME-215 pushed the engine past the size mandate. It plays
 * and writes nothing; what it draws is the clips' own surfaces — the timeline and the Clip
 * panel, both on the canvas's player (RD-301, RD-302).
 */
internal abstract class VideoEditorClips(
    private val file: File,
    registry: EditorToolRegistry,
    capability: EditCapability
) : VideoEditorOutputs(file, registry, capability) {

    /** Owned here so the canvas, `Thumbnail`, the timeline and the Clip panel share one decoder. */
    protected val playback = VideoPlayerHolder()

    /** The docked timeline (RD-301), on the canvas's holder: one decoder behind picture, lanes and stills. */
    @Composable
    override fun Timeline(document: EditDocument, edits: EditorTimelineScope, modifier: Modifier) =
        VideoEditorTimeline(playback, document, edits, modifier, sourceSize)

    override fun timelineKey(key: Key): Boolean = playback.timelineKey(key)

    /** Clip's panel is the engine's (RD-302): its rows act on the timeline's clips. */
    override fun drawsPanel(toolId: String): Boolean = toolId == TrimTool.manifest.id

    @Composable
    override fun ToolPanel(toolId: String, document: EditDocument, selectedClip: String?) {
        if (drawsPanel(toolId)) ClipPanel(playback, document, selectedClip)
    }

    /** What the file carries, read once: the shared layer and every clip's own (ME-215). */
    private val stored by lazy { storedVideoPayload(file) }

    /** A film opens on its shared layer, the All Clips document (ME-288, ME-215). */
    override fun storedDocument(): EditDocument = EditDocument(stored.ops)

    override fun storedDocumentIn(frameIndex: Int): EditDocument = stored.documentFor(frameIndex)

    override fun storedBase(): EditDocument = storedDocument()

    override fun openingFrame(requested: Int): Int = NO_CLIP

    /** No picture to measure; the film's even-loudness gain once its sound is (ME-246). */
    override val stats: SourceStats?
        get() = evenGainOf(file)?.let { SourceStats(emptyList(), 0f, 1f, 0.5f, soundGain = it) }

    override val frames: EditorFrames get() = EditorFrames(overridden = stored.frames.keys, clips = true)

    /** One scope entry per clip, so the switch appears once the film is cut in two. */
    override fun framesIn(document: EditDocument): EditorFrames =
        frames.copy(count = TrimTool.clipsIn(document)?.size ?: 1)

    /**
     * **Nothing is unavailable with a clip selected** (ME-221): a cut lands in the shared
     * layer whatever is selected, and Crop, Transform, Resize and Cover clear the selection
     * as they open (`EditorScreen`) — one film has one frame size.
     */
    override fun clipScopeOf(document: EditDocument, id: String?): ClipScope? {
        val clips = TrimTool.clipsIn(document) ?: return null
        val at = clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return ClipScope(number = at + 1, lengthMs = clips[at].lengthMs, still = clips[at].isFreeze)
    }
}
