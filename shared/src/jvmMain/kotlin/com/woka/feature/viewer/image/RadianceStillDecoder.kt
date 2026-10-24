package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/** The only orientation in practice: rows top-down, pixels left to right. */
private val STANDARD_RESOLUTION = Regex("""-Y (\d+) \+X (\d+)""")

/** A new-style RLE scanline opens with `2 2` and its width; widths outside this range are flat. */
private const val MIN_RLE_WIDTH = 8
private const val MAX_RLE_WIDTH = 0x7FFF

/** The parsed header: size, and where the pixels start. */
private class RadianceHeader(val width: Int, val height: Int, val dataStart: Int)

/**
 * Radiance RGBE (`.hdr`) — read here rather than through either library on hand.
 *
 * FFmpeg decodes it but hands back **linear** light, which an 8-bit path shows
 * dark (mid-grey 128 came back 55); TwelveMonkeys' reader refuses anything that
 * compresses past 16:1 and lifts mid-tones with its tone mapper (photo-formats
 * PF-002's probe). The format is a text header and run-length scanlines, so the
 * honest decode is short: RGBE → linear float → the sRGB transfer, clipped at
 * 1.0. The tests hold it to the sRGB picture a file was written from —
 * ImageMagick's own read stays linear, so it is no reference.
 *
 * No tone mapping: a value past 1.0 is brighter than the display can show, and
 * choosing how to compress it would be a guess about the photographer's intent.
 */
internal object RadianceStillDecoder : StillDecoder {

    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    override fun nativeSize(file: File, frameIndex: Int): IntSize {
        val header = readHeader(file.inputStream().use { it.readNBytes(HEADER_PEEK) })
        return IntSize(header.width, header.height)
    }

    override fun frame(file: File, frameIndex: Int): ImageBitmap {
        val bytes = file.readBytes()
        val header = readHeader(bytes)
        val image = BufferedImage(header.width, header.height, BufferedImage.TYPE_INT_RGB)
        val scanline = ByteArray(header.width * 4)
        var at = header.dataStart
        for (y in 0 until header.height) {
            at = readScanline(bytes, at, scanline, header.width)
            for (x in 0 until header.width) {
                image.setRGB(x, y, rgbeToSrgb(scanline, x * 4))
            }
        }
        return image.toStillBitmap()
    }
}

/** The header is a few hundred bytes; 64 KB covers any sane one. */
private const val HEADER_PEEK = 64 * 1024

private fun readHeader(bytes: ByteArray): RadianceHeader {
    var at = 0
    var format: String? = null
    while (true) {
        val end = bytes.indexOf('\n'.code.toByte(), at)
        check(end >= 0) { "no Radiance header end" }
        val line = String(bytes, at, end - at, Charsets.ISO_8859_1).trim()
        at = end + 1
        if (line.isEmpty()) break
        if (line.startsWith("FORMAT=")) format = line.removePrefix("FORMAT=")
    }
    check(format == null || format == "32-bit_rle_rgbe") { "Radiance format $format" }
    val end = bytes.indexOf('\n'.code.toByte(), at)
    val resolution = String(bytes, at, end - at, Charsets.ISO_8859_1).trim()
    val match = STANDARD_RESOLUTION.matchEntire(resolution) ?: error("Radiance orientation '$resolution'")
    return RadianceHeader(
        width = match.groupValues[2].toInt(),
        height = match.groupValues[1].toInt(),
        dataStart = end + 1
    )
}

private fun ByteArray.indexOf(value: Byte, from: Int): Int =
    (from until size).firstOrNull { this[it] == value } ?: -1

/**
 * One scanline into [into] as R,G,B,E per pixel. New-style RLE stores each of the
 * four channels as its own run-length stream; anything else is a flat line. The
 * 1991 old-style RLE (`1 1 1` repeats) is refused rather than half-read.
 */
private fun readScanline(bytes: ByteArray, from: Int, into: ByteArray, width: Int): Int {
    val isRle = width in MIN_RLE_WIDTH..MAX_RLE_WIDTH &&
        bytes[from].toInt() == 2 && bytes[from + 1].toInt() == 2 &&
        ((bytes[from + 2].toInt() and 0xFF) shl 8 or (bytes[from + 3].toInt() and 0xFF)) == width
    if (!isRle) {
        check(!(bytes[from].toInt() == 1 && bytes[from + 1].toInt() == 1 && bytes[from + 2].toInt() == 1)) {
            "old-style Radiance RLE"
        }
        bytes.copyInto(into, 0, from, from + width * 4)
        return from + width * 4
    }
    var at = from + 4
    for (channel in 0 until 4) {
        var x = 0
        while (x < width) {
            val count = bytes[at++].toInt() and 0xFF
            if (count > 128) {
                val value = bytes[at++]
                repeat(count - 128) { into[(x + it) * 4 + channel] = value }
                x += count - 128
            } else {
                check(count > 0 && x + count <= width) { "bad RLE run $count at $x" }
                repeat(count) { into[(x + it) * 4 + channel] = bytes[at++] }
                x += count
            }
        }
    }
    return at
}

/**
 * RGBE → linear ((mantissa + ½) × 2^(E−136)) → sRGB 8-bit, clipped.
 *
 * The half step is Radiance's own `colr_color`: writers truncate, so the middle
 * of the step is the best reconstruction. Measured 2026-10-24 against the sRGB
 * source of an ImageMagick-written file: max error 6, mean 0.11 with it; 12 and
 * 0.23 without. What remains is RGBE's own loss — three channels share one
 * exponent, so a dark channel beside a bright one keeps few bits.
 */
private fun rgbeToSrgb(rgbe: ByteArray, at: Int): Int {
    val exponent = rgbe[at + 3].toInt() and 0xFF
    if (exponent == 0) return 0
    val scale = 2.0.pow(exponent - 136)
    fun channel(offset: Int): Int = srgb(((rgbe[at + offset].toInt() and 0xFF) + 0.5) * scale)
    return (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
}

private fun srgb(linear: Double): Int {
    val clipped = linear.coerceIn(0.0, 1.0)
    val encoded = if (clipped <= 0.0031308) clipped * 12.92 else 1.055 * clipped.pow(1 / 2.4) - 0.055
    return (encoded * 255).roundToInt()
}
