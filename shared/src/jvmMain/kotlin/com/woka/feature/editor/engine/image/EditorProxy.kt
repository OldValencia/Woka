package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.feature.viewer.image.FfmpegStillDecoder
import com.woka.feature.viewer.image.stillDecoderFor
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge the canvas ever draws. Above this, precision is ME-116's problem. */
internal const val PROXY_MAX_PX = 2560

/**
 * The copy the editing canvas draws — the source, capped at [PROXY_MAX_PX].
 *
 * **Deliberately not cached anywhere.** Measured in ME-010: a 100 MP source is 384 MB
 * of native pixels against a 256 MB `ImageBitmapCache` budget, so putting one in the
 * shared cache evicts every thumbnail and poster the app holds and *still* does not
 * fit. Building this costs 6–8 ms, which is far cheaper than the eviction storm, and
 * keeping it out is the whole of the guard rail §10 asked for. The editor holds it in
 * a `remember` for as long as the session is open and drops it with the composition.
 *
 * **This is not `StillPreview` with a bigger constant.** That object writes both a
 * disk thumbnail and an `ImageBitmapCache` entry under `previewKey`, and the editor
 * must do neither. What is worth taking from it is the technique below.
 *
 * Native resolution is decoded exactly once, at save, by ME-104 — never here.
 *
 * **It does not apply the file's recipe, and that is not an omission.** Every other
 * reader in the app folds the edit in on the way out (`AppliedStillEdits`, ME-133);
 * this is the one place that must read the file raw, because the editor folds the
 * document live over what it gets. Applying here would apply the recipe twice — once
 * into the proxy and once over it — so reopening an edited photograph would draw two
 * rotations for one.
 */
internal object EditorProxy {

    fun load(file: File, frameIndex: Int = 0): ImageBitmap {
        val decoder = stillDecoderFor(file)
        // A tile grid assembles *at* the target size rather than assembling whole and
        // shrinking — the difference between allocating half a megabyte and 138 MB.
        // Only FFmpeg's decoder can do that; the rest hand over the whole picture and
        // are scaled below.
        if (decoder is FfmpegStillDecoder) {
            return decoder.frame(file, frameIndex, PROXY_MAX_PX)
        }
        return decoder.frame(file, frameIndex).cappedToProxy()
    }
}

/**
 * Scaled on the Skia path rather than through AWT: `toAwtImage` on a 36-megapixel
 * bitmap copies 138 MB onto the JVM heap, which is a real risk of exhausting it for
 * something about to become 2560 px wide.
 *
 * A source already within the cap is returned **as it is** — round-tripping it through
 * a surface would copy every pixel to produce an identical bitmap.
 */
internal fun ImageBitmap.cappedToProxy(): ImageBitmap {
    val scale = PROXY_MAX_PX.toDouble() / max(width, height)
    if (scale >= 1.0) return this

    val targetWidth = max(1, (width * scale).roundToInt())
    val targetHeight = max(1, (height * scale).roundToInt())
    val source = Image.makeFromBitmap(asSkiaBitmap())
    return Surface.makeRasterN32Premul(targetWidth, targetHeight).use { surface ->
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat())
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
}
