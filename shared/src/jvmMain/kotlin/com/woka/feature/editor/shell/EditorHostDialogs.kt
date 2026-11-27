package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.tools.resize.SizeChoice

/** What the editor's controls invoke, and whether Revert has anything to take back. */
@Stable
internal class EditorHostActions(
    val leave: () -> Unit,
    val done: () -> Unit,
    val askRevert: () -> Unit,
    val selectFrame: (Int) -> Unit,
    val canRevert: Boolean,
    /** The header's and the timeline's other ways out, for `LocalDoneChoices` (RD-401). */
    val choices: DoneChoices
)

/** Which of the editor's questions is up (RD-401) — at most one, since each is raised by a control the others cover. */
internal enum class EditorQuestion { Revert, Keep, Save, Copy, Frame }

/** What the questions do once answered — the host's actions, gathered so the call stays readable. */
internal class EditorAnswers(
    val revert: () -> Unit,
    /** `Keep changes`: the save at the size the document already holds. */
    val keep: () -> Unit,
    val discard: () -> Unit,
    /** `Save`: the photo's chosen size first (null for a film), then the save. */
    val save: (SizeChoice?) -> Unit,
    val copy: (IntSize?) -> Unit,
    val frame: () -> Unit,
    val dismiss: () -> Unit
)

/**
 * The editor's dialogs, whichever [question] is open (RD-401): Revert, `Keep your changes?`,
 * `Save your changes?`, `Create a finished copy` and `Save frame as photo` — in the
 * prototype's frame, over the editor's room.
 *
 * A fifth question — the window's close button, ME-160 — is deliberately **not** here. It is
 * raised from outside the editor rather than by a control inside it, so `EditorExitEffect`
 * owns the question, the answer and the dialog together.
 */
@Composable
internal fun EditorHostDialogs(
    question: EditorQuestion?,
    engine: EditorEngine,
    session: EditorSession,
    /** Which Revert the switch is pointing at, for the dialog to say so (ME-176). */
    revertScope: EditScope,
    /** Where the play head stood when `Save frame as photo…` was chosen. */
    frameAtMs: Long,
    keys: CanvasKeys?,
    answers: EditorAnswers
) {
    val video = engine.kind == MediaKind.Video
    CompositionLocalProvider(LocalCanvasKeys provides keys) {
        Questions(question, engine, session, revertScope, frameAtMs, answers, video)
    }
}

@Composable
private fun Questions(
    question: EditorQuestion?,
    engine: EditorEngine,
    session: EditorSession,
    revertScope: EditScope,
    frameAtMs: Long,
    answers: EditorAnswers,
    video: Boolean
) {
    when (question) {
        EditorQuestion.Revert -> RevertDialog(answers.revert, answers.dismiss, revertScope, video)
        EditorQuestion.Keep -> KeepChangesDialog(answers.dismiss, answers.discard, answers.keep)
        EditorQuestion.Save -> SaveChangesDialog(
            size = if (video) null else rememberOutputSize(engine, session, copy = false),
            onSave = answers.save,
            onCancel = answers.dismiss
        )
        EditorQuestion.Copy -> CreateCopyDialog(
            size = rememberOutputSize(engine, session, copy = true),
            video = video,
            retone = retoneOf(engine.sourceColor),
            onCreate = answers.copy,
            onCancel = answers.dismiss
        )
        EditorQuestion.Frame -> FramePhotoDialog(frameAtMs, answers.frame, answers.dismiss)
        null -> Unit
    }
}
