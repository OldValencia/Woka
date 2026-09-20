package com.woka.unit

import com.woka.feature.viewer.video.formatTime
import com.woka.feature.viewer.video.usesHours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE

/**
 * The seek bar's labels. Before this, `formatTime` knew only minutes and seconds,
 * so a 95-minute film read `95:03` — minutes that never rolled over.
 */
class TimeFormatTest {

    @Test
    fun `a clip is minutes and a film is hours`() {
        assertEquals("25:05", formatTime(25 * MINUTE + 5 * SECOND))
        assertEquals("1:24:34", formatTime(HOUR + 24 * MINUTE + 34 * SECOND))
        assertFalse(usesHours(59 * MINUTE))
        assertTrue(usesHours(HOUR))
    }

    @Test
    fun `the shape is the file's, not the value's`() {
        // Four minutes into a film. Left to itself this value would print `4:12`
        // and the label would gain two characters when the film crossed an hour.
        assertEquals("0:04:12", formatTime(4 * MINUTE + 12 * SECOND, withHours = true))
        assertEquals("04:12", formatTime(4 * MINUTE + 12 * SECOND, withHours = false))
    }

    @Test
    fun `hours are the largest unit and are not capped`() {
        assertEquals("74:12:05", formatTime(74 * HOUR + 12 * MINUTE + 5 * SECOND))
    }

    @Test
    fun `nothing before the beginning`() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:00", formatTime(-5_000L))
        assertEquals("0:00:00", formatTime(-5_000L, withHours = true))
    }

    @Test
    fun `the hour boundary rolls over rather than counting on`() {
        assertEquals("59:59", formatTime(HOUR - SECOND))
        assertEquals("1:00:00", formatTime(HOUR))
        assertEquals("1:00:01", formatTime(HOUR + SECOND))
    }

    @Test
    fun `past an hour the forced shape is the shape it would choose`() {
        listOf(HOUR, 2 * HOUR + 3 * SECOND, 74 * HOUR).forEach {
            assertEquals(formatTime(it), formatTime(it, withHours = true), "$it ms")
        }
    }
}
