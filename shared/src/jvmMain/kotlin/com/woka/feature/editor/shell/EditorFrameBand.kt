package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.frames.FRAME_STRIP_WIDTH
import java.io.File

/**
 * Everything the editor draws for a file holding more than one picture: the scope
 * switch under the top bar (ME-156) and the frame strip down the left edge (ME-158).
 *
 * **They appear together and never apart.** One rule — `EditorFrames.allowsPerFrame` —
 * and one sentence: the strip chooses which picture is being edited, the switch says
 * who the edit is for, and neither means anything without the other. Drawn from one
 * composable so that rule cannot be half-applied by a later edit to `EditorScreen`.
 */
@Composable
internal fun BoxScope.EditorFrameBand(controls: EditorFrameControls, file: File) {
    EditorFrameScopeStrip(
        frames = controls.frames,
        scope = controls.scope,
        frameIndex = controls.frameIndex,
        onScopeChange = controls.onScopeChange,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    EditorFrameStrip(
        file = file,
        frameIndex = controls.frameIndex,
        onSelectFrame = controls.onSelectFrame,
        modifier = Modifier.align(Alignment.CenterStart)
    )
}

/**
 * What the canvas gives up to the strip on its start edge.
 *
 * Inset rather than drawn under, so the picture is centred in what is left —
 * `MediaViewerPlayer.navStartInset` is the same measurement made in the viewer, and
 * the reason it is a padding rather than a smaller canvas is that the picture's own
 * coordinate space must not move (ME-116's zoom fidelity depends on it).
 */
internal fun canvasInsetFor(controls: EditorFrameControls?): Dp =
    if (controls != null) FRAME_STRIP_WIDTH else 0.dp
