package com.woka.feature.viewer.shell.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import com.woka.core.data.isAnimation
import com.woka.core.data.MediaItem
import com.woka.core.data.mediaKind
import com.woka.core.data.MediaKind
import com.woka.feature.viewer.shell.MediaThumbnails
import java.io.File

/**
 * What the strip shows for one file: the decoder's frames, plus a Live Photo's
 * motion clip if one is sitting beside it.
 *
 * [motion] is a **sibling file**, not a track. `livephoto.heic` reports exactly
 * one picture and the movement lives in `livephoto.mov` — measured, and the
 * reason this is a directory lookup rather than a container one.
 */
data class FrameStripModel(
    val frames: List<ImageFrame> = emptyList(),
    val motion: File? = null,
    /** Skia's own numbers: `-1` forever, `0` once, `n` that many more passes. */
    val repeatCount: Int? = null
) {
    /** Nothing to show: one picture and no clip beside it. */
    val isSingle: Boolean get() = frames.size <= 1

    /**
     * Whether this file plays ([isAnimation]). A burst and a page chain have frames
     * and no durations, so they keep the strip and never get a clock.
     *
     * A Live Photo's appended motion entry carries no duration either, which is
     * right: the movement is a *different file*, played by the video engine.
     */
    val isAnimated: Boolean get() = frames.isAnimation

    /**
     * True when [frameIndex] is the motion clip rather than a still frame.
     * Asked by the screen, which has to play a *different file* for it.
     */
    fun isMotionFrame(frameIndex: Int): Boolean = motion != null &&
        frames.firstOrNull { it.index == frameIndex }?.kind == FrameKind.Motion

    /**
     * The frame an editor may open on — [frameIndex] when it addresses a real picture
     * inside this file, and the first frame otherwise.
     *
     * A Live Photo's motion entry is indexed one past the last real frame and belongs
     * to a *different file*, so handing it to a decoder is asking for a frame that is
     * not there. Answered here because the strip is what knows the difference.
     */
    fun editableFrameOf(frameIndex: Int): Int {
        val frame = frames.firstOrNull { it.index == frameIndex } ?: return 0
        return if (frame.kind == FrameKind.Motion) 0 else frame.index
    }
}

/**
 * Loads the frame set for [item] and looks for its motion sibling.
 *
 * Both halves are off the composition thread — `MediaThumbnails.frameSet` counts
 * without decoding, but on a 1296-stream HEIC even opening the container is
 * milliseconds, and the directory listing is a disk hit.
 */
@Composable
internal fun rememberFrameStrip(item: MediaItem?): FrameStripModel {
    var model by remember(item) { mutableStateOf(FrameStripModel()) }
    LaunchedEffect(item) {
        if (item == null || item is MediaItem.Video) return@LaunchedEffect
        val set = MediaThumbnails.frameSet(item)
        val motion = motionSiblingOf(item.file)
        model = FrameStripModel(framesOf(set, motion != null), motion, set.repeatCount)
    }
    return model
}

/**
 * The viewable frames, with a [FrameKind.Motion] entry appended when a clip was
 * found. `ImageFrameSet.viewable` already drops depth maps and auxiliary items,
 * so a portrait shot does not offer its grey matte as a picture.
 *
 * The motion entry's index is one past the last real frame — the strip only ever
 * hands it back to [FrameStripModel.motion], so it addresses nothing in the
 * decoder and never reaches one.
 */
private fun framesOf(set: ImageFrameSet, hasMotion: Boolean): List<ImageFrame> {
    val viewable = set.viewable
    if (!hasMotion) return viewable
    return viewable + ImageFrame(viewable.size, FrameKind.Motion)
}

/**
 * A Live Photo is a pair on disk: Apple exports `IMG_1234.HEIC` beside
 * `IMG_1234.MOV`. Matched on the exact stem rather than the prefix scan the
 * subtitles do — a sidecar named `photo.ru.srt` belongs to `photo.mkv`, but
 * `photo.2.mov` is a different clip, not this photograph's movement.
 */
private fun motionSiblingOf(still: File): File? {
    val folder = still.parentFile ?: return null
    val stem = still.nameWithoutExtension
    return folder.listFiles()
        ?.firstOrNull {
            it.isFile &&
                it.nameWithoutExtension.equals(stem, ignoreCase = true) &&
                it.mediaKind() == MediaKind.Video
        }
}
