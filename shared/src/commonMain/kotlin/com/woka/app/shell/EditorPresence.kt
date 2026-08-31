package com.woka.app.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether an editor is up, and how to make it leave (ME-124).
 *
 * **Why it exists.** `PipManager.closePip(returnToMain = true)` calls
 * `Router.replaceTop` with a fresh `nonce`, which is a different `AnimatedContent`
 * target, which rebuilds `FolderScreen` — and `editorTarget` is `remember`ed inside it.
 * A keypress in the video window therefore used to destroy an open editor and its
 * unsaved work, with no dialog and no banner. The return still happens; the editor is
 * saved and closed on the way through.
 *
 * **In `app/shell` beside `GlobalShortcutState`**, for the reason that type is here: it
 * is shell state two features read. Not in `core/`, which may not know an editor exists,
 * and not in either feature, which may not name the other. An object rather than a
 * `CompositionLocal` because `closePip` is a plain function and cannot read one.
 *
 * **A count, not a flag.** It was written for ME-128's several live sessions; that ticket
 * was dropped 2026-09-01 and one editor is now the shape, so what the count earns today
 * is narrower and still worth having: an unbalanced [closed] cannot drive it negative and
 * swallow the next [opened], which `EditorPresenceTest` asserts. `ExitWhenQuiet` reads
 * [isOpen] to decide whether anyone has to be asked before the application goes (ME-160),
 * and a presence stuck below zero there would quit over an unsaved edit.
 */
@Stable
object EditorPresence {

    private var open by mutableStateOf(0)

    val isOpen: Boolean get() = open > 0

    /**
     * "Leave, and write if you have to" — published by the editor while it is mounted.
     *
     * **One lambda rather than a pair of facts.** The editor decides for itself whether
     * leaving means saving: a session that is not dirty closes without a write. An
     * `isDirty` exposed here and read by the caller would save a file that was opened
     * and never touched — its document is not empty, it holds the recipe from last time
     * (ME-134), so the save would answer `Saved` and the pill would report an edit that
     * never happened.
     *
     * It reads the session **when it is called**, never at publication, so there is no
     * snapshot to go stale between the two.
     */
    var saveAndClose: (() -> Unit)? = null
        private set

    fun opened(action: () -> Unit) {
        open++
        saveAndClose = action
    }

    fun closed() {
        open--
        if (open <= 0) {
            open = 0
            saveAndClose = null
        }
    }
}
