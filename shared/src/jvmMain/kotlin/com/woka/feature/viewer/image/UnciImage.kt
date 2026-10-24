package com.woka.feature.viewer.image

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/** Non-premultiplied ARGB, row-major — what [composePaintOverlay] blends. */
internal class ArgbPixels(val width: Int, val height: Int, val argb: IntArray)

/** ISO/IEC 23001-17 `cmpd` component types this reader maps; anything else is refused. */
private const val MONOCHROME = 0
private const val RED = 4
private const val GREEN = 5
private const val BLUE = 6
private const val ALPHA = 7

/** `uncC` version 1 names its layout instead of listing components. */
private val SIMPLIFIED_PROFILES = mapOf(
    "rgb3" to listOf(RED, GREEN, BLUE),
    "rgba" to listOf(RED, GREEN, BLUE, ALPHA),
    "abgr" to listOf(ALPHA, BLUE, GREEN, RED)
)

/**
 * One `unci` item — ISO/IEC 23001-17 uncompressed pixels — as ARGB.
 *
 * **Deliberately narrow: 8-bit unsigned components, one tile, no blocks, no
 * padding**, pixel- or component-interleaved. That is what Windows 11 Paint
 * writes (measured 2026-10-24: B,G,R,A pixel-interleaved, raw-DEFLATE `cmpC`),
 * and every other layout fails with the field that ruled it out rather than
 * drawing garbage.
 */
internal fun decodeUnci(file: File, meta: HeifMeta, item: Int): ArgbPixels {
    val bytes = meta.bytes
    val ispe = meta.property(item, "ispe") ?: error("unci $item has no ispe")
    val uncC = meta.property(item, "uncC") ?: error("unci $item has no uncC")
    val width = bytes.u32(ispe.start + 4)
    val height = bytes.u32(ispe.start + 8)
    val layout = componentLayout(meta, item, uncC)
    val raw = inflateIfCompressed(meta, item, itemBytes(file, meta, item))
    val count = layout.size
    check(raw.size >= width * height * count) { "unci $item holds ${raw.size} bytes" }
    val argb = IntArray(width * height) { pixel ->
        var a = 0xFF
        var r = 0
        var g = 0
        var b = 0
        for (component in 0 until count) {
            val at = if (layout.interleaved) pixel * count + component else component * width * height + pixel
            val value = raw[at].toInt() and 0xFF
            when (layout.types[component]) {
                RED -> r = value
                GREEN -> g = value
                BLUE -> b = value
                ALPHA -> a = value
                MONOCHROME -> { r = value; g = value; b = value }
            }
        }
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return ArgbPixels(width, height, argb)
}

private class ComponentLayout(val types: List<Int>, val interleaved: Boolean) {
    val size: Int get() = types.size
}

private fun componentLayout(meta: HeifMeta, item: Int, uncC: HeifBox): ComponentLayout {
    val bytes = meta.bytes
    val profile = bytes.fourCc(uncC.start + 4)
    if (bytes.u8(uncC.start) == 1) {
        val types = SIMPLIFIED_PROFILES[profile] ?: error("uncC profile $profile")
        return ComponentLayout(types, interleaved = true)
    }
    val cmpd = meta.property(item, "cmpd") ?: error("unci $item has no cmpd")
    val count = bytes.u32(uncC.start + 8)
    var at = uncC.start + 12
    val types = List(count) {
        val index = bytes.u16(at)
        val bitsMinusOne = bytes.u8(at + 2)
        val format = bytes.u8(at + 3)
        check(bitsMinusOne == 7 && format == 0) { "component ${bits(bitsMinusOne)}-bit format $format" }
        at += 5
        bytes.u16(cmpd.start + 4 + index * 2).also {
            check(it in setOf(MONOCHROME, RED, GREEN, BLUE, ALPHA)) { "component type $it" }
        }
    }
    val sampling = bytes.u8(at)
    val interleave = bytes.u8(at + 1)
    val blockSize = bytes.u8(at + 2)
    val tileColumns = bytes.u32(at + 16)
    val tileRows = bytes.u32(at + 20)
    check(sampling == 0 && blockSize == 0) { "sampling $sampling, block size $blockSize" }
    check(interleave <= 1) { "interleave $interleave" }
    check(tileColumns == 0 && tileRows == 0) { "${tileColumns + 1}×${tileRows + 1} tiles" }
    return ComponentLayout(types, interleaved = interleave == 1)
}

private fun bits(minusOne: Int): Int = minusOne + 1

/** The item's extents, joined — from the file or from `idat`. */
internal fun itemBytes(file: File, meta: HeifMeta, item: Int): ByteArray {
    val out = ByteArrayOutputStream()
    meta.extents[item].orEmpty().forEach { extent ->
        if (extent.inIdat) {
            val idat = meta.idat ?: error("item $item lives in a missing idat")
            val from = idat.start + extent.offset.toInt()
            out.write(meta.bytes.bytes, from, extent.length)
        } else {
            out.write(readFileRange(file, extent.offset, extent.length))
        }
    }
    return out.toByteArray()
}

/** `cmpC` with a whole-image unit: `defl` is raw DEFLATE, `zlib` the wrapped form. */
private fun inflateIfCompressed(meta: HeifMeta, item: Int, data: ByteArray): ByteArray {
    val cmpC = meta.property(item, "cmpC") ?: return data
    val type = meta.bytes.fourCc(cmpC.start + 4)
    val unit = meta.bytes.u8(cmpC.start + 8)
    check(unit == 0) { "cmpC unit type $unit" }
    check(type == "defl" || type == "zlib") { "cmpC compression $type" }
    val inflater = Inflater(type == "defl")
    return try {
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val chunk = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(chunk)
            check(read > 0 || !(inflater.needsInput() || inflater.needsDictionary())) { "truncated $type stream" }
            out.write(chunk, 0, read)
        }
        out.toByteArray()
    } finally {
        inflater.end()
    }
}
