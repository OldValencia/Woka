package com.woka.feature.editor.tools.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditorToolScope

/**
 * Choosing a ratio reshapes the rectangle **there and then**.
 *
 * Without this the chip lit up and nothing moved: the aspect was stored and only
 * consulted the next time a handle was dragged, which is indistinguishable from a
 * control that does not work. `Free` reshapes nothing — it is the absence of a lock,
 * not a shape of its own.
 *
 * Split from `CropOverlay` when RD-205 took that file past the size mandate.
 *
 * **Nothing while another edit is in flight** (RD-205). A straighten dragged on the dial
 * changes the frame with every step, which re-runs this effect; a rect scaled into the new
 * frame can be a pixel off its ratio, and fitting it here would replace the dial's pending
 * operation with a crop — the drag would stop under the pointer.
 */
@Composable
internal fun CropAspectEffect(scope: EditorToolScope, frame: IntSize, ratio: Float?) {
    LaunchedEffect(ratio, frame) {
        if (ratio == null || scope.document.pending != null) return@LaunchedEffect
        val rect = scope.document.cropRectIn(frame)
        val fitted = rect.fittedTo(ratio, frame)
        if (fitted == rect) return@LaunchedEffect
        scope.preview(scope.document.cropOpWith(fitted, frame))
        scope.commit()
    }
}
