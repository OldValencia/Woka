package com.woka.feature.editor.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind

/**
 * Everything about editing that knows what pixels are — behind one interface
 * (§3.3).
 *
 * `EditorSession`, the history, the tool contract, the schema-driven inspector and
 * the save banner all sit above this and none of them can name a bitmap. The image
 * engine folds to a raster recipe and draws a bitmap; the video engine folds to a
 * timeline edit and draws the player plus a trim strip, and its saver runs ffmpeg
 * with `-c copy`. The shell composes [Surface] and **never branches on media
 * kind** — that is what makes "the video editor inherits the shell" a fact the
 * compiler enforces rather than an intention.
 *
 * **The recipe type stays inside the implementation.** There is no `recipe()` here
 * on purpose: the moment the shell can name `RasterRecipe`, every file that
 * touches it becomes a video ticket later.
 */
interface EditorEngine {

    val kind: MediaKind

    /** What operations address, in source pixels — never the canvas or the proxy. */
    val sourceSize: IntSize

    /** The live canvas for this medium, drawing [document] as it stands. */
    @Composable
    fun Surface(document: EditDocument, modifier: Modifier)

    /**
     * Writes [document] to [target]. Runs off the UI thread and reports through
     * [EditSaveResult] rather than throwing: §4.5's banner needs an outcome, and
     * the source must survive every failure path.
     */
    suspend fun save(document: EditDocument, target: SaveTarget): EditSaveResult
}
