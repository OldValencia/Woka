package com.woka.feature.viewer.shell.frames

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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

/** What the strip occupies on the left edge, so the Prev arrow can step aside. */
val FRAME_STRIP_WIDTH = 80.dp

/**
 * The frames inside one file, as a vertical strip down the left edge.
 *
 * **The axis carries the meaning.** The carousel runs horizontally along the
 * bottom and moves *between* files; this runs vertically and moves *within*
 * one. Stacked as two horizontal rows — which is how it was first built — they
 * were the same shape in the same axis, one above the other, and nothing but a
 * label could have said which was which. macOS Preview reaches the same answer
 * from the same problem.
 *
 * Left rather than right because the right edge is already spoken for: the
 * Next button and all three vertical popovers are anchored there. The
 * treatment still matches the carousel — 8 dp radius, unselected dimmed rather
 * than shrunk away — so the two read as one family.
 */
@Composable
internal fun FrameStrip(
    item: MediaItem,
    frames: List<ImageFrame>,
    selectedIndex: Int,
    loadPreview: suspend (MediaItem, Int) -> ImageBitmap?,
    onSelectFrame: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(frames.size, key = { frames[it].index }) { position ->
            val frame = frames[position]
            FrameThumbnail(
                item = item,
                frame = frame,
                isSelected = frame.index == selectedIndex,
                loadPreview = loadPreview,
                onClick = { onSelectFrame(frame.index) }
            )
        }
    }
}

@Composable
private fun FrameThumbnail(
    item: MediaItem,
    frame: ImageFrame,
    isSelected: Boolean,
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
    }
}

private fun borderOf(isSelected: Boolean): Color =
    if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f)
