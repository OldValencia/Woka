package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.core.ParamPoint
import com.woka.feature.editor.shell.paramsOf
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.feature.editor.tools.filter.PhotoUndertone
import com.woka.support.MainDispatcherRule
import com.woka.support.ParamPanel
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Styles' well (RD-203): both strengths and the look's shape as scrub rows. The shape is one
 * stored point drawn as two rows, Vividness and Brightness, each moving only its own axis.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class StyleWellUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** At Neutral and Original there is nothing to scale or shape, so no well (the author). */
    @Test
    fun `each strength appears with what it acts on`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent { ParamPanel(FilterTool, harness) }
                listOf("palette", "intensity", "tone-x", "tone-y").forEach {
                    onNodeWithTag("param-$it").assertDoesNotExist()
                }

                onNodeWithTag("param-undertone-${PhotoUndertone.Amber.name}").performClick()
                waitForIdle()
                onNodeWithTag("param-palette").assertIsDisplayed()
                onNodeWithTag("param-intensity").assertDoesNotExist()

                onNodeWithTag("param-filter-${PhotoFilter.Vivid.name}").performClick()
                waitForIdle()
                listOf("intensity", "tone-x", "tone-y").forEach { onNodeWithTag("param-$it").assertIsDisplayed() }
            }
        }
    }

    @Test
    fun `a pad axis row moves its own axis and leaves the other`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent { ParamPanel(FilterTool, harness) }
                onNodeWithTag("param-filter-${PhotoFilter.Vivid.name}").performClick()
                waitForIdle()
                // Vividness runs -100..100, so three quarters across is 50.
                onNodeWithTag("param-tone-x").performMouseInput { click(Offset(width * 0.75f, centerY)) }
                waitForIdle()

                val tone = paramsOf(harness.session.document.value, FilterTool)[FilterTool.Tone]
                assertEquals(50f, tone.x, 2f)
                assertEquals(ParamPoint(0f, 0f).y, tone.y)
            }
        }
    }
}
