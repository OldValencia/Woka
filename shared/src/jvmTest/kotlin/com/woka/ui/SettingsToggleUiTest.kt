package com.woka.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.core.ui.icons.Icons
import com.woka.feature.settings.SettingsToggleRow
import com.woka.support.onUi
import com.woka.support.onUiWithSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first product scenario of the UI layer: a real settings toggle, driven
 * through a real `SettingsRepositoryImpl` on a throwaway root.
 *
 * **No `testTag` is used, and none was added to production.** `SettingsToggleRow`
 * renders its title and subtitle as text, and `SliderSwitch` carries a click action —
 * between them that is a textual and a semantic anchor, which is exactly the case
 * the plan says does *not* warrant a tag. The row is composed on its own, so
 * `hasClickAction()` is unambiguous.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsToggleUiTest {

    @Test
    fun `a toggle renders its title and subtitle`() = onUiWithSettings { repository ->
        setContent {
            val settings by repository.settings.collectAsState()
            SettingsToggleRow(
                title = "Show titles",
                subtitle = "File names under each item",
                checked = settings.showTitles,
                onCheckedChange = { repository.setShowTitles(it) }
            )
        }
        onNodeWithText("Show titles").assertIsDisplayed()
        onNodeWithText("File names under each item").assertIsDisplayed()
    }

    @Test
    fun `clicking the switch writes the value into the repository`() = onUiWithSettings { repository ->
        assertTrue(repository.settings.value.showTitles, "the fixture assumed the shipped default")
        setContent {
            val settings by repository.settings.collectAsState()
            SettingsToggleRow(
                title = "Show titles",
                subtitle = "File names under each item",
                checked = settings.showTitles,
                onCheckedChange = { repository.setShowTitles(it) }
            )
        }
        onNode(hasClickAction()).performClick()
        waitForIdle()
        assertFalse(repository.settings.value.showTitles, "the click never reached the repository")
    }

    @Test
    fun `the switch follows the repository back, so the row holds no state of its own`() =
        onUiWithSettings { repository ->
            setContent {
                val settings by repository.settings.collectAsState()
                SettingsToggleRow(
                    title = "Show titles",
                    subtitle = "File names under each item",
                    checked = settings.showTitles,
                    onCheckedChange = { repository.setShowTitles(it) }
                )
            }
            onNode(hasClickAction()).performClick()
            waitForIdle()
            assertFalse(repository.settings.value.showTitles)

            onNode(hasClickAction()).performClick()
            waitForIdle()
            assertTrue(repository.settings.value.showTitles, "the second click did not toggle back")
        }

    @Test
    fun `a toggle writes its own setting and not a neighbour's`() = onUiWithSettings { repository ->
        setContent {
            val settings by repository.settings.collectAsState()
            SettingsToggleRow(
                title = "Hidden files",
                subtitle = "Show files the system hides",
                checked = settings.showHiddenFiles,
                onCheckedChange = { repository.setShowHiddenFiles(it) }
            )
        }
        assertFalse(repository.settings.value.showHiddenFiles)
        onNode(hasClickAction()).performClick()
        waitForIdle()
        assertTrue(repository.settings.value.showHiddenFiles)
        assertEquals(true, repository.settings.value.showTitles, "an unrelated setting moved")
    }

    /**
     * The icon carries no `contentDescription` — it is decoration beside a title
     * that already says what the row is — so there is no node to look it up by.
     * What *is* observable is the space it takes: 16.dp of glyph and 12.dp of gap
     * push the title 28.dp right, and a row given no icon is laid out exactly where
     * it was. Composed side by side so the assertion is a difference rather than an
     * absolute that would move with the row's padding (ME-115).
     */
    @Test
    fun `an icon indents the title, and a row without one is laid out as before`() = onUi {
        setContent {
            Column {
                SettingsToggleRow("Plain", "no icon", checked = true, onCheckedChange = {})
                SettingsToggleRow(
                    title = "Iconed",
                    subtitle = "with an icon",
                    checked = true,
                    onCheckedChange = {},
                    icon = Icons.Static.Close
                )
            }
        }
        val plain = onNodeWithText("Plain").getUnclippedBoundsInRoot().left
        val iconed = onNodeWithText("Iconed").getUnclippedBoundsInRoot().left
        assertEquals(
            28f,
            (iconed - plain).value,
            0.5f,
            "the icon and its gap did not move the title by 16.dp + 12.dp"
        )
    }
}
