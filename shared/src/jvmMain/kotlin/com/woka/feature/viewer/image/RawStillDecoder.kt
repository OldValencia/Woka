package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.libraw.libraw_data_t
import org.bytedeco.libraw.global.LibRaw.libraw_close
import org.bytedeco.libraw.global.LibRaw.libraw_dcraw_clear_mem
import org.bytedeco.libraw.global.LibRaw.libraw_dcraw_make_mem_image
import org.bytedeco.libraw.global.LibRaw.libraw_dcraw_process
import org.bytedeco.libraw.global.LibRaw.libraw_init
import org.bytedeco.libraw.global.LibRaw.libraw_open_buffer
import org.bytedeco.libraw.global.LibRaw.libraw_strerror
import org.bytedeco.libraw.global.LibRaw.libraw_unpack
import java.io.File
import java.nio.channels.FileChannel

/** LibRaw's `flip` bits: 4 means the picture is stored transposed (90° either way). */
private const val FLIP_TRANSPOSED = 4

/**
 * Camera RAW, through LibRaw (`org.bytedeco:libraw`).
 *
 * FFmpeg was measured against nine real files and read none of them properly —
 * CR2/DNG/PEF/ARW errored, NEF came back as a 160×120 1-bit thumbnail — while
 * LibRaw decoded all nine (photo-formats, 2026-10-24). The preset is built
 * against javacpp 1.5.11 and runs under Woka's 1.5.14 with only a version
 * warning from the loader; that pairing is what the decision log records.
 *
 * **Opened from memory, never by path.** LibRaw's C API takes a narrow `char*`
 * path, which on Windows is the ANSI code page — the exact failure 1.0.2 fixed
 * for CJK paths elsewhere. The file is read into native memory instead.
 *
 * LibRaw's defaults otherwise: camera white balance, 8-bit sRGB, the camera's
 * orientation applied by `dcraw_make_mem_image`.
 */
internal object RawStillDecoder : StillDecoder {

    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    /** From the header alone — `open_buffer` parses metadata, nothing is unpacked. */
    override fun nativeSize(file: File, frameIndex: Int): IntSize = onRaw(file) { raw ->
        val sizes = raw.sizes()
        val width = sizes.width().toInt() and 0xFFFF
        val height = sizes.height().toInt() and 0xFFFF
        if ((sizes.flip() and FLIP_TRANSPOSED) != 0) IntSize(height, width) else IntSize(width, height)
    }

    override fun frame(file: File, frameIndex: Int): ImageBitmap = develop(file, halfSize = false)

    /**
     * Half resolution, which LibRaw gets by skipping demosaicing — 120–720 ms on
     * the samples against 0.6–8 s for the full picture. The preview and the grid
     * tile ask for this; only the viewer pays for every pixel.
     */
    fun halfSize(file: File): ImageBitmap = develop(file, halfSize = true)
}

private fun develop(file: File, halfSize: Boolean): ImageBitmap = onRaw(file) { raw ->
    raw.params().use_camera_wb(1)
    raw.params().output_bps(8)
    raw.params().half_size(if (halfSize) 1 else 0)
    requireOk(libraw_unpack(raw))
    requireOk(libraw_dcraw_process(raw))
    val error = IntArray(1)
    val processed = libraw_dcraw_make_mem_image(raw, error)
        ?: error("LibRaw: ${libraw_strerror(error[0]).string}")
    try {
        val width = processed.width().toInt() and 0xFFFF
        val height = processed.height().toInt() and 0xFFFF
        check(processed.colors().toInt() == 3 && processed.bits().toInt() == 8) {
            "LibRaw produced ${processed.colors()}×${processed.bits()}-bit"
        }
        rgbToBitmap(processed.data().address(), width, height)
    } finally {
        libraw_dcraw_clear_mem(processed)
    }
}

private fun requireOk(result: Int) {
    check(result == 0) { "LibRaw: ${libraw_strerror(result).string}" }
}

/** [file] in native memory, opened by LibRaw, and both freed afterwards. */
private fun <T> onRaw(file: File, block: (libraw_data_t) -> T): T {
    val raw = libraw_init(0) ?: error("LibRaw could not initialise")
    val size = file.length()
    val buffer = BytePointer(size)
    try {
        FileChannel.open(file.toPath()).use { channel ->
            val target = buffer.asByteBuffer()
            while (target.hasRemaining() && channel.read(target) >= 0) Unit
        }
        requireOk(libraw_open_buffer(raw, buffer, size))
        return block(raw)
    } finally {
        libraw_close(raw)
        buffer.deallocate()
    }
}
