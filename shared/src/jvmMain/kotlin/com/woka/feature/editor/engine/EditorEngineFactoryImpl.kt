package com.woka.feature.editor.engine

import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.ImageEditorEngine
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

    override fun create(path: String, frameIndex: Int): EditorEngine? {
        val file = File(path)
        return when (file.mediaKind()) {
            MediaKind.Image -> ImageEditorEngine(file, frameIndex, registry)
            MediaKind.Video -> null
            null -> null
        }
    }
}
