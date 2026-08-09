package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.WatchRecord
import com.woka.core.ui.components.WatchMarks
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val CLIP = "D:/Films/clip.mkv"
private const val FILM = "D:/Films/film.mkv"

/** "Long videos" means twenty minutes, the shipped threshold. */
private const val LONG_ONLY_MS = 20 * 60_000L

/**
 * Which records reach the grid. The rule that matters: a setting change must not
 * leave a card claiming something the player has stopped honouring.
 */
class WatchMarksTest {

    private val clip = MediaItem.Video(File(CLIP))
    private val film = MediaItem.Video(File(FILM))

    // Keyed off the item's own absolutePath, not the literal: on Windows that
    // normalises the separators, and a map keyed by the literal never matches.
    private fun marks(minDurationMs: Long) = WatchMarks(
        records = mapOf(
            clip.file.absolutePath to WatchRecord(positionMs = 8_000L, durationMs = 20_000L),
            film.file.absolutePath to WatchRecord(
                positionMs = 600_000L,
                durationMs = 2 * 60 * 60_000L
            )
        ),
        enabled = true,
        minDurationMs = minDurationMs
    )

    @Test
    fun `Always draws every record`() {
        val marks = marks(minDurationMs = 0L)
        assertNotNull(marks.forVideo(clip))
        assertNotNull(marks.forVideo(film))
    }

    @Test
    fun `Long videos hides a short file written under an earlier setting`() {
        val marks = marks(minDurationMs = LONG_ONLY_MS)
        assertNull(marks.forVideo(clip), "a clip kept its bar while refusing to resume")
        assertNotNull(marks.forVideo(film))
    }

    @Test
    fun `Off draws nothing`() {
        val marks = marks(minDurationMs = 0L).copy(enabled = false)
        assertNull(marks.forVideo(clip))
        assertNull(marks.forVideo(film))
    }

    @Test
    fun `a hand-marked record survives Long videos`() {
        // No duration is known, so no length gate can judge it — and the user said
        // outright that they had watched it.
        val marks = WatchMarks(
            records = mapOf(clip.file.absolutePath to WatchRecord(finished = true)),
            enabled = true,
            minDurationMs = LONG_ONLY_MS
        )
        assertNotNull(marks.forVideo(clip), "a hand-marked video was overruled by the setting")
    }

    @Test
    fun `photographs and folders are never marked`() {
        val marks = WatchMarks(
            records = mapOf(
                clip.file.absolutePath to WatchRecord(positionMs = 8_000L, durationMs = 20_000L)
            ),
            enabled = true
        )
        assertNull(marks.forVideo(MediaItem.Image(File(CLIP))))
        assertNull(marks.forVideo(MediaItem.Folder(File(CLIP))))
    }
}
