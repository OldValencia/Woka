package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport

/**
 * The editor's ways out besides `Done` itself (`video-editor.md` §4.5, RD-401): what the
 * engine exports, and the header's and the timeline's doors to them.
 *
 * **`Done ▾` is gone** (RD-201): a drop-down beside the one affirmative action was bad UX
 * (answer 3). The other exports are `Create a copy…` ([onCopy]) and the timeline menus'
 * `Save frame as photo…` ([onSaveFrame]); [instead] remains — on a film that cannot keep the
 * edit, the render takes `Done`'s place.
 *
 * **A composition local, because the bar is drawn somewhere else.** `EditorTopBarSetup`
 * hands `GlobalTopBar` a lambda that composes under the bar, not under the editor, so the
 * setup reads this under the editor and carries it across with `rememberUpdatedState`. The
 * timeline's menus read it the same way, from inside their popup.
 */
@Immutable
internal class DoneChoices(
    val exports: List<EditorExport>,
    /** Runs [export] and closes the editor first — [instead]'s road. */
    val onExport: (EditorExport) -> Unit,
    /** False for `RenderOnly` (ME-289): the file cannot keep the edit, so `Done` gives way. */
    val keeps: Boolean = true,
    /** `Create a copy…` in the header (RD-401). */
    val onCopy: () -> Unit = {},
    /** The timeline menus' `Save frame as photo…`; null where the engine offers no frame. */
    val onSaveFrame: (() -> Unit)? = null
) {
    /** What the button does in `Done`'s place — the first export — or null where `Done` stays. */
    val instead: (() -> Unit)?
        get() = exports.firstOrNull()?.takeIf { !keeps }?.let { export -> { onExport(export) } }

    /** The button's word: `Done`, or the export that stands in for it. */
    val label: String
        get() = if (keeps) "Done" else exports.firstOrNull()?.title ?: "Done"
}

internal fun doneChoicesOf(
    engine: EditorEngine,
    onExport: (EditorExport) -> Unit,
    onCopy: () -> Unit = {},
    onSaveFrame: (() -> Unit)? = null
) = DoneChoices(
    exports = engine.exports,
    onExport = onExport,
    keeps = engine.capability != EditCapability.RenderOnly,
    onCopy = onCopy,
    onSaveFrame = onSaveFrame
)

internal val LocalDoneChoices = staticCompositionLocalOf { DoneChoices(emptyList(), {}) }
