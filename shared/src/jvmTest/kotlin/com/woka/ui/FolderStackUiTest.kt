package com.woka.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.FOLDER_STACK_TAG
import com.woka.core.ui.components.FolderCard
import com.woka.support.onUi
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * The mark that says "folder", now that a folder draws a photograph like everything
 * else. Sibling of [FolderCoverUiTest], which covers the glyph this replaced.
 *
 * Split from it under the §2 file-size mandate: the glyph is about what a card draws
 * when it has *no* cover, and the stack is about what a card draws when it has one.
 */
@OptIn(ExperimentalTestApi::class)
class FolderStackUiTest {

    private fun directory(): File = Files.createTempDirectory("woka-stack").toFile()

    /**
     * The mark that replaced the glyph.
     *
     * Borrowing a cover from one level down fixed the blank tile and took away the
     * only thing on the card that said "folder" — the glyph, which by design leaves
     * the moment a picture arrives (`FolderCoverUiTest`, last case). The stack of
     * sheets is what says it now, and unlike the glyph it does **not** depend on the
     * cover: that independence is the whole point, so the fixture here is a folder
     * that has a picture in it.
     *
     * Driven through [FolderCard] rather than a part of it, because "present on a
     * folder and absent on a picture" is a claim about the branch in `FolderCardBody`,
     * and a test that called the stack directly would only prove the stack draws.
     *
     * **`useUnmergedTree` is not optional here.** The card's outer `Box` is clickable,
     * which merges its descendants' semantics into itself, so the tag is reachable
     * only in the unmerged tree. Without it the folder case fails outright and — far
     * worse — the picture case below passes for the wrong reason: it would be reading
     * the merge, not the absence.
     */
    @Test
    fun `a folder card carries the stack`() = onUi {
        val folder = directory()
        File(folder, "cover.jpg").writeBytes(ByteArray(8))
        setContent {
            FolderCard(
                path = folder.absolutePath,
                onClick = {},
                modifier = Modifier.size(240.dp, 170.dp)
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_STACK_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    /** And an ordinary picture card comes out of this untouched. */
    @Test
    fun `a picture card carries no stack`() = onUi {
        val picture = File(directory(), "holiday.jpg").apply { writeBytes(ByteArray(8)) }
        setContent {
            FolderCard(
                path = picture.absolutePath,
                onClick = {},
                modifier = Modifier.size(240.dp, 170.dp)
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_STACK_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
