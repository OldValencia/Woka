package com.woka.feature.viewer.shell

import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import com.woka.core.data.isReadOnlyMedia
import com.woka.feature.editor.EditorNoticeStatus
import com.woka.feature.viewer.shell.frames.FrameStripModel
import java.io.File

/**
 * Was a stub that closed the viewer; the editor now opens *over* it, so the
 * viewer stays mounted and Cancel returns to this exact zoom and frame (§4.1).
 * The frame goes with the path: editing what is on screen means *this* picture,
 * and a strip stopped on frame 5 of an animation was handing over frame 0.
 *
 * A read-only format (PSD family) never reaches the editor: it raises the notice.
 */
internal fun viewerEditAction(
    isVideo: Boolean,
    activeItem: MediaItem?,
    state: MediaViewerState,
    strip: FrameStripModel,
    onEdit: (String, Int) -> Unit
): (() -> Unit)? = editActionOf(isVideo) {
    val file = activeItem?.file ?: File(state.currentId)
    if (file.isReadOnlyMedia) {
        EditorNoticeStatus.show()
    } else {
        onEdit(state.currentId, strip.editableFrameOf(state.frameIndex))
    }
}

/** Applies a size change, and writes it back as the default when "remember" is on. */
internal fun sizeModeSetter(
    state: MediaViewerState,
    settings: WokaSettings,
    repo: SettingsRepository
): (SizeMode) -> Unit = { mode ->
    state.sizeMode = mode
    if (settings.rememberSizeState) persistSizeState(repo, mode)
}
