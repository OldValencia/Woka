package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.stillDecoderFor
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Largest source the canvas will ever hold at native resolution — 40 MP, ~160 MB of
 * N32 pixels at `w × h × 4`.
 *
 * The refusal is the ticket's, and it is permanent: the 100 MP case ME-010 measured is
 * **384 MB** for one picture, against a 256 MB `ImageBitmapCache` budget it is
 * deliberately outside. Refusing is invisible — the picture stays exactly as sharp as
 * it was before this file existed.
 */
internal const val NATIVE_HOLD_MAX_PX = 40_000_000L

/**
 * And a second refusal on the longest edge, because area alone lets a panorama
 * through: 12000 × 900 is 10.8 MP and would be admitted on bytes, while Skia is being
 * asked to sample a 12000 px row into a canvas a thousand pixels wide.
 */
internal const val NATIVE_MAX_EDGE = 8192

/** Whether a source of this size may be held at native resolution at all. */
internal fun canHoldNative(sourceSize: IntSize): Boolean {
    if (sourceSize.width <= 0 || sourceSize.height <= 0) return false
    if (sourceSize.width.toLong() * sourceSize.height > NATIVE_HOLD_MAX_PX) return false
    return max(sourceSize.width, sourceSize.height) <= NATIVE_MAX_EDGE
}

/**
 * The native bitmap, once the canvas is zoomed far enough to want it — null until
 * then, and null forever for a source past either budget.
 *
 * **No region decoder exists and none is needed.** `StillDecoder` answers a whole
 * picture, never a rectangle of one, and Skia's `Codec` cannot subset. It does not
 * matter: `RasterSurface` draws with `dstSize = sourceSize` whatever it is handed, and
 * the canvas is `clipToBounds`, so Skia rasterises only the visible destination pixels
 * however large the source is. The work is deciding *when* to hold the bitmap.
 *
 * **Decoded once and kept for as long as the composition lives.** [wanted] gates the
 * decode and the answer, but never the hold: zooming back to Fit returns null so the
 * canvas falls to the cheaper proxy, and zooming in again re-uses what is already in
 * memory rather than re-reading the file. Dropped with the composition, and it never
 * enters `ImageBitmapCache` — that is `EditorProxy`'s rule and the guard rail §10 asks
 * for, because one of these would evict every thumbnail the app holds.
 *
 * **Never throws.** A decode that fails answers null and the canvas goes on drawing
 * the proxy, which is the picture the user already had.
 */
@Composable
internal fun rememberNativeFrame(
    file: File,
    frameIndex: Int,
    sourceSize: IntSize,
    wanted: Boolean
): ImageBitmap? {
    val held = remember(file, frameIndex) { mutableStateOf<ImageBitmap?>(null) }
    val allowed = remember(sourceSize) { canHoldNative(sourceSize) }

    LaunchedEffect(file, frameIndex, wanted, allowed) {
        if (!wanted || !allowed || held.value != null) return@LaunchedEffect
        held.value = withContext(ImageBitmapCache.ioDispatcher) {
            runCatching { stillDecoderFor(file).frame(file, frameIndex) }.getOrNull()
        }
    }

    return if (wanted) held.value else null
}
