package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.frames.FRAME_STRIP_CARD
import java.io.File

/**
 * The frame strip of a file holding more than one picture (ME-158), at the canvas's
 * top-left: 12 / 16 in, as in the viewer, so entering the editor recolours it and does
 * not move it (RD-204).
 *
 * **It and the scope line appear together** — both behind `EditorFrames.allowsPerFrame`,
 * through the one `perFrame` value `EditorScreen` builds: the strip chooses which picture
 * is being edited, the line at the inspector's head says whom the edit is for.
 */
@Composable
internal fun BoxScope.EditorFrameBand(controls: EditorFrameControls, file: File) {
    // A film's clips are chosen on its own band, under the picture (ME-215).
    if (controls.frames.clips) return
    EditorFrameStrip(
        file = file,
        frameIndex = controls.frameIndex,
        onSelectFrame = controls.onSelectFrame,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
    )
}

/**
 * What the canvas gives up to the strip on its start edge, beyond the room's own 34 dp:
 * the card's width (`.has-frames .stage`, 104 = 34 + 70).
 *
 * Inset rather than drawn under, so the picture is centred in what is left —
 * `MediaViewerPlayer.navStartInset` is the same measurement made in the viewer, and
 * the reason it is a padding rather than a smaller canvas is that the picture's own
 * coordinate space must not move (ME-116's zoom fidelity depends on it).
 */
internal fun canvasInsetFor(controls: EditorFrameControls?): Dp =
    if (controls != null && !controls.frames.clips) FRAME_STRIP_CARD else 0.dp
