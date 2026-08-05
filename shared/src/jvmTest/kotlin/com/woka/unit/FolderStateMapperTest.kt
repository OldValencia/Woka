package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.SortType
import com.woka.core.data.ViewLayout
import com.woka.core.data.WokaSettings
import com.woka.feature.folder.FolderSettings
import com.woka.feature.folder.buildFolderState
import com.woka.feature.folder.folderSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Date sorting is **not** covered here: `MediaItem.lastModified` is stat'd on
 * construction, so distinct timestamps need real files. It is checked on the
 * integration layer instead (`FolderViewModelTest`, Phase 3).
 */
private fun folderItem(name: String) = MediaItem.Folder(File("mapper-fixture", name))

private fun imageItem(name: String) = MediaItem.Image(File("mapper-fixture", name))

private fun videoItem(name: String) = MediaItem.Video(File("mapper-fixture", name))

private fun settings(
    sort: SortType = SortType.NameAsc,
    mediaFilter: String = "All",
    showHiddenFiles: Boolean = false
) = FolderSettings(
    layout = ViewLayout.Grid,
    sort = sort,
    showTitles = true,
    showFolders = true,
    showHiddenFiles = showHiddenFiles,
    mediaFilter = mediaFilter
)

class FolderStateMapperTest {

    private val items = listOf(
        imageItem("banana.png"),
        folderItem("Zulu"),
        videoItem("Apple.mkv"),
        folderItem("alpha"),
        imageItem("cherry.jpg")
    )

    private fun namesFor(sort: SortType, source: List<MediaItem> = items) =
        buildFolderState(source, settings(sort), false, emptySet()).items.map { it.name }

    @Test
    fun `name ascending puts folders first and ignores case`() {
        assertEquals(
            listOf("alpha", "Zulu", "Apple.mkv", "banana.png", "cherry.jpg"),
            namesFor(SortType.NameAsc)
        )
    }

    @Test
    fun `name descending reverses the files but keeps folders on top`() {
        assertEquals(
            listOf("Zulu", "alpha", "cherry.jpg", "banana.png", "Apple.mkv"),
            namesFor(SortType.NameDesc)
        )
    }

    @Test
    fun `custom falls back to name ascending`() {
        assertEquals(namesFor(SortType.NameAsc), namesFor(SortType.Custom))
    }

    @Test
    fun `date sorting still holds folders above files`() {
        listOf(SortType.DateAsc, SortType.DateDesc).forEach { sort ->
            val names = namesFor(sort)
            assertEquals(setOf("alpha", "Zulu"), names.take(2).toSet(), "$sort")
        }
    }

    @Test
    fun `equal keys keep the listing order`() {
        val equal = listOf(imageItem("a.png"), imageItem("a.png"), imageItem("a.png"))
        assertEquals(3, namesFor(SortType.DateAsc, equal).size)
        assertEquals(namesFor(SortType.DateAsc, equal), namesFor(SortType.DateDesc, equal))
    }

    @Test
    fun `an empty listing gives an empty state, not a crash`() {
        SortType.entries.forEach { sort ->
            assertTrue(buildFolderState(emptyList(), settings(sort), false, emptySet()).items.isEmpty())
        }
    }

    @Test
    fun `the photo filter keeps folders and images`() {
        val state = buildFolderState(items, settings(mediaFilter = "Photos"), false, emptySet())
        assertEquals(listOf("alpha", "Zulu", "banana.png", "cherry.jpg"), state.items.map { it.name })
    }

    @Test
    fun `the video filter keeps folders and videos`() {
        val state = buildFolderState(items, settings(mediaFilter = "Videos"), false, emptySet())
        assertEquals(listOf("alpha", "Zulu", "Apple.mkv"), state.items.map { it.name })
    }

    @Test
    fun `the settings values are carried into the state verbatim`() {
        val state = buildFolderState(items, settings(SortType.NameDesc, "Videos"), true, setOf("x"))
        assertEquals(SortType.NameDesc, state.sort)
        assertEquals(ViewLayout.Grid, state.layout)
        assertEquals("Videos", state.mediaFilter)
        assertTrue(state.isSelectMode)
    }

    @Test
    fun `the selection survives a re-sort unchanged`() {
        val selection = setOf("mapper-fixture/banana.png", "mapper-fixture/Apple.mkv")
        val ascending = buildFolderState(items, settings(SortType.NameAsc), true, selection)
        val descending = buildFolderState(items, settings(SortType.NameDesc), true, selection)
        assertEquals(selection, ascending.selectedPaths)
        assertEquals(selection, descending.selectedPaths)
    }

    @Test
    fun `the per-folder overrides win over the global ones`() {
        val global = WokaSettings(
            showTitles = true,
            folderLayouts = mapOf("D:/Photos" to ViewLayout.BigGrid),
            folderSorts = mapOf("D:/Photos" to SortType.DateDesc),
            folderShowTitles = mapOf("D:/Photos" to false)
        )
        val mine = global.folderSettings("D:/Photos")
        assertEquals(ViewLayout.BigGrid, mine.layout)
        assertEquals(SortType.DateDesc, mine.sort)
        assertEquals(false, mine.showTitles)
        val other = global.folderSettings("D:/Films")
        assertEquals(ViewLayout.Grid, other.layout)
        assertEquals(SortType.NameAsc, other.sort)
        assertEquals(true, other.showTitles)
    }
}
