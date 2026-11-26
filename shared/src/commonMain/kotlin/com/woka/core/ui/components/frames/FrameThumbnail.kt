package com.woka.core.ui.components.frames

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.rememberReloadKey

private val TILE_WIDTH = 56.dp
private val TILE_HEIGHT = 42.dp
private val TILE_SHAPE = RoundedCornerShape(7.dp)

/**
 * One frame of a file, as a tile in the strip beside it.
 *
 * Split from [FrameStrip] when ME-157 gave it a second mark to draw. The seam is the
 * one already there: the strip lays out and the tile draws, and everything the tile
 * needs arrives as a parameter.
 *
 * **`internal`, not `private`.** It is the strip's own tile and has no other caller;
 * the visibility is what the split cost, and it is why nothing else in `components/`
 * may name it.
 */
@Composable
internal fun FrameThumbnail(
    item: MediaItem,
    frame: ImageFrame,
    isSelected: Boolean,
    isEdited: Boolean,
    colors: FrameStripColors,
    loadPreview: suspend (MediaItem, Int) -> ImageBitmap?,
    onClick: () -> Unit
) {
    var bitmap by remember(item.file.absolutePath, frame.index) {
        mutableStateOf<ImageBitmap?>(null)
    }
    // A Motion frame is the sibling clip, which this strip has no decoder for —
    // it shows the badge over the still's own preview rather than nothing.
    val previewIndex = if (frame.kind == FrameKind.Motion) 0 else frame.index
    // Keyed on the save signal as well (ME-135): one recipe covers every frame in the
    // file, so an edit made on frame 3 changes what all of them draw.
    val reloadKey = rememberReloadKey(item.file.absolutePath)
    LaunchedEffect(item.file.absolutePath, previewIndex, reloadKey) {
        bitmap = loadPreview(item, previewIndex)
    }
    // On the box rather than the image, so it is there while the thumbnail is
    // still loading — and so a screen reader says which frame this is.
    val label = if (frame.kind == FrameKind.Motion) "Motion" else "Frame ${frame.index + 1}"
    Box(
        modifier = Modifier
            .size(TILE_WIDTH, TILE_HEIGHT)
            .clip(TILE_SHAPE)
            .background(colors.tile)
            // Drawn over the picture, inside the tile (`.frame-thumb.selected::after`).
            .then(if (isSelected) Modifier.border(2.dp, colors.selected, TILE_SHAPE) else Modifier)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(frame.index) { detectTapGestures { onClick() } }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (frame.kind == FrameKind.Motion) MotionBadge()
        if (isEdited) EditedDot(Modifier.align(Alignment.TopEnd))
    }
}
