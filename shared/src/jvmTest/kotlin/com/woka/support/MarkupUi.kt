package com.woka.support

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MARKUP_TEXT_FIELD_TAG

/**
 * The sequences every markup UI test starts with, in one place (ME-145).
 *
 * Four scenarios now open the tool, choose a pen and place a caret, and each of them was
 * about to carry its own copy. A harness a test *uses*, following `EditorHarness` — and
 * the reason it is here rather than private to one file is the size mandate, which two of
 * those files had already crossed.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openMarkupTool(harness: EditorHarness) {
    setContent {
        EditorScreen(
            session = harness.session, engine = FixtureEngine(),
            registry = harness.registry, onCancel = {}, onDone = {}
        )
    }
    onNodeWithTag("editor-tool-markup").performClick()
    waitForIdle()
}

/**
 * The pen, by the name on its chip. `Text` is the fifth kind, which is what makes the row
 * a chip row rather than a `SegmentedControl` — and what makes this a text lookup.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.chooseMarkupPen(name: String) {
    onNodeWithText(name).performClick()
    waitForIdle()
}

/** A click on the picture — a caret, with the text pen out. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.clickPicture(at: Offset) {
    onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
        moveTo(at)
        press()
        release()
    }
    waitForIdle()
}

/** Types into the open caret without finishing — the field keeps the keyboard. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.typeIntoCaret(text: String) {
    onNodeWithTag(MARKUP_TEXT_FIELD_TAG).performTextInput(text)
    waitForIdle()
}

/** …and `Enter`, which commits it and leaves it selected. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.finishCaret() {
    onNodeWithTag(MARKUP_TEXT_FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
}

/** A tap on the middle of a parameter's track, which is how a slider is moved by hand. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tapSizeRow(spec: String) {
    onNodeWithTag("param-$spec").performClick()
    waitForIdle()
}
