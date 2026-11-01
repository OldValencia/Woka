package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.woka.app.shell.nowplaying.cornerOffset
import com.woka.app.shell.nowplaying.nearestCorner
import com.woka.core.data.WindowCorner
import kotlin.test.Test
import kotlin.test.assertEquals

private val WINDOW = IntSize(1000, 700)

/** Where the Now Playing bubble sits and where a released drag lands (AP-006). */
class BubbleCornersTest {

    private fun at(corner: WindowCorner) = cornerOffset(corner, WINDOW, bubble = 48, edge = 16, top = 64)

    @Test
    fun `the bottom corners sit 16 from the sides and the bottom`() {
        assertEquals(IntOffset(16, 700 - 16 - 48), at(WindowCorner.BottomLeft))
        assertEquals(IntOffset(1000 - 16 - 48, 700 - 16 - 48), at(WindowCorner.BottomRight))
    }

    @Test
    fun `the top corners start under the title bar`() {
        assertEquals(IntOffset(16, 64), at(WindowCorner.TopLeft))
        assertEquals(IntOffset(1000 - 16 - 48, 64), at(WindowCorner.TopRight))
    }

    @Test
    fun `a release snaps to the quadrant it happened in`() {
        assertEquals(WindowCorner.TopLeft, nearestCorner(Offset(100f, 100f), WINDOW))
        assertEquals(WindowCorner.TopRight, nearestCorner(Offset(900f, 100f), WINDOW))
        assertEquals(WindowCorner.BottomLeft, nearestCorner(Offset(100f, 600f), WINDOW))
        assertEquals(WindowCorner.BottomRight, nearestCorner(Offset(900f, 600f), WINDOW))
    }
}
