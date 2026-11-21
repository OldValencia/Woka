package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.core.data.MediaItem
import com.woka.feature.editor.copyMediaToClipboard
import kotlinx.coroutines.launch

/** Copy the current photo frame or the original video, with the shared explanation. */
@Composable
internal fun rememberCopyAction(activeItem: MediaItem?, frameIndex: Int): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        activeItem?.let { item ->
            scope.launch { copyMediaToClipboard(item, frameIndex) }
        }
    }
}