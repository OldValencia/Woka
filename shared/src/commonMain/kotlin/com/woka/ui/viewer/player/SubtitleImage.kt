package com.woka.ui.viewer.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One decoded bitmap subtitle rectangle and where it was authored to sit, in the
 * canvas coordinates of [SubtitleImage] (Step 10 / §2.2).
 */
@Immutable
data class SubtitleImageRect(
    val bitmap: ImageBitmap,
    val x: Int,
    val y: Int
)

/**
 * A bitmap subtitle cue: *n* positioned rectangles on the canvas the subtitle
 * stream declares (`codecpar.width/height` — 1920×1080 for a typical PGS track),
 * which is independent of the video's own resolution.
 *
 * A single [ImageBitmap] cannot express this: PGS and VobSub carry their own
 * placement, so the geometry has to travel with the pixels rather than be decided
 * by the overlay the way text placement is.
 *
 * A canvas of 0 means the stream declared none — the rects are then in the video's
 * own coordinate space and the renderer substitutes the frame size.
 */
@Immutable
data class SubtitleImage(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val rects: List<SubtitleImageRect>
)
