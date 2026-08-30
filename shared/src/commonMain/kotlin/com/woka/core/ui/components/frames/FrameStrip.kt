package com.woka.core.ui.components.frames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem

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
 *
 * **In `core/ui/components/` since ME-157**, because the editor mounts it too
 * (ME-158) and `core/` is shared by everyone while a `feature/` is shared by no
 * one. Nothing in it was ever viewer-specific: it takes a `MediaItem`, a list of
 * `ImageFrame`, an index and a `loadPreview` lambda, all `core` types. What stayed
 * behind is `FrameStripState` — `rememberFrameStrip` looks for a Live Photo's
 * motion sibling and models what the *viewer* shows, which is the viewer's use of
 * this component rather than the component.
 */
@Composable
internal fun FrameStrip(
    item: MediaItem,
    frames: List<ImageFrame>,
    selectedIndex: Int,
    loadPreview: suspend (MediaItem, Int) -> ImageBitmap?,
    onSelectFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Frames carrying an edit of their own (ME-157) — empty says nothing at all. */
    editedFrames: Set<Int> = emptySet()
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
                isEdited = frame.index in editedFrames,
                loadPreview = loadPreview,
                onClick = { onSelectFrame(frame.index) }
            )
        }
    }
}
