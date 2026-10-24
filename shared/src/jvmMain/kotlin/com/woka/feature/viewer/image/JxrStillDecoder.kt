package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import com.woka.core.system.BundledNatives
import java.awt.image.BufferedImage
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

private const val RGBA = 4

/** `woka_jxr.h` return codes, for the message — `natives/jxr/README.md`. */
private val JXR_ERRORS = mapOf(
    1 to "bad arguments",
    2 to "not JPEG XR, or corrupt",
    3 to "unsupported pixel format",
    4 to "out of memory",
    5 to "larger than 2^28 pixels"
)

/**
 * JPEG XR / HD Photo (`.jxr`, `.wdp`) — Xbox and Game Bar HDR screenshots
 * among them — through `woka_jxr`, Microsoft's jxrlib behind a three-function C
 * shim built from pinned sources (`natives/jxr/`) and bundled with the app.
 *
 * No Java, FFmpeg or macOS decoder exists for the format. The shim hands back
 * sRGB RGBA8 whatever the file holds: HDR (half/float, scRGB-linear) is clipped
 * at 1.0 and sRGB-encoded with no tone mapping, matching Windows' own decoder
 * within one level on every sample the shim was built against.
 */
internal object JxrStillDecoder : StillDecoder {

    private val linker = Linker.nativeLinker()
    private val lookup by lazy { BundledNatives.load("woka_jxr") }

    private fun bind(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.find(name).orElseThrow { error("woka_jxr has no $name") }, descriptor)

    private val info by lazy {
        bind("woka_jxr_info", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS))
    }
    private val decode by lazy {
        bind("woka_jxr_decode", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS))
    }
    private val free by lazy { bind("woka_jxr_free", FunctionDescriptor.ofVoid(ADDRESS)) }

    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    override fun nativeSize(file: File, frameIndex: Int): IntSize = Arena.ofConfined().use { arena ->
        val (input, size) = input(arena, file)
        val width = arena.allocate(JAVA_INT)
        val height = arena.allocate(JAVA_INT)
        requireOk(info.invokeWithArguments(input, size, width, height) as Int)
        IntSize(width.get(JAVA_INT, 0), height.get(JAVA_INT, 0))
    }

    override fun frame(file: File, frameIndex: Int): ImageBitmap = Arena.ofConfined().use { arena ->
        val (input, size) = input(arena, file)
        val widthOut = arena.allocate(JAVA_INT)
        val heightOut = arena.allocate(JAVA_INT)
        val pixelsOut = arena.allocate(ADDRESS)
        requireOk(decode.invokeWithArguments(input, size, widthOut, heightOut, pixelsOut) as Int)
        val width = widthOut.get(JAVA_INT, 0)
        val height = heightOut.get(JAVA_INT, 0)
        val native = pixelsOut.get(ADDRESS, 0)
        try {
            val pixels = native.reinterpret(width.toLong() * height * RGBA)
            val argb = IntArray(width * height) { at ->
                val base = at.toLong() * RGBA
                fun byte(offset: Int): Int = pixels.get(JAVA_BYTE, base + offset).toInt() and 0xFF
                (byte(3) shl 24) or (byte(0) shl 16) or (byte(1) shl 8) or byte(2)
            }
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, width, height, argb, 0, width)
            image.toStillBitmap()
        } finally {
            free.invokeWithArguments(native)
        }
    }

    /** The shim decodes from memory: the file's bytes, copied off-heap. */
    private fun input(arena: Arena, file: File): Pair<MemorySegment, Long> {
        val bytes = file.readBytes()
        val segment = arena.allocate(bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes))
        return segment to bytes.size.toLong()
    }

    private fun requireOk(code: Int) {
        check(code == 0) { "woka_jxr: ${JXR_ERRORS[code] ?: "error $code"}" }
    }
}
