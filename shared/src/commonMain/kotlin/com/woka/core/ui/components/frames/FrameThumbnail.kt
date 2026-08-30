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
import androidx.compose.ui.graphics.Color
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

private val SELECTED_SIZE = 56.dp
private val UNSELECTED_SIZE = 48.dp

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
            .size(if (isSelected) SELECTED_SIZE else UNSELECTED_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderOf(isSelected), RoundedCornerShape(8.dp))
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
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)))
        }
        if (!isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        if (frame.kind == FrameKind.Motion) MotionBadge()
        if (isEdited) EditedDot(Modifier.align(Alignment.TopEnd))
    }
}

private fun borderOf(isSelected: Boolean): Color =
    if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f)
