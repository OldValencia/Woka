package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.core.data.MediaItem
import com.woka.core.utils.copyFilesToClipboard
import com.woka.core.utils.copyImageToClipboard
import kotlinx.coroutines.launch

/**
 * What `Copy` means in the viewer (2026-08-19).
 *
 * **A picture, when there is one.** It used to put the file on the clipboard, which
 * made it a duplicate-in-waiting: pasting into a message gave nothing and pasting into
 * a folder gave a second copy of a photograph the user already had. Copying the *frame
 * on screen* is what the control is reached for, and it is the one way an edit made
 * here leaves Woka at all (§5.3).
 *
 * The current frame, not frame zero: in a burst, `Copy` means the picture being
 * looked at.
 *
 * **A video is still its file.** There is no single picture to hand over, and the file
 * is what another program can do something with — so the behaviour splits on the media
 * rather than the control pretending both are the same thing.
 *
 * Split into its own file because `MediaViewerScreen` is at the size mandate; it is
 * also the only place this rule is written, which is what keeps the grid's menu and
 * this one from drifting.
 */
@Composable
internal fun rememberCopyAction(activeItem: MediaItem?, frameIndex: Int): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        when (activeItem) {
            null -> Unit
            is MediaItem.Image -> {
                scope.launch { copyImageToClipboard(activeItem.file.absolutePath, frameIndex) }
                Unit
            }

            else -> copyFilesToClipboard(listOf(activeItem.file.absolutePath))
        }
    }
}
