package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 *
 * **The one guard for a proxy that has not arrived** (ME-168), which is why the engine's
 * `Surface` and `Thumbnail` carry none: both are this call, so guarding here guards both
 * and there is no second place to keep in step.
 */
@Composable
internal fun ImageCanvas(
    file: File,
    frameIndex: Int,
    playback: EditorPlayback,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier,
    /**
     * Handed the proxy the moment it exists, so the engine can measure a histogram off a
     * bitmap that is already decoded (ME-166). The alternative was a lazy `stats` that
     * decoded its own copy on the first click, which is the freeze ME-168 removed,
     * reintroduced on a different button.
     *
     * **Suspending, and called once for the first bitmap only.** What arrives here is
     * whatever the surface drew — the proxy at Fit, the native frame once ME-116 swaps it
     * in — so the receiver keeps the first and ignores the rest. The first is always the
     * proxy, because the native one is not asked for until the canvas is zoomed past it.
     */
    onProxy: suspend (ImageBitmap) -> Unit = {}
) {
    val source = rememberCanvasSource(file, frameIndex, playback, sourceSize) ?: return
    LaunchedEffect(source) { onProxy(source) }
    RasterSurface(
        source = source,
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
 * Null until the proxy has been decoded (ME-168), and the native frame is not asked for
 * before then: the two decodes would otherwise race for the same file, and the picture
 * the zoom is meant to sharpen is the one that has not arrived yet.
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
): ImageBitmap? {
    val frame = rememberEditorFrame(file, frameIndex, playback)
    val proxy = frame.bitmap
    val proxyScale = if (proxy != null && sourceSize.width > 0) {
        proxy.width.toFloat() / sourceSize.width
    } else {
        1f
    }
    // Never under an animation: those are held frame by frame at proxy size, and one
    // native still swapped in would be a different picture rather than a sharper one.
    val wanted = proxy != null &&
        !frame.isAnimating &&
        LocalCanvasScale.current > proxyScale * SHARPEN_MARGIN
    return rememberNativeFrame(file, frameIndex, sourceSize, wanted) ?: proxy
}
