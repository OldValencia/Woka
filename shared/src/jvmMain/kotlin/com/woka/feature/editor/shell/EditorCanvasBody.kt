package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.LocalCanvasScale
import kotlin.math.roundToInt

/** The canvas at a known size: zoom, pan, gestures, the picture and its overlay (`EditorCanvasHost`). */
@Composable
internal fun CanvasHostBody(
    engine: EditorEngine,
    document: EditDocument,
    canvasSize: IntSize,
    resetKey: Any?,
    picture: Modifier,
    overlay: @Composable (EditorCanvasGeometry) -> Unit
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(resetKey) {
        zoom = 1f
        pan = Offset.Zero
    }

    // `drawnSize`, not `sourceSize`: after a crop the picture has the crop's shape
    // while operations are still addressed against the original, and measuring the
    // box with the source would letterbox it in the wrong proportions.
    val drawnSize = engine.drawnSize(document)
    val geometry = remember(drawnSize, canvasSize, zoom, pan) {
        EditorCanvasGeometry(drawnSize, canvasSize, zoom, pan)
    }

    // The other half of what the pill needs from whoever owns the canvas (ME-303).
    PublishCanvasColor(engine.sourceColor)

    // The wheel is not the only way in: the top bar's pill drives the same two values.
    PublishCanvasZoom(
        geometry = geometry,
        onZoom = { next -> zoom = next.coerceIn(geometry.minZoom, geometry.maxZoom) },
        onReset = {
            zoom = 1f
            pan = Offset.Zero
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag(CANVAS_HOST_TAG)
            .editorCanvasGestures(
                engine = engine,
                geometry = geometry,
                zoom = zoom,
                onZoomAt = { next, at ->
                    pan = geometry.panForZoomAbout(at, next)
                    zoom = next
                },
                onPan = { dragged -> pan += dragged }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Nothing at a zero size: there the fit scale is a placeholder 1.0.
        if (canvasSize != IntSize.Zero) {
            val density = LocalDensity.current
            // `requiredSize`, **not** `size`: `size` constrains itself to what the
            // parent offers, so the picture could never measure larger than the canvas
            // and zooming past Fit changed the readout while the picture stood still.
            val drawn = with(density) {
                Modifier.requiredSize(
                    width = (geometry.sourceSize.width * geometry.scale).toDp(),
                    height = (geometry.sourceSize.height * geometry.scale).toDp()
                )
            }
            Box(
                // The *clamped* pan, read from the geometry rather than from the raw
                // state, so the picture stops where the matrices say it does. An
                // overlay hit-testing against `canvasToSource` while the box was drawn
                // somewhere else is the disagreement this avoids.
                modifier = drawn.offset {
                    IntOffset(
                        geometry.clampedPan.x.roundToInt(),
                        geometry.clampedPan.y.roundToInt()
                    )
                }.then(picture).shadow(EditorTokens.Canvas.shadow, EditorTokens.Canvas.pictureShape, clip = false)
            ) {
                // What the engine draws at, published rather than passed: `Surface`
                // takes no zoom by design (§3.3), and this is the only surface in the
                // editor allowed to answer with a native decode (ME-116).
                CompositionLocalProvider(LocalCanvasScale provides geometry.scale) {
                    // Rounded, while the overlay above it is not: a crop handle may stand on the corner.
                    engine.Surface(document, Modifier.fillMaxSize().clip(EditorTokens.Canvas.pictureShape))
                }
                overlay(geometry)
            }
        }
    }
}
