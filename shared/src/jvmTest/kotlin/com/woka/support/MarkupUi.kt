package com.woka.support

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import com.woka.feature.editor.core.EditDocument
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
 * The pen, by its constant's name.
 *
 * **By tag, not by text**: the chips are glyphs since 2026-08-27 and their names live in a
 * tooltip, so there is no word on screen to click.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.chooseMarkupPen(name: String) {
    onNodeWithTag("param-kind-$name").performClick()
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

/**
 * Open the tool, choose the text pen, click the picture once — and answer the document as
 * it stood **before** the caret, which is what an abandoned edit has to restore.
 *
 * Was private to `MarkupTextUiTest` until 2026-08-27, when a second file needed it and that
 * one crossed the size mandate.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openCaret(harness: EditorHarness): EditDocument {
    openMarkupTool(harness)
    chooseMarkupPen("Text")
    val before = harness.session.document.value
    clickPicture(Offset(60f, 60f))
    return before
}

/** Types into the open caret without finishing — the field keeps the keyboard. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.typeIntoCaret(text: String) {
    onNodeWithTag(MARKUP_TEXT_FIELD_TAG).performTextInput(text)
    waitForIdle()
}

/**
 * …and finishing it, which since 2026-08-27 is **a click on the picture** and no longer
 * `Enter` — that key breaks a line now that a caption has a box to break inside.
 *
 * [at] defaults to a corner well clear of where `clickPicture(60, 60)` puts a caret, so the
 * press lands on empty picture: `MarkupPointer` ends any open edit before it decides what
 * else a press means, so the click commits and does nothing more.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.finishCaret(at: Offset = Offset(400f, 300f)) {
    clickPicture(at)
}

/**
 * Opens — or closes — a compact control's popover: one press on the button that reports
 * its value (ME-155). Colours and face sets are reached this way.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.toggleControl(key: String) {
    onNodeWithTag("param-$key").performClick()
    waitForIdle()
}

/**
 * The same, for a level: its button is the number in the middle of `− │ 64px │ +`, and
 * the slider is inside what that opens.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openValue(key: String) {
    onNodeWithTag("param-$key-value").performClick()
    waitForIdle()
}

/**
 * A tap on the middle of a parameter's track, which is how a slider is moved by hand —
 * **inside the popover its frame opens** since ME-155, rather than on the panel itself.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tapSizeRow(spec: String) {
    openValue(spec)
    onNodeWithTag("param-$spec-slider").performClick()
    waitForIdle()
}
