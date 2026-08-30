package com.woka.integration

import com.woka.core.ui.components.fetchFolderStateData
import com.woka.support.withTempStorageRoot
import com.woka.support.flatImage
import com.woka.support.transparentImage
import com.woka.support.variedImage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which picture a folder card borrows for its cover.
 *
 * Two reports from the Home grid, 2026-08-30: a folder holding only subfolders drew
 * nothing, and a folder whose one picture was fully transparent drew nothing either —
 * it decoded perfectly, painted no pixels, and left the card a cover it could not show.
 *
 * The fixtures are real encoded images rather than junk bytes, because the question
 * this file asks is now about what a picture *contains*.
 */
class FolderCoverTest {

    private fun root(): File = Files.createTempDirectory("woka-folder-cover").toFile()

    private fun subfolder(root: File, name: String) = File(root, name).apply { mkdirs() }

    private fun coverOf(dir: File) =
        withTempStorageRoot { runBlocking { fetchFolderStateData(dir) } }

    @Test
    fun `a folder of pictures covers itself with the first of them`() {
        val dir = root()
        variedImage(dir, "a.jpg")

        assertEquals(listOf("a.jpg"), coverOf(dir).previews.map { it.name })
    }

    /** The first report: nothing lies directly inside, so nothing used to be offered. */
    @Test
    fun `a folder of folders borrows a cover from the first subfolder that has one`() {
        val dir = root()
        variedImage(subfolder(dir, "trip"), "shot.jpg")

        assertEquals(listOf("shot.jpg"), coverOf(dir).previews.map { it.name })
    }

    /** By name, so the same folder shows the same cover on every launch. */
    @Test
    fun `the subfolder scanned first is the alphabetically first one`() {
        val dir = root()
        variedImage(subfolder(dir, "zebra"), "z.jpg")
        variedImage(subfolder(dir, "alpha"), "a.jpg")

        assertEquals(listOf("a.jpg"), coverOf(dir).previews.map { it.name })
    }

    /**
     * The second report: five subfolders of pictures, one fully transparent PNG and one
     * hidden file — and the transparent one won, being the first *file*.
     */
    @Test
    fun `a fully transparent picture is passed over for one with something in it`() {
        val dir = root()
        transparentImage(dir, "blank.png")
        variedImage(subfolder(dir, "trip"), "shot.jpg")

        assertEquals(listOf("shot.jpg"), coverOf(dir).previews.map { it.name })
    }

    /** The same rule without the transparency: one flat colour is nothing to look at. */
    @Test
    fun `a picture of one flat colour is passed over too`() {
        val dir = root()
        flatImage(dir, "aaa-red.png", 0xFFFF0000.toInt())
        variedImage(dir, "bbb-real.png")

        assertEquals(listOf("bbb-real.png"), coverOf(dir).previews.map { it.name })
    }

    /**
     * The regression the first attempt shipped: `coverHere().ifEmpty { … }` decided on
     * the *candidate list*, so blank files stopped the subfolder scan.
     */
    @Test
    fun `blank files inside do not stop the subfolder scan`() {
        val dir = root()
        transparentImage(dir, "aaa-blank.png")
        flatImage(dir, "bbb-flat.png", 0xFF203040.toInt())
        variedImage(subfolder(dir, "trip"), "shot.jpg")

        assertEquals(listOf("shot.jpg"), coverOf(dir).previews.map { it.name })
    }

    /** A folder whose only picture is blank has nothing to show, and says so. */
    @Test
    fun `a folder of nothing but blank pictures offers no cover`() {
        val dir = root()
        transparentImage(dir, "blank.png")

        assertTrue(coverOf(dir).previews.isEmpty(), "a blank picture was accepted as a cover")
    }

    /** One level, and no further — two is a different feature. */
    @Test
    fun `a picture two levels down is not borrowed`() {
        val dir = root()
        variedImage(subfolder(dir, "outer/inner"), "deep.jpg")

        assertTrue(coverOf(dir).previews.isEmpty(), "the scan walked past one level")
    }

    @Test
    fun `an empty folder still offers no cover`() {
        assertTrue(coverOf(root()).previews.isEmpty())
    }

    /** The count is about this directory, and the borrowed cover must not move it. */
    @Test
    fun `borrowing a cover does not change what the caption counts`() {
        val dir = root()
        variedImage(subfolder(dir, "one"), "a.jpg")
        subfolder(dir, "two")

        assertEquals(2, coverOf(dir).itemCount)
    }
}
