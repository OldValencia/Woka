package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import com.woka.app.shell.StatusBanners
import com.woka.core.utils.FrameExportStatus
import com.woka.feature.editor.EditorSaveStatus
import com.woka.support.onUi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val EXPORTING = "Exporting clip.mp4 — 3 of 96"
private const val SAVING = "Saving photo.jpg…"

/**
 * Two producers, one corner of the window (ME-130).
 *
 * Export frames from the grid, then edit a photograph and press `Done`, and both have
 * something to say at the same time. While each banner aligned *itself* to the bottom
 * centre, that was one pill drawn on top of the other.
 */
@OptIn(ExperimentalTestApi::class)
class StatusBannersUiTest {

    @BeforeTest
    fun reset() {
        FrameExportStatus.clear()
        EditorSaveStatus.clear()
    }

    @Test
    fun `an export and a save are both readable, one above the other`() {
        onUi {
            setContent { StatusBanners() }
            FrameExportStatus.progress("clip.mp4", 3, 96)
            EditorSaveStatus.saving("photo.jpg")
            waitForIdle()
            // Past the slide-in, so the bounds compared below are where the pills rest.
            mainClock.advanceTimeBy(1000)

            onNodeWithText(EXPORTING).assertIsDisplayed()
            onNodeWithText(SAVING).assertIsDisplayed()

            val export = onNodeWithText(EXPORTING).getUnclippedBoundsInRoot()
            val save = onNodeWithText(SAVING).getUnclippedBoundsInRoot()
            assertTrue(export.bottom <= save.top, "the pills overlap: $export against $save")
        }
    }
}
