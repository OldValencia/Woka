package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.frames.FrameStrip
import com.woka.core.ui.components.frames.rememberEditedFrames
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.feature.viewer.shell.MediaThumbnails
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The editor's mounting of the shared frame strip (ME-158), down its left edge.
 *
 * **Vertical, exactly as in the viewer.** The axis carries the meaning and this
 * project made that call once already: the carousel runs horizontally along the bottom
 * and moves *between* files, the strip runs vertically and moves *within* one. A
 * horizontal row of frames here would be a third band in a stack that already has the
 * inspector and the rail, in the same axis as both.
 *
 * **It does not reuse `rememberFrameStrip`.** That models the *viewer's* strip: it
 * appends a Live Photo's motion sibling, which is a different file the editor has no
 * decoder for and `FrameStripModel.editableFrameOf` already exists to keep out. The
 * editor's list is this file's viewable frames and nothing else.
 *
 * Read off the composition thread for the reason `rememberFrameStrip` gives — counting
 * without decoding is cheap, but opening a 1296-stream HEIC is still milliseconds — and
 * a decoder that throws leaves the list empty, which draws nothing.
 */
@Composable
internal fun EditorFrameStrip(
    file: File,
    frameIndex: Int,
    onSelectFrame: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var frames by remember(file) { mutableStateOf(emptyList<ImageFrame>()) }
    LaunchedEffect(file) {
        frames = withContext(ImageBitmapCache.ioDispatcher) {
            runCatching { stillDecoderFor(file).frameSet(file).viewable }
                .getOrDefault(emptyList())
        }
    }

    val item = remember(file) { MediaItem.Image(file) }
    FrameStrip(
        item = item,
        frames = frames,
        selectedIndex = frameIndex,
        loadPreview = { media, frame -> MediaThumbnails.loadPreview(media, frame) },
        onSelectFrame = onSelectFrame,
        modifier = modifier,
        editedFrames = rememberEditedFrames(file.absolutePath)
    )
}
