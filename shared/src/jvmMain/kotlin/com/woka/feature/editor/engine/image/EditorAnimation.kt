package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrame
import com.woka.feature.viewer.image.forwardFrames
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How much memory the canvas will hold in frames before it declines to play at all.
 *
 * A proxy frame is capped at [PROXY_MAX_PX], so an ordinary GIF is a few hundred
 * kilobytes and a hundred of them still fit here. A 1920×1080 animation is 8 MB a
 * frame, and a long one of those is what this exists to refuse: the alternative is
 * the editor quietly allocating a gigabyte to animate a picture being rotated.
 */
private const val BUDGET_BYTES = 96L * 1024 * 1024

/**
 * An animation, decoded once and held for the editing session.
 *
 * **Held, rather than decoded when due.** The viewer can decode a frame at a time
 * because `ImageBitmapCache` catches the result; `EditorProxy` is deliberately outside
 * that cache (a 100 MP photograph is 384 MB and would evict everything), so a canvas
 * that decoded per frame would re-read the file ten times a second forever.
 *
 * Null from [editorAnimationOf] means "draw the still frame", which is what the canvas
 * did before it could play at all — a photograph, a burst, a page chain, a format skia
 * cannot walk, and an animation too large for [BUDGET_BYTES].
 */
internal class EditorAnimation(
    val frames: List<ImageFrame>,
    val repeatCount: Int?,
    private val bitmaps: Map<Int, ImageBitmap>
) {
    /** Null for a frame the walk did not reach, which falls back to the still. */
    fun bitmapAt(index: Int): ImageBitmap? = bitmaps[index]
}

/**
 * Reads [file]'s frames if it is an animation worth playing, on the caller's thread —
 * which must not be the UI one, because this decodes every frame of it.
 *
 * **Never throws.** A file that turns out not to be readable as a frame walk answers
 * null and the canvas draws its one frame, exactly as it did before.
 */
internal fun editorAnimationOf(file: File): EditorAnimation? {
    val decoder = stillDecoderFor(file)
    val set = runCatching { decoder.frameSet(file) }.getOrNull() ?: return null
    if (!set.isAnimated) return null

    val native = runCatching { decoder.nativeSize(file, 0) }.getOrNull() ?: return null
    if (proxyBytes(native, set.frames.size) > BUDGET_BYTES) return null

    val bitmaps = mutableMapOf<Int, ImageBitmap>()
    // One forward walk rather than one decode per frame: skia builds each frame on the
    // one before it, so asking for them independently is quadratic (`forwardFrames`).
    runCatching {
        forwardFrames(file) { index, bitmap -> bitmaps[index] = bitmap.cappedToProxy() }
    }.getOrElse { return null }

    if (bitmaps.isEmpty()) return null
    return EditorAnimation(set.frames, set.repeatCount, bitmaps)
}

/** What the whole animation will occupy once every frame is capped to the proxy size. */
private fun proxyBytes(native: IntSize, frames: Int): Long {
    val longest = max(native.width, native.height)
    val scale = if (longest > PROXY_MAX_PX) PROXY_MAX_PX.toDouble() / longest else 1.0
    val width = max(1, (native.width * scale).roundToInt()).toLong()
    val height = max(1, (native.height * scale).roundToInt()).toLong()
    return width * height * 4 * frames
}
