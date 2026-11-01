package com.woka.unit

import androidx.compose.ui.geometry.Rect
import com.woka.app.shell.nowplaying.lerpRect
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ghost cover's path between the sidebar row and the bubble (AP-008). */
class CoverFlightTest {

    private val row = Rect(16f, 600f, 48f, 632f)
    private val disc = Rect(20f, 640f, 60f, 680f)

    @Test
    fun `the ghost starts on the row and lands on the disc`() {
        assertEquals(row, lerpRect(row, disc, 0f))
        assertEquals(disc, lerpRect(row, disc, 1f))
    }

    @Test
    fun `halfway it is halfway in place and in size`() {
        assertEquals(Rect(18f, 620f, 54f, 656f), lerpRect(row, disc, 0.5f))
    }
}
