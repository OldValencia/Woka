package com.woka.feature.editor.engine.video

import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.tools.trim.TrimTool
import java.io.File

/**
 * The video engine's answers about **clips** (ME-215): what each carries, where a sitting
 * opens, how many there are, and what This Clip may edit. Between [VideoEditorOutputs] and
 * `VideoEditorEngine`, split out when ME-215 pushed the engine past the size mandate —
 * none of it draws, plays or writes.
 */
internal abstract class VideoEditorClips(
    private val file: File,
    registry: EditorToolRegistry,
    capability: EditCapability
) : VideoEditorOutputs(file, registry, capability) {

    /** What the file carries, read once: the shared layer and every clip's own (ME-215). */
    private val stored by lazy { storedVideoPayload(file) }

    /** A film opens on its shared layer, the All Clips document (ME-288, ME-215). */
    override fun storedDocument(): EditDocument = EditDocument(stored.ops)

    override fun storedDocumentIn(frameIndex: Int): EditDocument = stored.documentFor(frameIndex)

    override fun storedBase(): EditDocument = storedDocument()

    override fun openingFrame(requested: Int): Int = NO_CLIP

    override val frames: EditorFrames get() = EditorFrames(overridden = stored.frames.keys, clips = true)

    /** One scope entry per clip, so the switch appears once the film is cut in two. */
    override fun framesIn(document: EditDocument): EditorFrames =
        frames.copy(count = TrimTool.clipsIn(document)?.size ?: 1)

    /**
     * **Nothing is unavailable with a clip selected** (ME-221): a cut lands in the shared
     * layer whatever is selected, and Crop, Transform, Resize and Cover clear the selection
     * as they open (`EditorScreen`) — one film has one frame size.
     */
    override fun clipNumberOf(document: EditDocument, id: String?): Int? =
        TrimTool.clipsIn(document)?.indexOfFirst { it.id == id }?.takeIf { it >= 0 }?.plus(1)
}
