package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.shell.MediaViewerPlaylist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun track(name: String) = MediaItem.Audio(File("walk-fixture", name))

/**
 * Music plays a folder through (AP-003): Repeat Off walks on to the end of the
 * order and stops there, where a film would stop after itself.
 */
class AudioPlaylistWalkTest {

    private val one = track("a.mp3")
    private val two = track("b.flac")
    private val folder = listOf(one, two)

    @Test
    fun `repeat off plays the next track`() {
        val next = MediaViewerPlaylist().nextPath(
            one.file.absolutePath, folder, shuffle = false, repeat = RepeatMode.Off, playThrough = true
        )
        assertEquals(two.file.absolutePath, next)
    }

    @Test
    fun `repeat off stops after the last track`() {
        val next = MediaViewerPlaylist().nextPath(
            two.file.absolutePath, folder, shuffle = false, repeat = RepeatMode.Off, playThrough = true
        )
        assertNull(next)
    }

    @Test
    fun `repeat folder wraps to the first track`() {
        val next = MediaViewerPlaylist().nextPath(
            two.file.absolutePath, folder, shuffle = false, repeat = RepeatMode.Folder, playThrough = true
        )
        assertEquals(one.file.absolutePath, next)
    }
}
