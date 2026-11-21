package com.woka.feature.viewer.video.engine.decode

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer

// A frame this far past its wall-clock slot is not worth the conversion cost.
private const val LATE_DROP_MS = 40L

// Ceiling on consecutive skips, so a burst can never freeze the picture outright.
private const val MAX_SKIPS_IN_A_ROW = 4

/**
 * Decides which decoded frames are worth turning into bitmaps, and converts them.
 *
 * On 4K HDR the RGBA conversion measured ~27ms a frame against ~9ms for the decode
 * itself, so the pipeline caps out just above real time. At 2x the presenter then
 * found every frame late, dropped it, and let one through only when the drift
 * passed its 1s discontinuity threshold — a frame every two to three seconds.
 *
 * Skipping the conversion for frames already past their slot keeps the media clock
 * (and with it the audio) running at the requested speed and pays for it in
 * displayed frame rate instead, which is what every player does.
 */
/** A converted frame and the id [FrameRetention] tracks its lifetime by. */
internal class PacedFrame(val bitmap: ImageBitmap, val id: Long)

internal class FramePacer(private val retention: FrameRetention) {
    private var anchorWallMs = -1L
    private var anchorTsMs = -1L
    private var skipsInARow = 0
    private var pacedSpeed = 0f
    private var nextFrameId = 1L

    /** The newest [convert] result, re-sent while nothing draws (`deliverPicture`). */
    var lastConverted: PacedFrame? = null
        private set

    /**
     * Two late frames in a row: the machine is not keeping up *right now*. One late
     * frame is ordinary jitter, which is why this is not `skipsInARow > 0` — a
     * background job gated on that would flicker on and off constantly.
     */
    val isFallingBehind: Boolean get() = skipsInARow >= 2

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
     * RGBA frame to a Skia-backed bitmap, in **one** native buffer and one copy.
     *
     * It used to be `Image.makeRaster(…).toComposeImageBitmap()`, which is two
     * full-size native allocations rather than one: `makeRaster` copies the array,
     * and `toComposeImageBitmap` then allocates a second bitmap and blits the whole
     * frame into it through a canvas, converting UNPREMUL RGBA to PREMUL BGRA on
     * the way. Neither was ever closed. `installPixels` writes the pixels the
     * decoder already produced straight into the bitmap that gets drawn.
     *
     * The bitmap is handed to [FrameRetention], which closes it once the UI has
     * drawn past it, and the decoder stops minting new ones while nothing draws
     * (`FrameRetention.isBacklogged`). That is what bounds memory.
     *
     * **No Java staging array any more** (ST-001, 2026-11-19). One used to be
     * allocated per frame on purpose, so heap pressure tracked the native frames
     * while the collector was what freed them. Since retention closes them itself,
     * all it did was churn: 4K playback held ~420 MB of committed heap for it. The
     * pixels now go from the decoder's buffer straight into the bitmap's own.
     */
    fun convert(frame: Frame): PacedFrame {
        val size = frame.imageStride.toLong() * frame.imageHeight
        // OPAQUE, not UNPREMUL: swscale writes 255 into every alpha byte, and
        // saying so spares Skia an un-premultiply pass when it uploads the texture.
        val info = ImageInfo(
            frame.imageWidth,
            frame.imageHeight,
            ColorType.RGBA_8888,
            ColorAlphaType.OPAQUE
        )
        val bitmap = Bitmap()
        check(bitmap.allocPixels(info, frame.imageStride)) {
            "allocPixels rejected ${frame.imageWidth}x${frame.imageHeight} stride ${frame.imageStride}"
        }
        val pixels = checkNotNull(bitmap.peekPixels()) { "no pixels behind a freshly allocated bitmap" }
        try {
            // Skia sizes the block as stride × (height − 1) + width × 4: a padded last row
            // has no tail. Copying stride × height wrote past it — a heap corruption, and an
            // access violation when the block ended on a page (caught by the suite).
            val bytes = pixels.computeByteSize().toLong()
            check(bytes <= size) { "bitmap of $bytes bytes for a $size-byte frame" }
            val source = MemorySegment.ofBuffer((frame.image[0] as ByteBuffer).duplicate().rewind())
            MemorySegment.ofAddress(pixels.addr).reinterpret(bytes).copyFrom(source.asSlice(0, bytes))
        } finally {
            pixels.close()
        }
        bitmap.notifyPixelsChanged()
        // Skia only lets an Image share a bitmap's storage when the bitmap can no
        // longer change; without this every draw would copy the frame instead.
        bitmap.setImmutable()
        val id = nextFrameId++
        retention.register(id, bitmap)
        return PacedFrame(bitmap.asComposeImageBitmap(), id).also { lastConverted = it }
    }
}
