package com.woka.support

import androidx.compose.runtime.Composable
import com.woka.app.shell.AppExitRequest
import com.woka.app.shell.EditorPresence
import com.woka.app.shell.ExitWhenQuiet
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.shell.EditorExitEffect

/**
 * The two halves of ME-160 composed together: the editor's answer and the door's gate.
 *
 * A harness rather than a copy in each test, because both halves are the subject —
 * asserting on `EditorExitEffect` alone would prove a dialog appears and nothing about
 * whether the application then goes, which is the whole point of the ticket.
 */
@Composable
internal fun ExitScene(isDirty: Boolean, onSave: () -> Unit, onExit: () -> Unit) {
    EditorExitEffect(isDirty = isDirty, onSave = onSave)
    ExitWhenQuiet(AppExitRequest.requested, onExit)
}

/**
 * An editor is up for the length of [block].
 *
 * `EditorExitEffect` does not publish presence — `EditorPresenceEffect` does, and it is
 * not in this scene — so without this the gate takes the no-editor branch and quits
 * before anything has been asked.
 */
internal fun withEditorOpen(block: () -> Unit) {
    EditorPresence.opened {}
    try {
        block()
    } finally {
        EditorPresence.closed()
    }
}

/**
 * Every singleton this ticket touches, back to rest.
 *
 * Three objects outlive any one test, and a count left at 1 by a test that failed
 * halfway makes the next one hang on the gate rather than fail on its own assertion.
 */
internal fun resetExitState() {
    AppExitRequest.cancel()
    EditorSaveStatus.clear()
    while (EditorSaveStatus.inFlight > 0) EditorSaveStatus.finished()
    while (EditorPresence.isOpen) EditorPresence.closed()
}
