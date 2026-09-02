package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.core.data.HdrViewMode
import com.woka.feature.viewer.shell.HdrModeMenu
import com.woka.feature.viewer.shell.labelFor
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pill's HDR dropdown (ME-179).
 *
 * `HdrModeMenu` is exercised rather than `HdrModeControl`, and that is a statement about
 * the split: the control resolves a repository and owns the popup, the menu is the part
 * with the choices in it. Testing the menu needs no DI graph, and what the control adds
 * over it — one `collectField` and one setter call — is the wiring the compiler checks.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class HdrModeMenuUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `all three modes are offered, each with what it does`() = onUi {
        setContent { HdrModeMenu(selected = HdrViewMode.Auto) {} }

        onNodeWithTag("hdr-mode-menu").assertIsDisplayed()
        onNodeWithText("Auto").assertIsDisplayed()
        onNodeWithText("HDR").assertIsDisplayed()
        onNodeWithText("SDR").assertIsDisplayed()

        // The captions are the reason the menu is a list and not a segmented control:
        // "HDR" on its own reads as an output setting the app does not have.
        onNodeWithText("Never tone map").assertIsDisplayed()
    }

    @Test
    fun `picking a mode reports it once`() = onUi {
        val picked = mutableListOf<HdrViewMode>()
        setContent { HdrModeMenu(selected = HdrViewMode.Auto) { picked += it } }

        onNodeWithTag("hdr-mode-SDR").performClick()

        assertEquals(listOf(HdrViewMode.ForceSdr), picked)
    }

    @Test
    fun `the anchor names the mode rather than leaving it to guess`() {
        // Not a UI assertion: the label is a pure function and the pill is the only place
        // a user sees which way a picture is being read without opening anything.
        assertEquals("HDR · Auto", labelFor(HdrViewMode.Auto))
        assertEquals("HDR · On", labelFor(HdrViewMode.ForceHdr))
        assertEquals("HDR · Off", labelFor(HdrViewMode.ForceSdr))
    }
}
