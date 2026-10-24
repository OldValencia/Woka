package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/** An `iovl` overlay: canvas size, fill, and where each input sits. */
private class Overlay(
    val width: Int,
    val height: Int,
    val fillArgb: Int,
    val offsets: List<Pair<Int, Int>>
)

/**
 * Windows 11 Paint projects (`.paint`) — HEIF, with every layer an `unci` item and
 * the picture an `iovl` overlay of them (measured 2026-10-24 on the user's samples).
 *
 * The bundled FFmpeg opens the container and finds no streams — it has no `unci`
 * decoder — and no library on the classpath reads one, so this is Woka's own: a
 * box walk ([parseHeifMeta]), an inflate ([decodeUnci]) and an alpha blend.
 *
 * **Every layer is drawn.** Paint does not persist a layer's visibility — it
 * resets when the project is closed, and nothing in the file records it — so the
 * composite libheif renders is the picture Paint itself shows on reopening. The
 * tests hold this decoder to libheif's output pixel for pixel.
 */
internal object PaintStillDecoder : StillDecoder {

    /** One picture: the layers are its parts, not frames. */
    override fun frameSet(file: File): ImageFrameSet = ImageFrameSet.Single

    override fun nativeSize(file: File, frameIndex: Int): IntSize {
        val meta = parseHeifMeta(readHeifMetaBox(file))
        val primary = meta.primaryItem
        if (meta.itemTypes[primary] == "iovl") {
            val overlay = readOverlay(file, meta, primary)
            return IntSize(overlay.width, overlay.height)
        }
        val ispe = meta.property(primary, "ispe") ?: error("item $primary has no ispe")
        return IntSize(meta.bytes.u32(ispe.start + 4), meta.bytes.u32(ispe.start + 8))
    }

    override fun frame(file: File, frameIndex: Int): ImageBitmap {
        val meta = parseHeifMeta(readHeifMetaBox(file))
        val primary = meta.primaryItem
        val pixels = when (val type = meta.itemTypes[primary]) {
            "iovl" -> composePaintOverlay(file, meta, primary)
            "unci" -> decodeUnci(file, meta, primary)
            else -> error("primary item is $type")
        }
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width)
        return image.toStillBitmap()
    }
}

/** ISO/IEC 23008-12 §6.6.2.3: fill is four 16-bit RGBA channels; flag bit 0 widens the fields. */
private fun readOverlay(file: File, meta: HeifMeta, item: Int): Overlay {
    val data = IsoBytes(itemBytes(file, meta, item))
    val wide = (data.u8(1) and 1) == 1
    val field = if (wide) 4 else 2
    val fill = (0 until 4).map { data.u16(2 + it * 2) shr 8 }
    val sizeAt = 2 + 8
    val inputs = meta.derivedFrom[item].orEmpty().size
    val offsetsAt = sizeAt + field * 2
    return Overlay(
        width = data.uN(sizeAt, field),
        height = data.uN(sizeAt + field, field),
        fillArgb = (fill[3] shl 24) or (fill[0] shl 16) or (fill[1] shl 8) or fill[2],
        offsets = List(inputs) { index ->
            val at = offsetsAt + index * field * 2
            if (wide) data.u32(at) to data.u32(at + field) else data.s16(at) to data.s16(at + field)
        }
    )
}

/**
 * The overlay's inputs blended bottom-up onto its fill colour — straight-alpha
 * "over", the operator libheif uses; a layer reaching past the canvas is clipped.
 */
internal fun composePaintOverlay(file: File, meta: HeifMeta, item: Int): ArgbPixels {
    val overlay = readOverlay(file, meta, item)
    val canvas = IntArray(overlay.width * overlay.height) { overlay.fillArgb }
    meta.derivedFrom[item].orEmpty().forEachIndexed { index, layerItem ->
        val layer = decodeUnci(file, meta, layerItem)
        val (left, top) = overlay.offsets[index]
        for (y in 0 until layer.height) {
            val canvasY = top + y
            if (canvasY !in 0 until overlay.height) continue
            for (x in 0 until layer.width) {
                val canvasX = left + x
                if (canvasX !in 0 until overlay.width) continue
                val at = canvasY * overlay.width + canvasX
                canvas[at] = over(layer.argb[y * layer.width + x], canvas[at])
            }
        }
    }
    return ArgbPixels(overlay.width, overlay.height, canvas)
}

private fun over(source: Int, destination: Int): Int {
    val sourceAlpha = (source ushr 24) / 255.0
    if (sourceAlpha == 0.0) return destination
    if (sourceAlpha == 1.0) return source
    val destinationAlpha = (destination ushr 24) / 255.0 * (1 - sourceAlpha)
    val outAlpha = sourceAlpha + destinationAlpha
    fun channel(shift: Int): Int {
        val s = (source shr shift) and 0xFF
        val d = (destination shr shift) and 0xFF
        return ((s * sourceAlpha + d * destinationAlpha) / outAlpha).roundToInt()
    }
    return ((outAlpha * 255).roundToInt() shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
