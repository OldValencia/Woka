package com.woka.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import org.jetbrains.skia.Image as SkiaImage

// A frame this far past its wall-clock slot is not worth the conversion cost.
private const val LATE_DROP_MS = 40L

// Ceiling on consecutive skips, so a burst can never freeze the picture outright.
private const val MAX_SKIPS_IN_A_ROW = 4

/**
 * Decides which decoded frames are worth turning into bitmaps, and converts them
 * through a reused scratch buffer.
 *
 * On 4K HDR the RGBA conversion measured ~27ms a frame against ~9ms for the decode
 * itself, so the pipeline caps out just above real time. At 2x the presenter then
 * found every frame late, dropped it, and let one through only when the drift
 * passed its 1s discontinuity threshold — a frame every two to three seconds.
 *
 * Skipping the conversion for frames already past their slot keeps the media clock
 * (and with it the audio) running at the requested speed and pays for it in
 * displayed frame rate instead, which is what every player does. Reusing the
 * scratch array removes a 33MB allocation per 4K frame on top of that.
 */
internal class FramePacer {
    private var anchorWallMs = -1L
    private var anchorTsMs = -1L
    private var skipsInARow = 0
    private var pacedSpeed = 0f
    private var scratch = ByteArray(0)

    /** Drops the baseline: the next frame anchors a fresh slot schedule. */
    fun rebase() {
        anchorTsMs = -1L
        skipsInARow = 0
    }

    /**
     * True when the frame at [frameTsMs] is already past its slot under [speed]
     * and its conversion can be skipped. A speed change rebases on its own — the
     * old baseline was anchored under a schedule that no longer applies.
     */
    fun shouldSkip(frameTsMs: Long, speed: Float): Boolean {
        val nowMs = System.nanoTime() / 1_000_000L
        if (speed != pacedSpeed) {
            pacedSpeed = speed
            rebase()
        }
        if (anchorTsMs < 0L) {
            anchorTsMs = frameTsMs
            anchorWallMs = nowMs
            return false
        }
        val lateMs = nowMs - (anchorWallMs + ((frameTsMs - anchorTsMs) / speed).toLong())
        if (lateMs <= LATE_DROP_MS) {
            skipsInARow = 0
            return false
        }
        if (skipsInARow < MAX_SKIPS_IN_A_ROW) {
            skipsInARow++
            return true
        }
        // Converting a late frame regardless: re-anchor, or every frame from here
        // on stays measured against a slot the machine can no longer reach.
        anchorTsMs = frameTsMs
        anchorWallMs = nowMs
        skipsInARow = 0
        return false
    }

    /**
     * RGBA frame to a Skia-backed bitmap. Skia copies the array into its own
     * storage, so handing it the same scratch buffer every time is safe.
     */
    fun convert(frame: Frame): ImageBitmap {
        val size = frame.imageStride * frame.imageHeight
        if (scratch.size != size) scratch = ByteArray(size)
        val buffer = frame.image[0] as ByteBuffer
        buffer.rewind()
        buffer.get(scratch, 0, size)
        buffer.rewind()
        val info = ImageInfo(
            frame.imageWidth,
            frame.imageHeight,
            ColorType.RGBA_8888,
            ColorAlphaType.UNPREMUL
        )
        return SkiaImage.makeRaster(info, scratch, frame.imageStride).toComposeImageBitmap()
    }
}
