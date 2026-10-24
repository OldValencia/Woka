package com.woka.feature.viewer.image

import org.bytedeco.javacv.Frame
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer

/**
 * The decoded frame as a `TYPE_4BYTE_ABGR` image, **taking four channels as a given
 * rather than asking the frame how many it has** (ME-182).
 *
 * `Java2DFrameConverter` infers the channel count as `imageStride / imageWidth`, and
 * ffmpeg pads each row to an alignment. At most widths the division is either exact at
 * three or not exact at all, and the inference survives — but where the padding happens
 * to add exactly one byte per pixel it lands on **four**, and the picture is then read
 * four bytes at a time out of three-byte data. Measured 2026-09-03: a 96-wide frame
 * pads 288 to 384, `384 / 96 == 4`, and the result was the right half black with the
 * rows walking. 128 (`384 / 128 == 3`) and 160 (`576 / 160 == 3.6`) were unaffected,
 * which is why this went unseen — the corpus is 320 and 640 wide.
 *
 * Nothing has to be inferred here: [onGrabber] asks for `AV_PIX_FMT_BGRA` and that is
 * what the buffer holds. The stride is still honoured, because the padding is real.
 *
 * BGRA rather than BGR24 so `dds`, `xpm`, `tga`, `qoi` and `psd` keep their alpha —
 * BGR24 rendered every transparent pixel black. The raster stores A,B,G,R, so each
 * pixel is reordered on the way in rather than copied row-wise.
 */
/** `AV_PIX_FMT_BGRA` is what [onGrabber] asks for, and this is its width in bytes. */
private const val BGRA_CHANNELS = 4

internal fun Frame.toBgraImage(): BufferedImage {
    val source = image[0] as ByteBuffer
    val target = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_4BYTE_ABGR)
    val row = ByteArray(imageWidth * BGRA_CHANNELS)
    val into = (target.raster.dataBuffer as DataBufferByte).data

    for (y in 0 until imageHeight) {
        source.position(y * imageStride)
        source.get(row, 0, row.size)
        val base = y * row.size
        for (at in row.indices step BGRA_CHANNELS) {
            into[base + at] = row[at + 3]
            into[base + at + 1] = row[at]
            into[base + at + 2] = row[at + 1]
            into[base + at + 3] = row[at + 2]
        }
    }
    source.rewind()
    return target
}
