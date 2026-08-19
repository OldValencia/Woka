package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo
import com.woka.core.data.WokaSettings
import com.woka.core.ui.image.MediaInvalidation
import com.woka.feature.viewer.shell.MediaViewerActiveItemEffects
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.support.onViewer
import com.woka.support.poster
import kotlin.test.Test
import kotlin.test.assertEquals

/** Records which files it was asked to decode, in call order. */
private class CountingThumbnails : ViewerThumbnails {
    val loads = mutableListOf<String>()
    override suspend fun full(item: MediaItem, frameIndex: Int) =
        poster(8, 8).also { loads += item.file.absolutePath }

    override suspend fun preview(item: MediaItem, frameIndex: Int) = null
    override suspend fun sourceSize(item: MediaItem) = IntSize.Zero
    override suspend fun stillColor(item: MediaItem) = StillColorInfo.Sdr
    override fun cachedFull(item: MediaItem, frameIndex: Int) = null
}

/**
 * The viewer notices a save without being remounted (ME-135).
 *
 * Driven against the decode effect itself rather than the whole player: the defect was
 * a key list, and this is the composable that owns it. Found by hand — `Done` from the
 * viewer changed nothing until the viewer was left and re-entered — so the assertion is
 * "it decoded again", counted, not eyeballed.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerInvalidationUiTest {

    @Test
    fun `the open file is re-read when it is invalidated and not when another is`() =
        onViewer(2) { items ->
            val state = MediaViewerState(items[0].file.absolutePath)
            val thumbnails = CountingThumbnails()
            setContent {
                MediaViewerActiveItemEffects(
                    state = state,
                    activeItem = items[0],
                    settings = WokaSettings(),
                    showConflictDialog = false,
                    onConflictChange = {},
                    onClose = {},
                    thumbnails = thumbnails
                )
            }
            waitForIdle()
            assertEquals(1, thumbnails.loads.size, "the picture was never decoded")

            MediaInvalidation.invalidate(items[1].file.absolutePath)
            waitForIdle()
            assertEquals(1, thumbnails.loads.size, "another file's save re-decoded this one")

            MediaInvalidation.invalidate(items[0].file.absolutePath)
            waitForIdle()
            assertEquals(2, thumbnails.loads.size, "the save never reached the viewer")
        }
}
