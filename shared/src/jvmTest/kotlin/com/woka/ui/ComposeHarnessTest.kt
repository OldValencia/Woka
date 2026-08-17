package com.woka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The Phase 4 feasibility gate, not a product test.**
 *
 * The plan records one open question about the UI-automation layer: whether
 * `runComposeUiTest` comes up headless on Windows at all. Everything Phase 4
 * needs is exercised here on composables this file owns — no production code is
 * touched, and in particular no `testTag` is added to the tree, because that is a
 * production change awaiting its own approval.
 *
 * Each mechanism is its own test so a failure says *which* one is unavailable
 * rather than just "the layer does not work".
 *
 * The entry point is **`androidx.compose.ui.test.v2.runComposeUiTest`**. The v1
 * one of the same name is deprecated: v2 drives the composition on a
 * `StandardTestDispatcher`, so coroutines queue the way they do in production
 * instead of running eagerly. Every check below passes unchanged under it — no
 * extra `waitForIdle` was needed — but Phase 4 should keep to v2 so its scenarios
 * are not written against eager execution the app never has.
 */
@OptIn(ExperimentalTestApi::class)
class ComposeHarnessTest {

    @Test
    fun `the compose runtime comes up and composes`() {
        runComposeUiTest {
            setContent { BasicText("woka-gate") }
            onNodeWithText("woka-gate").assertExists()
        }
    }

    @Test
    fun `a node is laid out and actually displayed`() {
        runComposeUiTest {
            setContent {
                Box(Modifier.size(120.dp).testTag("box")) { BasicText("inside") }
            }
            onNodeWithTag("box").assertIsDisplayed()
        }
    }

    @Test
    fun `a test tag is a usable anchor`() {
        runComposeUiTest {
            setContent { BasicText("tagged", Modifier.testTag("woka-anchor")) }
            onNodeWithTag("woka-anchor").assertExists()
        }
    }

    @Test
    fun `a click reaches state and recomposes`() {
        runComposeUiTest {
            setContent {
                var clicks by remember { mutableStateOf(0) }
                BasicText(
                    text = "clicks=$clicks",
                    modifier = Modifier.testTag("target").clickable { clicks++ }
                )
            }
            onNodeWithTag("target").performClick()
            onNodeWithText("clicks=1").assertExists()
        }
    }

    @Test
    fun `a key event reaches a focused node`() {
        runComposeUiTest {
            val focus = FocusRequester()
            var pressed = ""
            setContent {
                Box(
                    Modifier
                        .size(80.dp)
                        .testTag("keys")
                        .focusRequester(focus)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) pressed = event.key.toString()
                            true
                        }
                )
            }
            focus.requestFocus()
            onNodeWithTag("keys").performKeyInput { pressKey(Key.DirectionRight) }
            assertEquals(Key.DirectionRight.toString(), pressed)
        }
    }

    @Test
    fun `mouse input is delivered`() {
        runComposeUiTest {
            var clicked = false
            setContent {
                Box(Modifier.size(80.dp).testTag("mouse").clickable { clicked = true })
            }
            onNodeWithTag("mouse").performMouseInput { click() }
            assertEquals(true, clicked)
        }
    }
}
