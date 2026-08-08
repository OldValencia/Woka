package com.woka.unit

import com.woka.feature.viewer.image.DecodedTile
import com.woka.feature.viewer.image.HeifTile
import com.woka.feature.viewer.image.HeifTileGrid
import com.woka.feature.viewer.image.paintTiles
import com.woka.feature.viewer.image.shrunkBy
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BYTES_PER_PIXEL = 4

/** A tile painted a single value, so where it landed can be read off the canvas. */
private fun flatTile(size: Int, value: Int) =
    DecodedTile(size, size, ByteArray(size * size * BYTES_PER_PIXEL) { value.toByte() })

/** Blue channel of one canvas pixel — BGRA, so byte 0 of the group of four. */
private fun ByteArray.at(grid: HeifTileGrid, x: Int, y: Int): Int =
    this[(y * grid.width + x) * BYTES_PER_PIXEL].toInt() and 0xFF

/**
 * The tile placement maths, which is the part of the HEIF grid path that can go
 * subtly wrong: tiles land in the wrong place and the picture looks like a
 * shuffled mosaic rather than failing.
 *
 * **Tested without FFmpeg because it cannot be tested with it.** No tool
 * available writes a tiled HEIF — `pillow-heif` stores even an 8192×8192 image
 * as a single item — so the corpus has no fixture for this path and cannot
 * gain one. The decode half is verified by hand against real files; this is
 * the half that a test can hold.
 */
class TileGridAssemblyTest {

    @Test
    fun `each tile lands at its own offset`() {
        val grid = HeifTileGrid(
            width = 4,
            height = 2,
            tiles = listOf(HeifTile(stream = 0, left = 0, top = 0), HeifTile(1, 2, 0))
        )
        val canvas = grid.paintTiles { tile -> flatTile(2, if (tile.stream == 0) 0x11 else 0x22) }
        assertEquals(0x11, canvas.at(grid, 0, 0))
        assertEquals(0x11, canvas.at(grid, 1, 1))
        assertEquals(0x22, canvas.at(grid, 2, 0), "the second tile did not reach its column")
        assertEquals(0x22, canvas.at(grid, 3, 1))
    }

    /**
     * The edge case this maths exists for. A HEIF grid is coded padded up to a
     * whole number of tiles — a 6016×6016 picture is coded at 6144×6144 — so
     * the last column and row genuinely hang over the display size. Copying a
     * tile whole would run off the end of the canvas.
     */
    @Test
    fun `a tile hanging off the right edge is clipped, not wrapped`() {
        val grid = HeifTileGrid(width = 3, height = 2, tiles = listOf(HeifTile(0, 2, 0)))
        val canvas = grid.paintTiles { flatTile(2, 0x33) }
        assertEquals(0x33, canvas.at(grid, 2, 0))
        assertEquals(0x33, canvas.at(grid, 2, 1))
        assertEquals(0x00, canvas.at(grid, 0, 1), "the overhang wrapped onto the next row")
    }

    @Test
    fun `a tile hanging off the bottom edge is clipped`() {
        val grid = HeifTileGrid(width = 2, height = 3, tiles = listOf(HeifTile(0, 0, 2)))
        val canvas = grid.paintTiles { flatTile(2, 0x44) }
        assertEquals(0x44, canvas.at(grid, 0, 2))
        assertEquals(0x44, canvas.at(grid, 1, 2))
        assertEquals(grid.width * grid.height * BYTES_PER_PIXEL, canvas.size)
    }

    /** A grid whose tiles all sit outside it paints nothing and does not throw. */
    @Test
    fun `a tile entirely off the canvas is dropped`() {
        val grid = HeifTileGrid(width = 2, height = 2, tiles = listOf(HeifTile(0, 4, 4)))
        val canvas = grid.paintTiles { flatTile(2, 0x55) }
        assertEquals(0, canvas.count { it.toInt() != 0 })
    }

    @Test
    fun `the canvas covers exactly the display size`() {
        val grid = HeifTileGrid(width = 5, height = 3, tiles = emptyList())
        assertEquals(5 * 3 * BYTES_PER_PIXEL, grid.paintTiles { flatTile(1, 0) }.size)
    }

    /**
     * The shrunken grid is what a preview is assembled on, and it only works
     * because 512 px tiles at multiples of 512 halve without a remainder. If
     * the offsets ever stop dividing exactly, the tiles stop tessellating and
     * the picture gains seams.
     */
    @Test
    fun `shrinking divides the grid and every offset exactly`() {
        val grid = HeifTileGrid(
            width = 1024,
            height = 512,
            tiles = listOf(HeifTile(0, 0, 0), HeifTile(1, 512, 0), HeifTile(2, 0, 256))
        ).shrunkBy(8)
        assertEquals(128, grid.width)
        assertEquals(64, grid.height)
        assertEquals(listOf(0, 64, 0), grid.tiles.map { it.left })
        assertEquals(listOf(0, 0, 32), grid.tiles.map { it.top })
    }

    /** A shrunken grid's tiles still cover it edge to edge, with no gap. */
    @Test
    fun `a shrunken grid is still fully covered by its tiles`() {
        val grid = HeifTileGrid(
            width = 1024,
            height = 512,
            tiles = listOf(HeifTile(0, 0, 0), HeifTile(1, 512, 0))
        ).shrunkBy(8)
        val canvas = grid.paintTiles { flatTile(64, 0x66) }
        assertEquals(0, canvas.count { it.toInt() == 0 }, "the shrunken tiles left a gap")
    }

    /** Rounding up: a size that does not divide must not leave the canvas short. */
    @Test
    fun `an odd display size rounds up rather than losing a row`() {
        val grid = HeifTileGrid(width = 6016, height = 6016, tiles = emptyList()).shrunkBy(16)
        assertEquals(376, grid.width)
        assertEquals(376, grid.height)
    }
}
