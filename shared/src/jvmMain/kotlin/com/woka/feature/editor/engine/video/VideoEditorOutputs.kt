package com.woka.feature.editor.engine.video

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.render.RenderVideo
import com.woka.feature.editor.engine.video.render.renderOnlyNote
import com.woka.feature.editor.engine.video.render.renderVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything the video engine **writes** — `Done`, Revert and `Render video…` — apart from
 * what it draws, which is `VideoEditorEngine`.
 *
 * Split when the render (ME-291) and the Resize note pushed the engine past the size
 * mandate, along the line `VideoEditorWrites` had already drawn: none of this touches a
 * player, a frame or a composable. An abstract class rather than a delegate because the
 * members are `EditorEngine`'s own overrides, and Kotlin delegates a whole interface or
 * nothing.
 */
internal abstract class VideoEditorOutputs(
    private val file: File,
    private val registry: EditorToolRegistry,
    capability: EditCapability
) : EditorEngine {

    /** What a write does to the file, and the stamp it carries — see `VideoEditorWrites`. */
    private val writes = VideoEditorWrites(file, capability)

    override suspend fun save(layers: EditLayers): EditSaveResult =
        withContext(Dispatchers.IO) { writes.save(layers) }

    /** The whole edit under All Clips; one clip's look under This Clip (ME-215). */
    override suspend fun revert(scope: EditScope, frameIndex: Int): EditSaveResult =
        withContext(Dispatchers.IO) { writes.revert(scope, frameIndex) }

    /** `Render video…` beside `Done` — a new file, the source untouched (ME-291). */
    override val exports = listOf(RenderVideo)

    override fun toolNote(toolId: String): String? = renderOnlyNote(toolId)

    override suspend fun export(export: EditorExport, layers: EditLayers, progress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            renderVideo(file, layers.base, looksFor(layers), registry, sourceSize, { job.isActive }, progress)
        }

    abstract override val sourceSize: IntSize

    /**
     * Each clip's own look for a render (ME-215): this sitting's where it touched the clip,
     * what the file carries otherwise — a render is of the edit as it stands, saved or not.
     */
    private fun looksFor(layers: EditLayers): (Int) -> List<ToolOp> {
        val stored = storedVideoPayload(file)
        return { key ->
            if (key in layers.touched) layers.frames[key]?.ops.orEmpty()
            else stored.frames[key]?.ops.orEmpty()
        }
    }
}
