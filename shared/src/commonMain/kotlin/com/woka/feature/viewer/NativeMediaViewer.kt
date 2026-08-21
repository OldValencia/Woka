package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem

/**
 * [onEdit] carries the **frame** as well as the path: a viewer stopped on frame 5 of an
 * animation and asked to edit it means that frame, not the file's first one.
 *
 * [isFrozen] is "something is mounted over this viewer" — the editor, which leaves it
 * composed underneath so Cancel returns to the same zoom, pan and frame. A frozen
 * viewer's animation stops rather than decoding frames nobody can see.
 */
@Composable
expect fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit,
    onEdit: (String, Int) -> Unit,
    isFrozen: Boolean = false
)
