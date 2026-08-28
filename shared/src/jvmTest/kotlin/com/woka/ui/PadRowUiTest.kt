package com.woka.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ParamPoint
import com.woka.feature.editor.shell.InspectorAccessory
import com.woka.feature.editor.shell.LocalInspectorAccessory
import com.woka.feature.editor.shell.ParamRow
import com.woka.feature.editor.shell.ParamWriter
import com.woka.feature.editor.shell.paramsOf
import com.woka.support.EditorHarness
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two-axis pad (ME-141) — the ninth spec's renderer, behind the indicator that
 * replaced its row on 2026-08-28.
 *
 * **The claim is the gesture's granularity**, which is what made the pad one spec
 * rather than two: a drag writes on every event and lands as exactly one history
 * entry. Asserted against the session's own operations, because that is the number
 * the manifest promises and the number a pair of specs would have doubled.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PadRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val opener = "param-${FixtureParams.Pad.key}-open"
    private val pad = "param-${FixtureParams.Pad.key}"

    /**
     * The indicator and the room beside it, wired as `EditorBottomPanel` wires them:
     * the pad registers itself into an [InspectorAccessory] and the panel — here, this
     * `Row` — is what draws it. Composed without one, the row would write into the
     * inert default and nothing would appear.
     */
    private fun row(harness: EditorHarness): @Composable () -> Unit = {
        val document by harness.session.document.collectAsState()
        val tool = FixtureTool()
        val accessory = remember { InspectorAccessory() }
        CompositionLocalProvider(LocalInspectorAccessory provides accessory) {
            Row(modifier = Modifier.size(400.dp)) {
                ParamRow(
                    FixtureParams.Pad,
                    paramsOf(document, tool),
                    ParamWriter(harness.session, tool)
                )
                accessory.content?.invoke()
            }
        }
    }

    private fun currentPoint(harness: EditorHarness): ParamPoint =
        paramsOf(harness.session.document.value, FixtureTool())[FixtureParams.Pad]

    @Test
    fun `the pad is behind an indicator and opens on a press`() =
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent(row(harness))
                // Closed, the panel carries a 40 dp square and nothing else.
                onNodeWithTag(pad).assertDoesNotExist()

                onNodeWithTag(opener).performClick()
                waitForIdle()
                onNodeWithTag(pad).assertExists()
            }
        }

    @Test
    fun `a drag writes both axes and settles as one operation`() =
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent(row(harness))
                onNodeWithTag(opener).performClick()
                waitForIdle()

                onNodeWithTag(pad).performTouchInput {
                    down(centerLeft)
                    moveTo(center)
                    moveTo(topRight)
                    up()
                }
                waitForIdle()

                val point = currentPoint(harness)
                // Up and to the right: x past the middle, y above it — the inversion
                // the renderer exists to apply, since a pointer's y grows downward.
                assertTrue(point.x > 0f, "the drag did not move x right, landed at ${point.x}")
                assertTrue(point.y > 0f, "dragging upward did not raise y, landed at ${point.y}")
                assertEquals(1, harness.session.document.value.ops.size, "one gesture, one operation")
            }
        }

    @Test
    fun `a double click returns both axes to the default`() =
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent(row(harness))
                onNodeWithTag(opener).performClick()
                waitForIdle()

                onNodeWithTag(pad).performTouchInput {
                    down(centerLeft)
                    moveTo(topRight)
                    up()
                }
                waitForIdle()
                assertTrue(
                    currentPoint(harness) != FixtureParams.Pad.default,
                    "the fixture needs the pad moved before a reset can be shown"
                )

                onNodeWithTag(pad).performMouseInput { doubleClick(Offset(10f, 10f)) }
                waitForIdle()
                assertEquals(FixtureParams.Pad.default, currentPoint(harness))
            }
        }
}
