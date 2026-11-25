package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport

/**
 * What the engine offers besides `Done`, and what choosing one does (`video-editor.md` §4.5).
 *
 * **`Done ▾` is gone** (RD-201): a drop-down beside the one affirmative action was bad UX
 * (answer 3), and the other exports come back as RD-401's `Create a copy…`. What remains
 * here is [instead] — on a film that cannot keep the edit, the render takes `Done`'s place.
 *
 * **A composition local, because the bar is drawn somewhere else.** `EditorTopBarSetup`
 * hands `GlobalTopBar` a lambda that composes under the bar, not under the editor, so the
 * setup reads this under the editor and carries it across with `rememberUpdatedState`.
 */
@Immutable
internal class DoneChoices(
    val exports: List<EditorExport>,
    val onExport: (EditorExport) -> Unit,
    /** False for `RenderOnly` (ME-289): the file cannot keep the edit, so `Done` gives way. */
    val keeps: Boolean = true
) {
    /** What the button does in `Done`'s place — the first export — or null where `Done` stays. */
    val instead: (() -> Unit)?
        get() = exports.firstOrNull()?.takeIf { !keeps }?.let { export -> { onExport(export) } }

    /** The button's word: `Done`, or the export that stands in for it. */
    val label: String
        get() = if (keeps) "Done" else exports.firstOrNull()?.title ?: "Done"
}

internal fun doneChoicesOf(engine: EditorEngine, onExport: (EditorExport) -> Unit) =
    DoneChoices(engine.exports, onExport, keeps = engine.capability != EditCapability.RenderOnly)

internal val LocalDoneChoices = staticCompositionLocalOf { DoneChoices(emptyList(), {}) }
