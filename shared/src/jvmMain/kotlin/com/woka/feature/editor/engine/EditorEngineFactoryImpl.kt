package com.woka.feature.editor.engine

import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.video.VideoEditorEngine
import com.woka.feature.editor.recipe.carrierFor
import com.woka.feature.editor.save.formatFor
import java.io.File

/**
 * Routes a path to the engine for its medium, using `File.mediaKind()` — the
 * app's single answer to what a file is. No extension list lives here, and none
 * may: the one in `core/data/MediaKind.kt` exists because six copies of it
 * disagreed with each other.
 *
 * **A video now opens too** (ME-201), and answers `Preview`: the canvas draws the
 * player's frame through the folded document, so the picture tools work on it — and
 * neither save exists yet, which is what that capability says out loud. `save/`'s
 * first ticket asks a video the same carrier question an image is asked and deletes
 * the case.
 *
 * `EditorEngineFactory` is a `commonMain` interface and this is its JVM half,
 * because a `commonMain` module cannot name `ImageEditorEngine` (`di.md`).
 */
class EditorEngineFactoryImpl(
    private val registry: EditorToolRegistry
) : EditorEngineFactory {

    /**
     * Which of §5.1's buckets the file is in — the two extension tables that
     * already exist, asked in order and never duplicated.
     *
     * A file that is not there is not editable, and nothing downstream would say
     * so: `mediaKind()` reads the extension alone, and `SourceStamp` answers 0/0
     * for a missing file rather than failing. Without this the grid's Edit entry
     * stays enabled for a file deleted out from under it, and the error arrives
     * as a failed save instead of a greyed menu item.
     */
    override fun capability(path: String): EditCapability {
        val file = File(path)
        if (!file.isFile) return EditCapability.Unavailable
        return when (file.mediaKind()) {
            MediaKind.Image -> imageCapability(file)
            // One phase only, and the same answer for every container: the editor
            // opens and neither save exists yet (ME-201). ME-280 asks a video the
            // carrier question below and deletes the case.
            MediaKind.Video -> EditCapability.Preview
            null -> EditCapability.Unavailable
        }
    }

    /** The two extension tables that already exist, asked in order and never duplicated. */
    private fun imageCapability(file: File): EditCapability = when {
        carrierFor(file) != null -> EditCapability.Recorded
        formatFor(file) != null -> EditCapability.Destructive
        else -> EditCapability.Unavailable
    }

    /**
     * **Refusal moved forward, 2026-08-19.** This used to build an engine for all
     * eleven image formats and let eight of them fail at save time with "convert
     * first" — spending the user's work before saying it could not be kept. A
     * format with neither a carrier nor an encoder now has no engine at all, so
     * both doors grey out before anything is edited.
     */
    override fun create(path: String, frameIndex: Int): EditorEngine? {
        val file = File(path)
        val capability = capability(path)
        if (capability == EditCapability.Unavailable) return null
        // **[frameIndex] is an image's own**: it selects the picture inside a container
        // that holds several. A video has one stream and its position is time, so the
        // video engine takes no frame — which is also why it is not keyed on one.
        return when (file.mediaKind()) {
            MediaKind.Video -> VideoEditorEngine(file, registry, capability)
            else -> ImageEditorEngine(file, frameIndex, registry, capability)
        }
    }
}
