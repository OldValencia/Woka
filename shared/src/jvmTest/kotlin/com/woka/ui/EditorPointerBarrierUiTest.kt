package com.woka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.swallowPointer
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The editor is one hit target over the viewer it covers (2026-11-27): a click where the
 * editor has no control of its own goes nowhere, and its controls still take theirs.
 */
@OptIn(ExperimentalTestApi::class)
class EditorPointerBarrierUiTest {

    @Test
    fun `a click in the editor's gaps never reaches the viewer, and its buttons still work`() = onUi {
        var viewer = 0
        var button = 0
        setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable { viewer++ }.testTag("viewer"))
                Box(Modifier.fillMaxSize().swallowPointer().testTag("editor")) {
                    Box(Modifier.size(40.dp).align(Alignment.Center).clickable { button++ }.testTag("button"))
                }
            }
        }

        onNodeWithTag("editor").performTouchInput { click(topLeft) }
        onNodeWithTag("button").performClick()
        waitForIdle()

        assertEquals(0, viewer, "a click through the editor reached the viewer")
        assertEquals(1, button, "the barrier took the editor's own click")
    }
}
