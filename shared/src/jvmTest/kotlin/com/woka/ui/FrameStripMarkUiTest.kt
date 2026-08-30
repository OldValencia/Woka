package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.frames.EDITED_DOT_TAG
import com.woka.core.ui.components.frames.FrameStrip
import com.woka.support.onUi
import java.io.File
import kotlin.test.Test

/**
 * The mark that says a frame carries an edit of its own (ME-157).
 *
 * Driven against the component directly rather than through the viewer, because what
 * is being asserted is the component's contract — a defaulted parameter that draws
 * nothing — and the viewer's own wiring of it is `ViewerInvalidationUiTest`'s.
 *
 * Its own file rather than four more tests in `FrameStripUiTest`, which is at 147
 * lines and would have gone past the size mandate.
 */
@OptIn(ExperimentalTestApi::class)
class FrameStripMarkUiTest {

    private fun burst(count: Int) = (0 until count).map {
        ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence)
    }

    private fun strip(edited: Set<Int>, block: ComposeUiTest.() -> Unit) =
        onUi {
            setContent {
                FrameStrip(
                    item = MediaItem.Image(File("burst.heic")),
                    frames = burst(5),
                    selectedIndex = 0,
                    loadPreview = { _, _ -> null },
                    onSelectFrame = {},
                    editedFrames = edited
                )
            }
            waitForIdle()
            block()
        }

    @Test
    fun `a frame with its own edit is marked, and no other is`() =
        strip(setOf(2)) { onAllNodesWithTag(EDITED_DOT_TAG).assertCountEquals(1) }

    /** A caller with nothing to say draws exactly the strip it drew before ME-157. */
    @Test
    fun `the default marks nothing at all`() =
        strip(emptySet()) { onAllNodesWithTag(EDITED_DOT_TAG).assertCountEquals(0) }
}
