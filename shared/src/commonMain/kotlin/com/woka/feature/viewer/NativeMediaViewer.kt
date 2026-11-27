package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.feature.editor.EditorTarget

/**
 * [onEdit] carries the **frame** as well as the path: a viewer stopped on frame 5 of an
 * animation and asked to edit it means that frame, not the file's first one — and, for an
 * animation, how its clock stood (RD-501): paused stays paused, Loop as it was.
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
    onEdit: (EditorTarget) -> Unit,
    isFrozen: Boolean = false
)
