package com.woka.support

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
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
 * A second press on the rail item, which closes the open tool — after which the shell
 * mounts Markup again in select-only mode, on a document that holds a mark.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.closeMarkupTool() {
    onNodeWithTag("editor-tool-markup").performClick()
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
    onNodeWithTag("editor-tool-markup").performClick()
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
