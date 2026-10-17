package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.folder.gridIndexOf
import com.woka.feature.folder.hasSections
import com.woka.feature.folder.isMostlyPhotos
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which layout a folder gets (SIDE-004), and where its items land once the mosaic's
 * headings take places in the grid. Pure: the files are never opened.
 */
class FolderGridLayoutTest {

    private fun folder(name: String) = MediaItem.Folder(File("D:/F/$name"))
    private fun photo(name: String) = MediaItem.Image(File("D:/F/$name.jpg"))
    private fun video(name: String) = MediaItem.Video(File("D:/F/$name.mp4"))

    @Test
    fun `a folder of photos is a mosaic`() {
        assertTrue(listOf(photo("a"), photo("b"), video("c")).isMostlyPhotos())
    }

    @Test
    fun `half video or more is cards`() {
        assertFalse(listOf(photo("a"), video("b")).isMostlyPhotos())
        assertFalse(listOf(video("a"), video("b"), photo("c")).isMostlyPhotos())
    }

    @Test
    fun `sub-folders count for neither side`() {
        val shelf = listOf(folder("S1"), folder("S2"), folder("S3"), video("trailer"))
        assertFalse(shelf.isMostlyPhotos())
        val album = listOf(folder("A"), folder("B"), folder("C"), photo("cover"))
        assertTrue(album.isMostlyPhotos())
    }

    @Test
    fun `a folder with no media is cards`() {
        assertFalse(emptyList<MediaItem>().isMostlyPhotos())
        assertFalse(listOf(folder("A")).isMostlyPhotos())
    }

    @Test
    fun `sections appear whenever there are sub-folders, in either layout`() {
        assertFalse(listOf(photo("a")).hasSections())
        assertTrue(listOf(folder("A"), photo("a")).hasSections())
        assertTrue(listOf(folder("A"), video("a")).hasSections())
        assertTrue(listOf(folder("A")).hasSections(), "a filter that empties Media keeps both headings")
    }

    @Test
    fun `a folder sits one heading down and a photo two`() {
        val items = listOf(folder("A"), folder("B"), photo("a"), photo("b"))
        assertEquals(1, items.gridIndexOf(0))
        assertEquals(2, items.gridIndexOf(1))
        assertEquals(4, items.gridIndexOf(2))
        assertEquals(5, items.gridIndexOf(3))
    }

    @Test
    fun `without headings an index is its own`() {
        val items = listOf(photo("a"), photo("b"))
        assertEquals(1, items.gridIndexOf(1))
        assertEquals(-1, items.gridIndexOf(-1))
    }
}
