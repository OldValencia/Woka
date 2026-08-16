package com.woka.feature.editor.core

/**
 * What a save job did. Read by the banner and by the folder refresh (§4.5).
 *
 * [Saved] carries the written path because **a copy is a file the grid has never
 * seen**: nothing watches the directory for it, so the listing is refreshed
 * against this path rather than re-scanned hopefully.
 */
sealed interface EditSaveResult {

    data class Saved(val path: String) : EditSaveResult

    /**
     * [reason] is shown to the user, so it says what happened to their file —
     * "the original is untouched" — not which exception was thrown.
     */
    data class Failed(val reason: String) : EditSaveResult

    /** A document with nothing in it. Not an error, and not worth a banner. */
    data object NothingToDo : EditSaveResult
}
