package com.woka.support

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.markupShapes
import kotlin.test.assertIs

/**
 * Getting to "a mark on the picture and no tool open" (ME-149) — the state both idle
 * tests start from, and a sequence neither can reach through `openMarkupTool`, which
 * composes the screen without the keyboard slot `Delete` travels through.
 *
 * Beside `MarkupUi` rather than inside it for the size mandate.
 */

/**
 * A second tab beside Markup, so Markup can be left (RD-201): one tab is always open, and
 * closing a tool now means moving to another one. First in the tabs, so the editor also
 * opens on it with Markup idle.
 */
internal val OtherTab = FixtureTool(id = "adjust", domain = ToolDomain.Color)

/**
 * Moving to [OtherTab], which closes Markup — after which the shell mounts it again in
 * select-only mode, on a document that holds a mark.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.closeMarkupTool() {
    onNodeWithTag("editor-tab-adjust").performClick()
    waitForIdle()
}

/** The editor, Markup opened, one arrow drawn — and the arrow it left. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.drawArrow(
    harness: EditorHarness,
    canvasKeys: CanvasKeys
): MarkupShape.Arrow {
    setContent {
        EditorScreen(
            session = harness.session, engine = FixtureEngine(),
            registry = harness.registry, onCancel = {}, onDone = {},
            canvasKeys = canvasKeys
        )
    }
    onNodeWithTag("editor-tab-markup").performClick()
    waitForIdle()

    onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
        moveTo(Offset(40f, 40f))
        press()
        moveTo(Offset(150f, 120f))
        release()
    }
    waitForIdle()
    return assertIs(harness.session.document.value.markupShapes().single())
}
