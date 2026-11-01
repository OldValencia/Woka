package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.SortType
import com.woka.core.data.ViewLayout
import com.woka.feature.folder.FolderSettings
import com.woka.feature.folder.buildFolderState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The grid's Music filter (AP-009), beside Photos and Videos in `FolderStateMapperTest`. */
class FolderMusicFilterTest {

    private fun settings(mediaFilter: String) = FolderSettings(
        layout = ViewLayout.Grid,
        sort = SortType.NameAsc,
        showTitles = true,
        showFolders = true,
        showHiddenFiles = false,
        mediaFilter = mediaFilter
    )

    private val items = listOf(
        MediaItem.Audio(File("mapper-fixture", "song.mp3")),
        MediaItem.Image(File("mapper-fixture", "cover.jpg")),
        MediaItem.Folder(File("mapper-fixture", "Album")),
        MediaItem.Video(File("mapper-fixture", "clip.mkv")),
        MediaItem.Audio(File("mapper-fixture", "intro.flac"))
    )

    @Test
    fun `the music filter keeps folders and tracks`() {
        val state = buildFolderState(items, settings("Music"), false, emptySet())
        assertEquals(listOf("Album", "intro.flac", "song.mp3"), state.items.map { it.name })
    }

    @Test
    fun `photos and videos leave tracks out`() {
        val photos = buildFolderState(items, settings("Photos"), false, emptySet()).items.map { it.name }
        val videos = buildFolderState(items, settings("Videos"), false, emptySet()).items.map { it.name }
        assertEquals(listOf("Album", "cover.jpg"), photos)
        assertEquals(listOf("Album", "clip.mkv"), videos)
    }
}
