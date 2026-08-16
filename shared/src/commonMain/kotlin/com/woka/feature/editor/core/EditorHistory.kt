package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable

/** Snapshots kept. A document is ~1 KB, so fifty of them is not a memory decision. */
private const val HISTORY_CAP = 50

/**
 * Undo/redo as a list of whole documents plus a cursor.
 *
 * **Snapshots, not inverse commands** (§3.1). Reversing an operation means every
 * tool shipping an `undo` alongside its `stage`, and the first tool to get that
 * subtly wrong corrupts the document in a way that only shows up several steps
 * later. Copying a list of a few hundred bytes removes the whole class of bug.
 *
 * Pure by construction: no coroutines, no flows, no clock. [EditorSession] is what
 * publishes it; this only computes.
 *
 * [entries] always holds at least one document — the state the editor opened in —
 * so [index] is always valid and "undone back to the beginning" is a real position
 * rather than an empty list.
 */
@Immutable
data class EditorHistory(
    val entries: List<EditDocument> = listOf(EditDocument()),
    val index: Int = 0
) {

    val current: EditDocument get() = entries[index]

    val canUndo: Boolean get() = index > 0

    val canRedo: Boolean get() = index < entries.lastIndex

    /**
     * Adds [document] as the newest entry.
     *
     * Everything after [index] is dropped first: editing after an undo abandons
     * the redo tail, because the branch the user did not take is not a branch this
     * keeps. At the cap the **oldest** entry goes — losing the newest would mean
     * the last thing done is the first thing forgotten.
     */
    fun push(document: EditDocument): EditorHistory {
        val kept = entries.take(index + 1) + document
        val trimmed = if (kept.size > HISTORY_CAP) kept.takeLast(HISTORY_CAP) else kept
        return EditorHistory(trimmed, trimmed.lastIndex)
    }

    fun undo(): EditorHistory = if (canUndo) copy(index = index - 1) else this

    fun redo(): EditorHistory = if (canRedo) copy(index = index + 1) else this
}
