package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val BYTES_PER_PIXEL = 4

/**
 * Assembles one HEIF tile grid into a single picture.
 *
 * This exists because FFmpeg decodes every piece of a tiled HEIC and JavaCV
 * cannot put them together: the assembled image is an `AVStreamGroup`, and
 * `FFmpegFrameGrabber` has no concept of one. Every real-world HEIC measured is
 * tiled, so without this the format opens and shows nothing.
 *
 * No PNG round trip on the way out, unlike the single-frame path — these are
 * 36-megapixel pictures and re-encoding one costs seconds. The canvas is built
 * straight in `BGRA_8888`, which is what the tiles were converted to, and
 * handed to Skia as a raster.
 */
internal fun assembleTileGrid(file: File, groupIndex: Int, maxEdge: Int = 0): ImageBitmap =
    onFormatContext(file) { format ->
        val full = readTileGrid(format, groupIndex)
        val shrink = full.shrinkFactorFor(maxEdge)
        val grid = if (shrink == 1) full else full.shrunkBy(shrink)
        val packets = collectPackets(format, grid.tiles.map { it.stream }.toSet())
        try {
            // Resolved here, on one thread, so the parallel decode below never
            // touches the format context — see [TileJob].
            val jobs = grid.tiles.associate { tile ->
                tile.stream to TileJob(
                    tile = tile,
                    codecpar = format.streams(tile.stream).codecpar(),
                    packet = packets[tile.stream] ?: error("no packet for stream ${tile.stream}")
                )
            }
            grid.toImageBitmap(
                grid.paintTiles { tile -> decodeTile(jobs.getValue(tile.stream), shrink) }
            )
        } finally {
            releasePackets(packets)
        }
    }

/**
 * The largest power-of-two reduction that still leaves the picture at or above
 * [maxEdge], or 1 for the full thing.
 *
 * Halving rather than scaling to fit exactly: a 6016 px grid becomes 376 px at
 * ÷16, which is close enough to a 400 px preview and, unlike 6016/400, divides
 * the 512 px tiles and their offsets without a remainder.
 */
private fun HeifTileGrid.shrinkFactorFor(maxEdge: Int): Int {
    if (maxEdge <= 0) return 1
    var factor = 1
    while (max(width, height) / (factor * 2) >= maxEdge) factor *= 2
    return factor
}

/**
 * The canvas, painted — **tiles decoded in parallel**.
 *
 * Decoding is the whole cost of this path: 144 tiles measured at 691 ms on one
 * thread against 263 ms to open the container and 30 ms to raster. They are
 * independent by construction — a codec context each, a packet each, and a
 * disjoint rectangle each — so the only thing serialising them was the loop.
 *
 * Peak memory stays bounded because a tile is blitted and dropped as soon as it
 * decodes: the parallelism, not the tile count, is what is ever held at once.
 *
 * [decode] is a parameter so the placement maths can be tested without FFmpeg,
 * which is the only way it *can* be tested: no tool available writes a tiled
 * HEIF, so there is no fixture for this path and there cannot be one.
 */
internal fun HeifTileGrid.paintTiles(decode: (HeifTile) -> DecodedTile): ByteArray {
    val canvas = ByteArray(width * height * BYTES_PER_PIXEL)
    tiles.parallelStream().forEach { tile -> blit(canvas, decode(tile), tile) }
    return canvas
}

private fun HeifTileGrid.toImageBitmap(canvas: ByteArray): ImageBitmap {
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    return Image.makeRaster(info, canvas, width * BYTES_PER_PIXEL).toComposeImageBitmap()
}

/**
 * Copies the visible part of one tile onto the canvas, row by row.
 *
 * Safe to run from several threads at once because grid tiles cover disjoint
 * rectangles: no two calls ever write the same byte.
 *
 * The clipping is not defensive tidiness. Tiles are laid out in the *coded*
 * canvas, which is padded up to a whole number of tiles — a 6016×6016 picture
 * is coded at 6144×6144 — so the last column and the last row genuinely hang
 * over the edge, and copying them whole would run off the end of the array.
 */
private fun HeifTileGrid.blit(canvas: ByteArray, tile: DecodedTile, at: HeifTile) {
    val sourceLeft = max(0, -at.left)
    val sourceTop = max(0, -at.top)
    val left = at.left + sourceLeft
    val top = at.top + sourceTop
    val visibleWidth = min(tile.width - sourceLeft, width - left)
    val visibleHeight = min(tile.height - sourceTop, height - top)
    if (visibleWidth <= 0 || visibleHeight <= 0) return
    val sourceStride = tile.width * BYTES_PER_PIXEL
    val canvasStride = width * BYTES_PER_PIXEL
    (0 until visibleHeight).forEach { row ->
        System.arraycopy(
            tile.bgra,
            (sourceTop + row) * sourceStride + sourceLeft * BYTES_PER_PIXEL,
            canvas,
            (top + row) * canvasStride + left * BYTES_PER_PIXEL,
            visibleWidth * BYTES_PER_PIXEL
        )
    }
}
