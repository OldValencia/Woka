package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import com.woka.app.shell.LocalCanvasOverride
import com.woka.core.data.StillColorInfo

/**
 * Hands the open picture's colour to the top bar's pill, and takes it back on the way out
 * (ME-303).
 *
 * `PublishCanvasZoom`'s shape, in its own file because that one is named after zoom and
 * this is not zoom. What reads it is `CanvasZoomPill`, which the editor brings only when
 * it was opened from the grid; opened over a viewer the centre slot is that viewer's pill
 * and it has answered from `state.stillColor` already. This still publishes on both paths,
 * because a publisher that knew which door it came through would be the branch the seam
 * exists to prevent.
 *
 * **The engine answers, not the file.** `EditorEnginePicture.sourceColor` is SDR for a
 * video (ME-302), which is what keeps ME-180's rule true here without this file, or the
 * canvas host above it, ever naming a `MediaKind`.
 */
@Composable
internal fun PublishCanvasColor(color: StillColorInfo) {
    val override = LocalCanvasOverride.current

    // Written after composition rather than during it, and put back on the way out so a
    // pill that outlives the editor stops claiming to describe its picture.
    SideEffect { override.sourceColor = color }
    DisposableEffect(Unit) {
        onDispose { override.sourceColor = StillColorInfo.Sdr }
    }
}
