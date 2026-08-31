package com.woka.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.shell.ParamRows

/**
 * One tool's panel, drawn from its schema and **collecting** the document.
 *
 * `ParamRows` takes a document by value, so a test that passed `document.value` once
 * would draw a panel nothing could move: every write would land in the session and
 * change no pixel. A harness rather than a copy per class, because two of them need it
 * and the mistake is silent when it is made.
 */
@Composable
internal fun ParamPanel(tool: EditorTool, harness: EditorHarness) {
    val document by harness.session.document.collectAsState()
    ParamRows(tool, harness.session, document)
}
