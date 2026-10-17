package com.woka.unit

import com.woka.feature.folder.ItemBox
import com.woka.feature.folder.parentWithinRoot
import com.woka.feature.folder.stepAcross
import com.woka.feature.folder.stepVertically
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The grid's arrows and Backspace (SIDE-005), as the pure decisions they are: where
 * the focus goes from item positions, and where "up" is from a path.
 */
class FolderKeysTest {

    /** Three tiles a row, 100 wide, rows 100 apart. */
    private val grid = (0 until 9).map { ItemBox(it, x = (it % 3) * 100, y = (it / 3) * 100, width = 100) }

    @Test
    fun `down lands on the item below`() {
        assertEquals(4, stepVertically(1, grid, down = true, count = 9))
        assertEquals(1, stepVertically(4, grid, down = false, count = 9))
    }

    @Test
    fun `down from a wide folder card lands on the tile nearest its centre`() {
        val boxes = listOf(
            ItemBox(0, x = 0, y = 0, width = 300),
            ItemBox(1, x = 0, y = 200, width = 100),
            ItemBox(2, x = 100, y = 200, width = 100),
            ItemBox(3, x = 200, y = 200, width = 100)
        )
        assertEquals(2, stepVertically(0, boxes, down = true, count = 4))
    }

    @Test
    fun `with the next row off screen the move is the current row's length`() {
        val visible = grid.take(3)
        assertEquals(4, stepVertically(1, visible, down = true, count = 9))
    }

    @Test
    fun `moves are held at both ends`() {
        assertEquals(8, stepVertically(7, grid, down = true, count = 9))
        assertEquals(0, stepAcross(0, right = false, count = 9))
        assertEquals(8, stepAcross(8, right = true, count = 9))
        assertEquals(3, stepAcross(2, right = true, count = 9))
    }

    @Test
    fun `backspace climbs inside a root and stops at it`() {
        val roots = listOf("D:/Media/Movies")
        val parent = parentWithinRoot("D:/Media/Movies/Series/Andor", roots)
        assertEquals(File("D:/Media/Movies/Series").absoluteFile.normalize().path, parent)
        assertNull(parentWithinRoot("D:/Media/Movies", roots))
        assertNull(parentWithinRoot("E:/Elsewhere/Folder", roots))
    }
}
