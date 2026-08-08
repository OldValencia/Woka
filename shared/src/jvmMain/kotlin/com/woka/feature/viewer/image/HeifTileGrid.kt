package com.woka.feature.viewer.image

import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avformat.AV_STREAM_GROUP_PARAMS_TILE_GRID

/** Where one tile sits in the assembled picture, and which stream carries it. */
internal data class HeifTile(
    val stream: Int,
    val left: Int,
    val top: Int
)

/**
 * One `derived image` of a HEIF file: a picture stored as a grid of separately
 * coded tiles.
 *
 * FFmpeg 7.1+ describes these with an `AVStreamGroup`, which is the *only* way
 * to reach them — the tiles themselves are streams flagged
 * `AV_DISPOSITION_DEPENDENT` and `FFmpegFrameGrabber` can decode one of them
 * and nothing more. Every real-world HEIC measured (four wallpapers, 11–264 MB)
 * is built this way and carries **no** independent picture stream at all.
 *
 * [width]/[height] are the display size; [tiles] are positioned in the larger
 * *coded* canvas, so their coordinates are already shifted by the crop origin
 * here and a tile may hang off the right or bottom edge.
 */
internal data class HeifTileGrid(
    val width: Int,
    val height: Int,
    val tiles: List<HeifTile>
)

/**
 * The same grid with every coordinate divided by [by], for building a preview
 * without ever allocating the full picture.
 *
 * **Powers of two only, and that is the whole reason this is exact.** Tiles are
 * 512 px and sit at multiples of 512, so halving repeatedly divides both the
 * size and every offset with no remainder: the shrunken tiles still tessellate.
 * At an arbitrary ratio they would not, and the rounding would show as seams
 * between them — a 1 px line across a photograph is more noticeable than the
 * blur it was traded for.
 *
 * The display size is rounded **up** so the canvas can never be a row short of
 * what the tiles cover.
 */
internal fun HeifTileGrid.shrunkBy(by: Int): HeifTileGrid = HeifTileGrid(
    width = (width + by - 1) / by,
    height = (height + by - 1) / by,
    tiles = tiles.map { HeifTile(it.stream, it.left / by, it.top / by) }
)

/** Indices of the stream groups that are tile grids, in container order. */
internal fun tileGridGroups(format: AVFormatContext): List<Int> =
    (0 until format.nb_stream_groups()).filter { index ->
        format.stream_groups(index).type() == AV_STREAM_GROUP_PARAMS_TILE_GRID
    }

/**
 * Reads grid [groupIndex]'s geometry.
 *
 * `offsets_idx(i)` is an index **into the group**, not into the container, so
 * the tile's real stream comes from `group.streams(...)`. Getting that wrong
 * assembles a picture out of the wrong tiles rather than failing.
 */
internal fun readTileGrid(format: AVFormatContext, groupIndex: Int): HeifTileGrid {
    val group = format.stream_groups(groupIndex)
    val grid = group.params_tile_grid()
    val cropLeft = grid.horizontal_offset()
    val cropTop = grid.vertical_offset()
    val tiles = (0 until grid.nb_tiles()).map { tile ->
        HeifTile(
            stream = group.streams(grid.offsets_idx(tile)).index(),
            left = grid.offsets_horizontal(tile) - cropLeft,
            top = grid.offsets_vertical(tile) - cropTop
        )
    }
    return HeifTileGrid(grid.width(), grid.height(), tiles)
}
