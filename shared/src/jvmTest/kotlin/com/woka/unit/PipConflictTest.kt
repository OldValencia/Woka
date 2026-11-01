package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.musicConflicts
import com.woka.feature.viewer.shell.pipConflicts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FILM = File("films/film.mp4").absoluteFile
private val OTHER_FILM = File("films/other.mkv").absoluteFile
private val PHOTO = File("pictures/photo.jpg").absoluteFile

/**
 * What the "Video Already Playing" dialog is actually about.
 *
 * Found by hand on 2026-08-30: opening a **photograph** while a film played in the PiP
 * window raised it, because the check asked only whether some other file was being
 * opened. The dialog offers to close the video and open the new one — an offer that
 * makes no sense for a picture, which needs no player at all.
 */
class PipConflictTest {

    private val playing = FILM.absolutePath

    @Test
    fun `a photograph never collides with a playing video`() {
        assertFalse(pipConflicts(MediaItem.Image(PHOTO), pipActive = true, pipPath = playing))
    }

    @Test
    fun `a second video collides`() {
        assertTrue(pipConflicts(MediaItem.Video(OTHER_FILM), pipActive = true, pipPath = playing))
    }

    /** Returning from fullscreen opens the same file; that is a return, not a clash. */
    @Test
    fun `the file already playing does not collide with itself`() {
        assertFalse(pipConflicts(MediaItem.Video(FILM), pipActive = true, pipPath = playing))
    }

    @Test
    fun `nothing collides while no video window is up`() {
        assertFalse(pipConflicts(MediaItem.Video(OTHER_FILM), pipActive = false, pipPath = null))
        assertFalse(pipConflicts(MediaItem.Image(PHOTO), pipActive = false, pipPath = null))
    }

    @Test
    fun `an empty viewer asks nothing`() {
        assertFalse(pipConflicts(null, pipActive = true, pipPath = playing))
    }

    /** AP-003: a film over a playing track asks first; a picture or no music never does. */
    @Test
    fun `only a video collides with music`() {
        val track = File("music/song.mp3").absolutePath
        assertTrue(musicConflicts(MediaItem.Video(FILM), musicPath = track))
        assertFalse(musicConflicts(MediaItem.Image(PHOTO), musicPath = track))
        assertFalse(musicConflicts(MediaItem.Video(FILM), musicPath = null))
    }

    @Test
    fun `a track collides with a video in PiP`() {
        val track = MediaItem.Audio(File("music/song.mp3").absoluteFile)
        assertTrue(pipConflicts(track, pipActive = true, pipPath = playing))
    }
}
