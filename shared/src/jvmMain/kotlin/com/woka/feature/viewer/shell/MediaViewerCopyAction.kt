package com.woka.feature.viewer.shell

import com.woka.core.data.MediaItem
import com.woka.feature.editor.copyMedia

/**
 * Copy the current photo frame or the original video, with the shared explanation. The
 * copy outlives the viewer (`copyMedia`), so Esc straight after Ctrl+C does not cancel it.
 */
internal fun rememberCopyAction(activeItem: MediaItem?, frameIndex: Int): () -> Unit = {
    activeItem?.let { item -> copyMedia(item, frameIndex) }
}
