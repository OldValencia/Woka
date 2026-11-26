package com.woka.core.ui.components.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem

/** The card's width: a 56 dp tile, 6 dp padding each side, a 1 dp outline (RD-204). */
val FRAME_STRIP_CARD = 70.dp

/** What the strip occupies from the left edge — 12 dp inset and the card — so the Prev arrow can step aside. */
val FRAME_STRIP_WIDTH = 12.dp + FRAME_STRIP_CARD

private val CARD_SHAPE = RoundedCornerShape(12.dp)

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
 * Next button and all three vertical popovers are anchored there.
 *
 * **A card at the top-left since RD-204** (`#viewer/frames`, `#editor/frames`): it hugs
 * its frames and scrolls once they outgrow the height the caller leaves it, in [colors]
 * — glass in the viewer, the room's panel in the editor.
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
    editedFrames: Set<Int> = emptySet(),
    colors: FrameStripColors = FrameStripDefaults.viewer
) {
    LazyColumn(
        modifier = modifier
            .clip(CARD_SHAPE)
            .background(colors.card)
            .border(1.dp, colors.line, CARD_SHAPE)
            // 6 dp inside the 1 dp outline, which `border` draws within the card.
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(frames.size, key = { frames[it].index }) { position ->
            val frame = frames[position]
            FrameThumbnail(
                item = item,
                frame = frame,
                isSelected = frame.index == selectedIndex,
                isEdited = frame.index in editedFrames,
                colors = colors,
                loadPreview = loadPreview,
                onClick = { onSelectFrame(frame.index) }
            )
        }
    }
}
