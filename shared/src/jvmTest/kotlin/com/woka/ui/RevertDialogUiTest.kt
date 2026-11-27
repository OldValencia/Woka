package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.shell.RevertDialog
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Revert to original?` in the prototype's frame (RD-401, `#dialogs/revert`): what goes, that
 * it cannot come back, and two full-width answers.
 */
@OptIn(ExperimentalTestApi::class)
class RevertDialogUiTest {

    @Test
    fun `a photo's revert says what goes and that it cannot be undone`() = onUi {
        var reverted = 0
        var kept = 0
        setContent { RevertDialog(onRevert = { reverted++ }, onKeepEditing = { kept++ }) }

        onNodeWithText("Revert to original?").assertIsDisplayed()
        onNodeWithText("All edits to the photo will be reset. This cannot be undone.").assertIsDisplayed()
        onNodeWithTag("editor-revert-cancel").performClick()
        assertEquals(1, kept)
        onNodeWithTag("editor-revert-confirm").performClick()
        assertEquals(1, reverted)
    }

    @Test
    fun `a film's revert names its cuts`() = onUi {
        setContent { RevertDialog(onRevert = {}, onKeepEditing = {}, video = true) }

        onNodeWithText("All edits to the video, including cuts, will be reset. This cannot be undone.")
            .assertIsDisplayed()
        onNodeWithText("Revert to original").assertIsDisplayed()
    }
}
