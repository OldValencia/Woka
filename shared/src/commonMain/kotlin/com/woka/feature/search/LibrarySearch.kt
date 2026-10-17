package com.woka.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.woka.app.shell.sidebar.pinnedParentLabel
import com.woka.core.data.isMedia
import java.io.File

/**
 * What is being searched for, read by the toolbar's box and by the results that
 * replace the grid (SIDE-008). One per window, so a plain holder: the box writes
 * [query], the results read it, and Ctrl+F asks [focus] for the box.
 */
object SearchState {
    var query by mutableStateOf("")

    /** All linked folders, or only the folder on screen and below it. */
    var everywhere by mutableStateOf(true)

    val focus = FocusRequester()
}

/** One match: the file, and where it sits, as the sidebar names a pin's place. */
data class SearchHit(val file: File, val place: String)

/** Enough to fill several screens; past it the query wants another letter. */
private const val MAX_HITS = 300

/** A bound on the walk itself, so a library of a million files cannot hang the box. */
private const val MAX_VISITED = 200_000

/**
 * Every folder and media file under [scope] whose name holds [query], ignoring case
 * (SIDE-008). Recursive — the old overlay looked one level down, which found a
 * folder by name and never the photograph inside it. Hidden folders are not entered.
 *
 * [stillWanted] is asked as the walk goes, so a query superseded by the next key
 * press stops walking rather than finishing a search nobody will read.
 */
fun searchLibrary(
    query: String,
    scope: List<String>,
    roots: List<String>,
    stillWanted: () -> Boolean
): List<SearchHit> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    var visited = 0
    return scope.asSequence()
        .flatMap { top -> File(top).walkTopDown().onEnter { it == File(top) || !it.isHidden }.drop(1) }
        .takeWhile { stillWanted() && ++visited <= MAX_VISITED }
        .filter { (it.isDirectory || it.isMedia) && it.name.contains(needle, ignoreCase = true) }
        // Linked roots may nest — `Heic` linked on its own and inside a linked `Pictures`
        // — and a file reached through both is still one result (2026-10-17: the grid
        // threw on the duplicate key).
        .distinctBy { it.absoluteFile.normalize().path.lowercase() }
        .take(MAX_HITS)
        .map { SearchHit(it, pinnedParentLabel(it.absolutePath, roots)) }
        .toList()
}
