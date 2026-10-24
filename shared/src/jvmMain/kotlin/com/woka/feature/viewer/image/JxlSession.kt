package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import java.awt.image.BufferedImage
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG

/** EXIF-style orientations 5–8 swap the axes. */
private const val FIRST_TRANSPOSING_ORIENTATION = 5

/**
 * One decoder over one file's bytes, with everything it allocated freed by the
 * caller's [Arena] and [decode]'s `finally`.
 */
internal class JxlSession(val arena: Arena, val decoder: MemorySegment) {

    private val basic: MemorySegment = arena.allocate(BASIC_INFO_SIZE)
    private val gray: Boolean get() = basicInt(BASIC_COLOR_CHANNELS) == 1

    /** The next event; an error or a stall on input that is all already given is a broken file. */
    fun next(): Int {
        val status = JxlNative.processInput.invokeWithArguments(decoder) as Int
        check(status != JXL_DEC_ERROR) { "libjxl could not decode this file" }
        check(status != JXL_DEC_NEED_MORE_INPUT) { "the JPEG XL file is truncated" }
        if (status == JXL_DEC_BASIC_INFO) {
            check(JxlNative.getBasicInfo.invokeWithArguments(decoder, basic) as Int == JXL_DEC_SUCCESS)
        }
        return status
    }

    fun basicInt(offset: Long): Int = basic.get(JAVA_INT, offset)

    fun basicFlag(offset: Long): Boolean = basicInt(offset) != 0

    /** libjxl applies the orientation itself, so the output is the oriented size. */
    fun orientedSize(): IntSize {
        val width = basicInt(BASIC_XSIZE)
        val height = basicInt(BASIC_YSIZE)
        val transposed = basicInt(BASIC_ORIENTATION) >= FIRST_TRANSPOSING_ORIENTATION
        return if (transposed) IntSize(height, width) else IntSize(width, height)
    }

    fun frameDurationTicks(): Long {
        val header = arena.allocate(FRAME_HEADER_SIZE)
        check(JxlNative.getFrameHeader.invokeWithArguments(decoder, header) as Int == JXL_DEC_SUCCESS)
        return header.get(JAVA_INT, 0).toLong() and 0xFFFFFFFFL
    }

    /** Must follow `JXL_DEC_COLOR_ENCODING` and precede every other event. */
    fun requestSrgb() {
        val cms = (JxlNative.defaultCms.invokeWithArguments() as MemorySegment)
        check(JxlNative.setCms.invokeWithArguments(decoder, cms) as Int == JXL_DEC_SUCCESS)
        val encoding = arena.allocate(COLOR_ENCODING_SIZE)
        JxlNative.setToSrgb.invokeWithArguments(encoding, if (gray) 1 else 0)
        val status = JxlNative.setOutputColorProfile.invokeWithArguments(decoder, encoding, MemorySegment.NULL, 0L)
        check(status as Int == JXL_DEC_SUCCESS) { "libjxl refused sRGB output" }
    }

    /** Gray pictures come out as gray + alpha and are widened here; colour as RGBA. */
    fun drawInto(): ImageBitmap {
        val channels = if (gray) 2 else 4
        val format = arena.allocate(PIXEL_FORMAT_SIZE)
        format.set(JAVA_INT, 0, channels)
        format.set(JAVA_INT, 4, JXL_TYPE_UINT8)
        check(next() == JXL_DEC_NEED_IMAGE_OUT_BUFFER) { "libjxl asked for no image buffer" }
        val sizeOut = arena.allocate(JAVA_LONG)
        check(JxlNative.imageOutBufferSize.invokeWithArguments(decoder, format, sizeOut) as Int == JXL_DEC_SUCCESS)
        val size = sizeOut.get(JAVA_LONG, 0)
        val pixels = arena.allocate(size)
        check(JxlNative.setImageOutBuffer.invokeWithArguments(decoder, format, pixels, size) as Int == JXL_DEC_SUCCESS)
        check(next() == JXL_DEC_FULL_IMAGE) { "libjxl stopped before the frame was complete" }
        val (width, height) = orientedSize()
        val argb = IntArray(width * height) { at ->
            val base = at.toLong() * channels
            fun byte(offset: Int): Int = pixels.get(JAVA_BYTE, base + offset).toInt() and 0xFF
            if (gray) {
                (byte(1) shl 24) or (byte(0) shl 16) or (byte(0) shl 8) or byte(0)
            } else {
                (byte(3) shl 24) or (byte(0) shl 16) or (byte(1) shl 8) or byte(2)
            }
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, argb, 0, width)
        return image.toStillBitmap()
    }
}

/** Opens a decoder with a thread pool over [file]'s bytes, subscribed to [events]. */
internal fun <T> decodeJxl(file: File, events: Int, block: (JxlSession) -> T): T = Arena.ofConfined().use { arena ->
    val bytes = file.readBytes()
    val input = arena.allocate(bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes))
    val decoder = JxlNative.decoderCreate.invokeWithArguments(MemorySegment.NULL) as MemorySegment
    val threads = JxlNative.runnerDefaultThreads.invokeWithArguments() as Long
    val runner = JxlNative.runnerCreate.invokeWithArguments(MemorySegment.NULL, threads) as MemorySegment
    try {
        check(JxlNative.setParallelRunner.invokeWithArguments(decoder, JxlNative.runner, runner) as Int == 0)
        check(JxlNative.subscribeEvents.invokeWithArguments(decoder, events) as Int == JXL_DEC_SUCCESS)
        check(JxlNative.setInput.invokeWithArguments(decoder, input, bytes.size.toLong()) as Int == 0)
        JxlNative.closeInput.invokeWithArguments(decoder)
        block(JxlSession(arena, decoder))
    } finally {
        JxlNative.decoderDestroy.invokeWithArguments(decoder)
        JxlNative.runnerDestroy.invokeWithArguments(runner)
    }
}
