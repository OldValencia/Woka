package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine

/** Where a `CanvasTool`'s overlay is drawn, once one exists (ME-109, ME-113). */
internal const val CANVAS_HOST_TAG = "editor-canvas"

/**
 * The frame the medium draws itself into.
 *
 * It measures, works out where the picture lands, and composes [EditorEngine.Surface]
 * — and that is the whole of it. **It never names a bitmap, a player or a recipe**,
 * which is what lets the video editor reuse this file rather than acquire its own
 * (§3.3): a trim strip and a photograph are both "something the engine draws inside a
 * measured box".
 *
 * [overlay] receives the geometry rather than measuring itself, because the thing that
 * turns a pointer position into a source coordinate needs exactly these two matrices.
 * A second measurement is a second answer, and the two disagree by a pixel or two at
 * the edges — which reads as a gesture bug rather than as the layout bug it is.
 */
@Composable
internal fun EditorCanvasHost(
    engine: EditorEngine,
    document: EditDocument,
    modifier: Modifier = Modifier,
    overlay: @Composable (EditorCanvasGeometry) -> Unit = {}
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Rebuilt only when a size really changes. `onSizeChanged` fires per layout pass,
    // and two matrices per frame of an animation is work nothing asked for.
    val geometry = remember(engine.sourceSize, canvasSize) {
        EditorCanvasGeometry(engine.sourceSize, canvasSize)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CANVAS_HOST_TAG)
            .onSizeChanged { canvasSize = it },
        contentAlignment = Alignment.Center
    ) {
        // Nothing is drawn before the first measurement: at `IntSize.Zero` the fit
        // scale is a placeholder 1.0, and drawing a picture at native size for one
        // frame is a visible flash on anything large.
        if (canvasSize != IntSize.Zero) {
            val density = LocalDensity.current
            val drawn = with(density) {
                Modifier.size(
                    width = (geometry.sourceSize.width * geometry.scale).toDp(),
                    height = (geometry.sourceSize.height * geometry.scale).toDp()
                )
            }
            // The engine is handed the *drawn size* and fills it. Handing it the scale
            // instead would make every engine repeat this arithmetic, and the second
            // copy is the one that rounds differently.
            Box(modifier = drawn) {
                engine.Surface(document, Modifier.fillMaxSize())
                overlay(geometry)
            }
        }
    }
}
