package com.woka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.settings.EditorToolSettings
import com.woka.support.FixtureTool
import com.woka.support.onUiWithSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings → Editor over a real `SettingsRepositoryImpl` on a throwaway root
 * (ME-115).
 *
 * Composed against **fixture tools**, not the shipped ones: the panel's claim is
 * "one row per declared tool, titled from its manifest", and asserting it on the
 * real registry would rewrite this file every time a tool ships.
 *
 * `EditorToolSettings` rather than `EditorSettingsPanel` for the reason
 * `PipSettingsPanel`'s KDoc already records — a panel that resolves Koin itself
 * cannot be composed over a throwaway repository. The `actual` above it is four
 * lines of wiring and nothing this file could fail on.
 *
 * The switches have no `toggleable` semantics — `SliderSwitch` is a `clickable` box —
 * so "the switch is off" is asserted the only way that can actually fail: the row
 * is seeded from the setting and the click is shown to move it in the direction
 * only a row reading that setting would move it.
 */
@OptIn(ExperimentalTestApi::class)
class EditorSettingsPanelUiTest {

    private fun registryOf(repository: SettingsRepositoryImpl) =
        EditorToolRegistry(
            listOf(FixtureTool(id = "alpha", order = 0), FixtureTool(id = "beta", order = 1)),
            repository
        )

    private fun panelOf(repository: SettingsRepositoryImpl): @Composable () -> Unit {
        val tools = registryOf(repository).all()
        return {
            val settings by repository.settings.collectAsState()
            EditorToolSettings(
                tools = tools,
                disabled = settings.editorDisabledTools,
                onToolEnabled = repository::setEditorToolEnabled
            )
        }
    }

    @Test
    fun `every declared tool gets a row titled from its manifest`() = onUiWithSettings { repository ->
        setContent(panelOf(repository))
        onNodeWithText("TOOLS").assertIsDisplayed()
        onNodeWithText("alpha").assertIsDisplayed()
        onNodeWithText("beta").assertIsDisplayed()
        onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun `switching a tool off writes its id, and switching it back removes it`() =
        onUiWithSettings { repository ->
            setContent(panelOf(repository))
            onAllNodes(hasClickAction())[0].performClick()
            waitForIdle()
            assertEquals(
                setOf("alpha"),
                repository.settings.value.editorDisabledTools,
                "the click never reached the repository"
            )

            onAllNodes(hasClickAction())[0].performClick()
            waitForIdle()
            assertTrue(
                repository.settings.value.editorDisabledTools.isEmpty(),
                "the second click did not re-enable the tool"
            )
        }

    @Test
    fun `a tool already disabled composes with its switch off`() = onUiWithSettings { repository ->
        repository.setEditorToolEnabled("beta", false)
        setContent(panelOf(repository))

        onAllNodes(hasClickAction())[1].performClick()
        waitForIdle()
        // A switch remembering its own state would have started "on" and written
        // `beta` a second time; reading the setting is what makes this a removal.
        assertFalse(
            "beta" in repository.settings.value.editorDisabledTools,
            "the row held its own state instead of the setting"
        )
    }

    @Test
    fun `switching one tool leaves its neighbour alone`() = onUiWithSettings { repository ->
        setContent(panelOf(repository))
        onAllNodes(hasClickAction())[1].performClick()
        waitForIdle()
        assertEquals(setOf("beta"), repository.settings.value.editorDisabledTools)
    }
}
