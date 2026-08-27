package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.LocalCanvasScale
import java.io.File

/**
 * How much sharper the canvas must be than the proxy before the native bitmap is worth
 * asking for.
 *
 * Not 1.0: a canvas sitting exactly at the proxy's own resolution would otherwise flip
 * between the two bitmaps on a one-pixel window resize, and each flip is a visible
 * change of sampling on a picture nobody touched.
 */
private const val SHARPEN_MARGIN = 1.05f

/**
 * The one place the image engine turns a recipe into pixels on screen.
 *
 * Both of its drawing surfaces — the canvas and a preview-choice thumbnail (ME-112) —
 * are the same call with a different recipe and a different box, and they **must** share
 * `rememberEditorFrame`: it holds the session's proxy, and a second loader would mean a
 * decode per thumbnail per recomposition.
 *
 * A separate file because `ImageEditorEngine` is at the size mandate, and because the
 * sharing is the point worth stating once rather than twice.
 */
@Composable
internal fun ImageCanvas(
    file: File,
    frameIndex: Int,
    playback: EditorPlayback,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier
) {
    RasterSurface(
        source = rememberCanvasSource(file, frameIndex, playback, sourceSize),
        sourceSize = sourceSize,
        recipe = recipe,
        modifier = modifier
    )
}

/**
 * Which bitmap this surface draws from: the ≤2560 px proxy, or the native one while
 * the canvas is magnified past what the proxy can answer (ME-116).
 *
 * **The comparison is between two scales, not against 100 %.** A 6000 px photograph
 * has a proxy at 0.43 of source, so it is soft from 43 % onward; a 1600 px one has no
 * proxy at all and never needs this. `proxy.width / sourceSize.width` is that number,
 * read off the bitmap in hand rather than recomputed from [PROXY_MAX_PX], so a decoder
 * that returned something other than the cap cannot make the two disagree.
 *
 * `LocalCanvasScale` defaults to `0f`, so every surface that is not the editing canvas
 * — the filter strip's thumbnails above all — fails this test by arithmetic and is
 * never named here.
 *
 * Split out of [ImageCanvas] so a test can assert on the bitmap `RasterSurface` was
 * handed, which is not observable once it has been drawn into a `Canvas`.
 */
@Composable
internal fun rememberCanvasSource(
    file: File,
    frameIndex: Int,
    playback: EditorPlayback,
    sourceSize: IntSize
): ImageBitmap {
    val frame = rememberEditorFrame(file, frameIndex, playback)
    val proxyScale = if (sourceSize.width > 0) {
        frame.bitmap.width.toFloat() / sourceSize.width
    } else {
        1f
    }
    // Never under an animation: those are held frame by frame at proxy size, and one
    // native still swapped in would be a different picture rather than a sharper one.
    val wanted = !frame.isAnimating && LocalCanvasScale.current > proxyScale * SHARPEN_MARGIN
    return rememberNativeFrame(file, frameIndex, sourceSize, wanted) ?: frame.bitmap
}
