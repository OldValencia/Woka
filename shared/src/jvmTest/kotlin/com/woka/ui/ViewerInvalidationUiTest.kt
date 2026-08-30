package com.woka.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo
import com.woka.core.data.WokaSettings
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.MediaInvalidation
import com.woka.core.ui.image.StillEdits
import com.woka.feature.viewer.shell.MediaViewerActiveItemEffects
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.core.ui.components.frames.EDITED_DOT_TAG
import com.woka.core.ui.components.frames.FrameStrip
import com.woka.core.ui.components.frames.rememberEditedFrames
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.support.onViewer
import com.woka.support.poster
import java.io.File
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

/** A seam whose answer the test moves, so what is under test is the *signal*. */
private class MovingMarks : StillEdits {
    var frames: Set<Int> = emptySet()
    override suspend fun carries(file: File) = frames.isNotEmpty()
    override fun apply(file: File, frameIndex: Int, decoded: ImageBitmap) = decoded
    override suspend fun editedFrames(file: File) = frames
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

    /**
     * The strip had the same defect one layer over (2026-08-19, by hand on a burst
     * HEIC): its thumbnails are keyed on the path and the frame, and a save moves
     * neither, so **all** of them re-read.
     */
    @Test
    fun `every frame thumbnail re-reads when the file is invalidated`() = onViewer(1) { items ->
        val loads = mutableListOf<Int>()
        setContent {
            FrameStrip(
                item = items[0],
                frames = listOf(ImageFrame(0, FrameKind.Primary), ImageFrame(1, FrameKind.Sequence)),
                selectedIndex = 0,
                loadPreview = { _, index -> poster(8, 8).also { loads += index } },
                onSelectFrame = {}
            )
        }
        waitForIdle()
        assertEquals(2, loads.size, "both frames were never drawn")

        MediaInvalidation.invalidate(items[0].file.absolutePath)
        waitForIdle()

        assertEquals(4, loads.size, "a frame kept the picture from before the edit")
    }

    /** And the mark rides the same signal (ME-157), without a remount. */
    @Test
    fun `a keyed write makes the mark appear without remounting the strip`() =
        onViewer(1) { items ->
            val marks = MovingMarks()
            AppliedStillEdits.install(marks)
            try {
                setContent {
                    FrameStrip(
                        item = items[0],
                        frames = listOf(
                            ImageFrame(0, FrameKind.Primary),
                            ImageFrame(1, FrameKind.Sequence)
                        ),
                        selectedIndex = 0,
                        loadPreview = { _, _ -> poster(8, 8) },
                        onSelectFrame = {},
                        editedFrames = rememberEditedFrames(items[0].file.absolutePath)
                    )
                }
                waitForIdle()
                onAllNodesWithTag(EDITED_DOT_TAG).assertCountEquals(0)

                marks.frames = setOf(1)
                MediaInvalidation.invalidate(items[0].file.absolutePath)
                waitForIdle()

                onAllNodesWithTag(EDITED_DOT_TAG).assertCountEquals(1)
            } finally {
                // The holder is process-wide; an inert one is what the rest of the
                // suite expects to find.
                AppliedStillEdits.install(MovingMarks())
            }
        }
}
