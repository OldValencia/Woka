package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import com.woka.support.toggleControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How many controls a panel puts on a line — **measured, not read off the arithmetic**.
 *
 * Its own file because it is a different kind of claim from `MarkupBarUiTest`'s: that one
 * asks which controls exist, this one asks where they land. Both panels were reported wrong
 * on screen after being called right in a report (2026-08-29): the card's width was worked
 * out against a 20 dp padding when `EditorTokens.Inspector.padding` is 14 a side, so a
 * palette meant to hold four circles held three and a chip row meant to hold two held one.
 * Nothing but a measurement catches that.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupPanelLinesUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * **Measured, not asserted by eye.** Both panels were reported wrong on screen after
     * being called right in a report: the card's width was worked out against a 20 dp
     * padding when `EditorTokens.Inspector.padding` is 14 a side, so a palette meant to
     * hold four circles held three and a chip row meant to hold two held one.
     *
     * A row is "the same line" if two controls share a top, and the next one does not.
     * That is the only claim about a `FlowRow` worth making, and it is the one no reading
     * of the arithmetic can substitute for.
     */
    @Test
    fun `the palette lays four to a line and the faces two`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")

                toggleControl(MarkupTool.Color.key)
                assertLines("param-color-swatch-", perLine = 4)
                toggleControl(MarkupTool.Color.key)

                toggleControl(MarkupTool.Fill.key)
                assertLines("param-fill-swatch-", perLine = 4)
                toggleControl(MarkupTool.Fill.key)

                toggleControl(MarkupTool.Faces.key)
                val faces = MarkupTool.Faces.options.map { "param-faces-${it.name}" }
                assertEquals(topOf(faces[0]), topOf(faces[1]), 0.5f, "the second chip wrapped")
                assertTrue(topOf(faces[2]) > topOf(faces[0]), "the third chip stayed on line one")
            }
        }
    }

    /** Where a node's top edge is, which is what says two of them share a line. */
    private fun ComposeUiTest.topOf(tag: String) =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    /** The first [perLine] swatches share a top, and the one after them does not. */
    private fun ComposeUiTest.assertLines(prefix: String, perLine: Int) {
        val first = topOf("${prefix}0")
        repeat(perLine - 1) { index ->
            assertEquals(
                first,
                topOf("$prefix${index + 1}"),
                0.5f,
                "swatch ${index + 1} wrapped before the line was full"
            )
        }
        assertTrue(
            topOf("$prefix$perLine") > first,
            "swatch $perLine stayed on a line meant to hold $perLine"
        )
    }
}
