package com.woka.feature.editor.engine

import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.recipe.carrierFor
import com.woka.feature.editor.save.formatFor
import java.io.File

/**
 * Routes a path to the engine for its medium, using `File.mediaKind()` — the
 * app's single answer to what a file is. No extension list lives here, and none
 * may: the one in `core/data/MediaKind.kt` exists because six copies of it
 * disagreed with each other.
 *
 * **Video returns null for now.** The video engine is Phase 5b; until it exists,
 * Edit is correctly unavailable on a video rather than opening a shell with no
 * canvas behind it. That is one line to change when ME-201 lands.
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
        if (!file.isFile || file.mediaKind() != MediaKind.Image) return EditCapability.Unavailable
        return when {
            carrierFor(file) != null -> EditCapability.Recorded
            formatFor(file) != null -> EditCapability.Destructive
            else -> EditCapability.Unavailable
        }
    }

    /**
     * **Refusal moved forward, 2026-08-19.** This used to build an engine for all
     * eleven image formats and let eight of them fail at save time with "convert
     * first" — spending the user's work before saying it could not be kept. A
     * format with neither a carrier nor an encoder now has no engine at all, so
     * both doors grey out before anything is edited.
     */
    override fun create(path: String, frameIndex: Int): EditorEngine? {
        if (capability(path) == EditCapability.Unavailable) return null
        return ImageEditorEngine(File(path), frameIndex, registry)
    }
}
