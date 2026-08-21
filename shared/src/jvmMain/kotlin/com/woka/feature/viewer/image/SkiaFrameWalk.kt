package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.File

/**
 * Every frame of an animation, decoded in **one forward walk**.
 *
 * [SkiaFrameDecoder.frame] deliberately refuses to assume an access pattern: it hands
 * skia no prior frame, so each call rebuilds the whole chain up to the frame asked for.
 * That is right for the frame strip, which is walked in both directions and backed by a
 * cache — and quadratic for the one caller that wants *all* of them in order. Loading a
 * hundred-frame GIF that way is five thousand decodes.
 *
 * Here the chain is kept: the bitmap holds the previous frame, and when skia says this
 * one is built on it (`requiredFrame`) it is handed over as the prior. A whole animation
 * then costs one decode per frame. Where the chain does not line up — a frame that
 * depends on something further back, which disposal methods produce — the prior is
 * dropped and skia rebuilds from scratch, exactly as [SkiaFrameDecoder.frame] would.
 *
 * **Each frame is copied out.** `Image.makeFromBitmap` snapshots the pixels, which is
 * required rather than wasteful: the one mutable bitmap is about to become the next
 * frame, and handing it over directly would give every caller the same picture.
 *
 * Callers decide how many frames are worth holding — this walks the whole file.
 */
internal fun forwardFrames(file: File, onFrame: (Int, ImageBitmap) -> Unit) {
    onCodec(file) { codec ->
        val bitmap = Bitmap()
        bitmap.allocPixels(codec.imageInfo)
        var held = -1

        for (index in 0 until codec.frameCount) {
            val required = runCatching { codec.getFrameInfo(index).requiredFrame }.getOrDefault(-1)
            val prior = if (required >= 0 && required == held) held else -1
            codec.readPixels(bitmap, index, prior)
            held = index
            onFrame(index, Image.makeFromBitmap(bitmap).toComposeImageBitmap())
        }
    }
}
