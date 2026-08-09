package com.woka.unit

import com.woka.core.data.WatchRecord
import com.woka.core.data.WokaSettings
import com.woka.core.data.withWatchFinished
import com.woka.core.data.withWatchPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PATH = "D:/Films/a.mkv"
private const val DURATION = 5_400_000L

/**
 * The state machine behind the grid's watched markers. Pure — no player, no disk —
 * which is what lets the one trap in the feature be pinned down: a finished video
 * re-opened at position 0 must not erase its own mark.
 */
class WatchRecordTest {

    private fun WokaSettings.record(): WatchRecord? = watchRecords[PATH]

    @Test
    fun `re-opening a finished video keeps its mark`() {
        // Finished, then opened again: resume returns null for a finished file, so
        // playback starts at 0 and the saver writes that position on its first tick.
        val after = WokaSettings()
            .withWatchFinished(PATH, DURATION)
            .withWatchPosition(PATH, 0L, DURATION)
        assertTrue(after.record()?.finished == true, "the watched mark was erased on re-open")
    }

    @Test
    fun `playing to the end marks it and keeps the record`() {
        val after = WokaSettings()
            .withWatchPosition(PATH, 4_000_000L, DURATION)
            .withWatchFinished(PATH, DURATION)
        val record = after.record()
        assertTrue(record?.finished == true)
        assertEquals(1f, record?.progress, "a finished video does not fill its bar")
    }

    @Test
    fun `watching a finished video again clears the mark`() {
        val after = WokaSettings()
            .withWatchFinished(PATH, DURATION)
            .withWatchPosition(PATH, 600_000L, DURATION)
        assertFalse(after.record()?.finished == true, "it is in progress again")
        assertEquals(600_000L / DURATION.toFloat(), after.record()?.progress)
    }

    @Test
    fun `a hand-marked video fills the bar without ever being played`() {
        // Marked from the grid: no duration is known, and none is needed.
        val record = WokaSettings().withWatchFinished(PATH, durationMs = 0L).record()
        assertEquals(1f, record?.progress)
        assertTrue(record?.isStarted == true)
    }

    @Test
    fun `playing a hand-marked video afterwards replaces it with a real record`() {
        val after = WokaSettings()
            .withWatchFinished(PATH, durationMs = 0L)
            .withWatchPosition(PATH, 600_000L, DURATION)
        assertEquals(DURATION, after.record()?.durationMs)
        assertFalse(after.record()?.finished == true)
    }

    @Test
    fun `a position with no known duration draws nothing rather than guessing`() {
        val record = WokaSettings().withWatchPosition(PATH, 600_000L, durationMs = 0L).record()
        assertNull(record?.progress, "a fraction was invented from an unknown length")
        assertTrue(record?.isStarted == true, "it is still started, and still dims")
    }

    @Test
    fun `an untouched video is neither started nor drawable`() {
        val record = WatchRecord()
        assertFalse(record.isStarted)
        assertNull(record.progress)
    }
}
