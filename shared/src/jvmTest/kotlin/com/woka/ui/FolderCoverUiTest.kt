package com.woka.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.FOLDER_GLYPH_TAG
import com.woka.core.ui.components.FolderCardPreview
import com.woka.support.onUi
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * What a folder card draws when it has no cover to draw.
 *
 * Written as the instrument the bug ticket asked for rather than as a regression
 * guard: a linked folder holding only subfolders was reported as a **black tile with
 * nothing on it** (2026-08-30), and `FolderCardPreview` reads as though the folder
 * glyph is unmissable there. This is the cheapest way to find out which of the two is
 * true without running the application.
 */
@OptIn(ExperimentalTestApi::class)
class FolderCoverUiTest {

    private fun directory(): File = Files.createTempDirectory("woka-cover").toFile()

    @Test
    fun `a folder with no preview draws its glyph`() = onUi {
        val folder = directory()
        setContent {
            FolderCardPreview(
                isDir = true,
                file = folder,
                previews = emptyList(),
                modifier = Modifier.size(240.dp, 150.dp)
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_GLYPH_TAG).assertIsDisplayed()
    }

    /**
     * The unhovered card is the one that was reported as blank, so it is the state
     * worth pinning: at rest the glyph is still in the tree and still laid out.
     *
     * **That it is drawn *above* the rest-state dimming is not asserted here** — a
     * layer order is not something the semantics tree exposes, and a test named for
     * it would be claiming more than it checks. The argument lives beside the code.
     */
    @Test
    fun `the glyph is there on a card the cursor is not on`() = onUi {
        val folder = directory()
        setContent {
            FolderCardPreview(
                isDir = true,
                file = folder,
                previews = emptyList(),
                modifier = Modifier.size(240.dp, 150.dp),
                isHovered = false
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_GLYPH_TAG).assertIsDisplayed()
    }

    /**
     * The tile reported on a screenshot 2026-08-30: "Pictures · 7 items", black, with
     * no glyph on it either. A folder *with* a cover never reached the glyph branch,
     * and `StillCoverImage` draws nothing at all for a Woka-road file whose decode
     * answers null — so an undecodable cover left the card blank.
     *
     * A `.heic` of eight junk bytes takes that road (`needsWokaCover` answers from the
     * extension) and fails on it, which is the case exactly.
     */
    @Test
    fun `a cover that cannot be decoded falls back to the glyph`() = onUi {
        val folder = directory()
        val broken = File(folder, "cover.heic").apply { writeBytes(ByteArray(8)) }
        setContent {
            FolderCardPreview(
                isDir = true,
                file = folder,
                previews = listOf(broken),
                modifier = Modifier.size(240.dp, 150.dp)
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_GLYPH_TAG).assertIsDisplayed()
    }

    /** And gets out of the way the moment there is a picture to show instead. */
    @Test
    fun `a folder with a preview draws no glyph`() = onUi {
        val folder = directory()
        val picture = File(folder, "cover.jpg").apply { writeBytes(ByteArray(8)) }
        setContent {
            FolderCardPreview(
                isDir = true,
                file = folder,
                previews = listOf(picture),
                modifier = Modifier.size(240.dp, 150.dp)
            )
        }
        waitForIdle()

        onNodeWithTag(FOLDER_GLYPH_TAG).assertDoesNotExist()
    }
}
