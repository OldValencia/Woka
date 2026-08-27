package com.woka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.settings.SettingsOverlay
import com.woka.support.onUiWithSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real `SettingsOverlay` over a real `SettingsRepositoryImpl` on a throwaway
 * root — scrim, card, sidebar and the Appearance panel, composed exactly as `App`
 * composes them.
 *
 * **No `testTag` was added.** Every anchor this file needs already exists in the
 * tree: the categories and the segments render their labels as text, and
 * `CloseIconButton` carries `contentDescription = "Close settings"`.
 *
 * One thing worth inheriting: `Modifier.clickable` **merges descendant
 * semantics**, so a label reached through the default merged tree *is* the
 * clickable box that draws it — `onNodeWithText("Dark").performClick()` hits the
 * segment, while `onParent()` climbs past it to the card, whose clickable only
 * absorbs. The merge stops at the next merging node down, which is why the scrim
 * and the card do not swallow every label into one ambiguous match.
 *
 * The "a toggle writes its value" half of this scenario is `SettingsToggleUiTest`;
 * it is not repeated here, where the two switches of the Library panel share one
 * semantics parent and could only be told apart by index.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsOverlayUiTest {

    private fun openOverlay(
        repository: SettingsRepositoryImpl,
        onClosed: () -> Unit = {}
    ): @Composable () -> Unit = {
        var isOpen by remember { mutableStateOf(true) }
        SettingsOverlay(
            isOpen = isOpen,
            onClose = {
                isOpen = false
                onClosed()
            },
            repo = repository
        )
    }

    @Test
    fun `a closed overlay composes none of its content`() = onUiWithSettings { repository ->
        setContent { SettingsOverlay(isOpen = false, onClose = {}, repo = repository) }
        onNodeWithText("Settings").assertDoesNotExist()
        onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test
    fun `an open overlay renders its title and every category`() = onUiWithSettings { repository ->
        setContent(openOverlay(repository))
        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Appearance").assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
        onNodeWithText("Media Viewer").assertIsDisplayed()
        onNodeWithText("Video Player").assertIsDisplayed()
        onNodeWithText("Editor").assertIsDisplayed()
        onNodeWithText("PiP").assertIsDisplayed()
    }

    @Test
    fun `picking a theme reaches the repository`() = onUiWithSettings { repository ->
        assertEquals("System", repository.settings.value.theme, "the fixture assumed the default")
        setContent(openOverlay(repository))
        onNodeWithText("Dark").assertHasClickAction().performClick()
        waitForIdle()
        assertEquals("Dark", repository.settings.value.theme, "the click never reached the repository")
    }

    @Test
    fun `the segmented control holds no state of its own`() = onUiWithSettings { repository ->
        setContent(openOverlay(repository))
        onNodeWithText("Dark").performClick()
        waitForIdle()
        onNodeWithText("Light").performClick()
        waitForIdle()
        assertEquals("Light", repository.settings.value.theme, "the second pick did not replace the first")
    }

    @Test
    fun `selecting a category swaps the panel`() = onUiWithSettings { repository ->
        setContent(openOverlay(repository))
        onNodeWithText("System").assertExists()
        onNodeWithText("Library").performClick()
        waitForIdle()
        onNodeWithText("Show Item Titles").assertIsDisplayed()
        onNodeWithText("System").assertDoesNotExist()

        // The sidebar label is short because the column is 140.dp; the panel is
        // what carries the full name, and that pairing is the thing to hold.
        // `SectionLabel` upper-cases what it is given, so that is what is on screen.
        onNodeWithText("PiP").performClick()
        waitForIdle()
        onNodeWithText("PICTURE IN PICTURE").assertIsDisplayed()
        onNodeWithText("Always on Top").assertIsDisplayed()
        onNodeWithText("Show Item Titles").assertDoesNotExist()
    }

    @Test
    fun `the close button closes the overlay`() = onUiWithSettings { repository ->
        var closed = false
        setContent(openOverlay(repository) { closed = true })
        onNodeWithContentDescription("Close settings").performClick()
        waitForIdle()
        assertTrue(closed, "onClose was never called")
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `clicking the scrim beside the card closes the overlay`() = onUiWithSettings { repository ->
        var closed = false
        setContent(openOverlay(repository) { closed = true })
        // The card is 560x420 and centred, so the top-left corner is scrim.
        onRoot().performMouseInput { click(Offset(4f, 4f)) }
        waitForIdle()
        assertTrue(closed, "the scrim did not absorb the click into onClose")
    }

    @Test
    fun `the chosen category survives a close and reopen`() = onUiWithSettings { repository ->
        setContent {
            var isOpen by remember { mutableStateOf(true) }
            // Reopened from outside, the way App does it, so nothing in this test
            // holds the category either: it is remembered above the
            // AnimatedVisibility, which is the behaviour under test.
            BasicText(text = "reopen", modifier = Modifier.clickable { isOpen = true })
            SettingsOverlay(isOpen = isOpen, onClose = { isOpen = false }, repo = repository)
        }
        onNodeWithText("Library").performClick()
        waitForIdle()
        onNodeWithContentDescription("Close settings").performClick()
        waitForIdle()
        onNodeWithText("Show Item Titles").assertDoesNotExist()

        onNodeWithText("reopen").performClick()
        waitForIdle()
        onNodeWithText("Show Item Titles").assertExists()
    }
}
