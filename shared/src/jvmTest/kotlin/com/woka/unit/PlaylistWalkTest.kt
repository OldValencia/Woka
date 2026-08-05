package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.shell.MediaViewerPlaylist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The walk is pure list arithmetic — no file is opened. `MediaItem` stats its
 * path on construction, which for a path that does not exist is a no-op.
 */
private fun video(name: String) = MediaItem.Video(File("walk-fixture", name))

private fun image(name: String) = MediaItem.Image(File("walk-fixture", name))

private val MediaItem.id: String get() = file.absolutePath

class PlaylistWalkTest {

    private val one = video("a.mkv")
    private val two = video("b.mkv")
    private val three = video("c.mkv")
    private val folder = listOf(one, image("p.jpg"), two, image("q.png"), three)

    @Test
    fun `neither shuffle nor folder repeat means stop at the end`() {
        val playlist = MediaViewerPlaylist()
        assertNull(playlist.nextPath(one.id, folder, shuffle = false, repeat = RepeatMode.Off))
        assertNull(playlist.nextPath(one.id, folder, shuffle = false, repeat = RepeatMode.File))
    }

    @Test
    fun `a folder with fewer than two videos has nowhere to advance`() {
        val playlist = MediaViewerPlaylist()
        val single = listOf(one, image("p.jpg"), image("q.png"))
        assertNull(playlist.nextPath(one.id, single, shuffle = false, repeat = RepeatMode.Folder))
        assertNull(playlist.nextPath(one.id, emptyList(), shuffle = true, repeat = RepeatMode.Folder))
    }

    @Test
    fun `images are skipped rather than played`() {
        val playlist = MediaViewerPlaylist()
        val next = playlist.nextPath(one.id, folder, shuffle = false, repeat = RepeatMode.Folder)
        assertEquals(two.id, next)
    }

    @Test
    fun `an image can never be the current file of a walk`() {
        val playlist = MediaViewerPlaylist()
        val next = playlist.nextPath(
            currentId = folder[1].id,
            mediaItems = folder,
            shuffle = false,
            repeat = RepeatMode.Folder
        )
        assertNull(next)
    }

    @Test
    fun `folder repeat wraps back to the first video`() {
        val playlist = MediaViewerPlaylist()
        val next = playlist.nextPath(three.id, folder, shuffle = false, repeat = RepeatMode.Folder)
        assertEquals(one.id, next)
    }

    @Test
    fun `shuffle without folder repeat stops once the order is exhausted`() {
        val playlist = MediaViewerPlaylist()
        var current = one.id
        val seen = mutableListOf(current)
        // Bounded rather than `while (true)`: without folder repeat the walk must
        // terminate, and hanging is the failure this guards against.
        repeat(10) {
            val next = playlist.nextPath(current, folder, shuffle = true, repeat = RepeatMode.Off)
                ?: return@repeat
            assertTrue(next !in seen, "$next played twice before the order ran out")
            seen.add(next)
            current = next
        }
        assertNull(playlist.nextPath(current, folder, shuffle = true, repeat = RepeatMode.Off))
        assertTrue(seen.size <= 3, "the walk left the folder: $seen")
    }

    @Test
    fun `exactly one video ends a shuffled walk`() {
        val playlist = MediaViewerPlaylist()
        val ends = listOf(one, two, three).count { item ->
            playlist.nextPath(item.id, folder, shuffle = true, repeat = RepeatMode.Off) == null
        }
        assertEquals(1, ends, "a shuffled order has exactly one last element")
    }

    @Test
    fun `a file outside the folder has no successor`() {
        val playlist = MediaViewerPlaylist()
        val stranger = video("elsewhere.mkv").id
        assertNull(playlist.nextPath(stranger, folder, shuffle = true, repeat = RepeatMode.Folder))
    }

    @Test
    fun `the shuffled order is stable across calls`() {
        val playlist = MediaViewerPlaylist()
        val first = playlist.nextPath(one.id, folder, shuffle = true, repeat = RepeatMode.Folder)
        repeat(30) {
            val again = playlist.nextPath(one.id, folder, shuffle = true, repeat = RepeatMode.Folder)
            assertEquals(first, again, "the order was reshuffled between calls")
        }
    }

    @Test
    fun `the shuffled order is a permutation of the videos`() {
        val playlist = MediaViewerPlaylist()
        val videos = listOf(one.id, two.id, three.id)
        val walk = mutableListOf(one.id)
        repeat(2) {
            walk.add(playlist.nextPath(walk.last(), folder, true, RepeatMode.Folder)!!)
        }
        assertEquals(videos.sorted(), walk.sorted())
        assertEquals(one.id, playlist.nextPath(walk.last(), folder, true, RepeatMode.Folder))
    }

    @Test
    fun `changing the folder contents rebuilds the order`() {
        val playlist = MediaViewerPlaylist()
        assertEquals(two.id, playlist.nextPath(one.id, folder, false, RepeatMode.Folder))
        val reordered = listOf(one, three, two)
        assertEquals(three.id, playlist.nextPath(one.id, reordered, false, RepeatMode.Folder))
    }
}
