package com.woka.feature.editor.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A film's session as **one line of history** (ME-221): the selected clip is the scope, and
 * selecting, editing and deselecting are all steps `Ctrl+Z` walks back through in order.
 *
 * A still and a GIF never enter it. Their frames keep a history each and walking between them
 * is not a step (`EditorSessionFrames`) — the right answer for a frame strip nobody edits as a
 * sequence, and the wrong one for clips picked and left a dozen times a sitting.
 *
 * Each step carries a [SessionShot]; [restore] puts one back whole.
 */

/** The film half of the session's state, held apart so the session stays one screen long. */
internal class FilmLine(openedOn: Int) {
    val focus = MutableStateFlow(openedOn)
    val selection = MutableStateFlow<String?>(null)
    var linear = false
    var sharedTools: Set<String> = emptySet()
}

/**
 * Turns this session linear. [shared] are the tools whose ops are the whole film's — a cut, a
 * crop, a size — and go into the shared layer even while a clip is open. Called once, before
 * anything is edited, on a session that opened on no clip.
 */
fun EditorSession.useLinearHistory(shared: Set<String>) {
    film.sharedTools = shared
    if (film.linear) return
    film.linear = true
    val history = _history.value
    _history.value = history.copy(
        shots = history.entries.map { SessionShot(it, it, NO_CLIP, emptyMap(), openedWith, null) }
    )
}

/** Selects clip [id], whose layer key is [key] — [NO_CLIP] and null for the whole film — as one step. */
fun EditorSession.pickClip(id: String?, key: Int) {
    if (id == film.selection.value && key == frame) return
    focusClip(id, key)
    record(_document.value)
}

/**
 * Selects clip [id] **without** a step of its own: a delete or a duplicate changes the
 * selection inside the step it is already making. The open clip is put away and the next one
 * composed over the shared layer, measured against what the file holds for it.
 */
fun EditorSession.focusClip(id: String?, key: Int) {
    film.selection.value = id
    if (key == frame) return
    val shown = _document.value.clearPending()
    if (frame == NO_CLIP) {
        base = shown
    } else {
        elsewhere.value += frame to FrameEdit(shown, EditorHistory.startingAt(shown), openedWith, base)
    }
    val next = if (key == NO_CLIP) base else EditDocument(base.ops + ownOpsOf(key))
    openedWith = if (key == NO_CLIP) storedBase else storedFor(key)
    elsewhere.value -= key
    frame = key
    _document.value = next
}

/** The one push a step goes through: a linear session keeps its whole state beside it. */
internal fun EditorSession.record(document: EditDocument, change: LayerChange? = null) {
    _history.value = _history.value.push(document, change, shotNow().takeIf { film.linear })
}

/** The session's whole state as it stands. */
internal fun EditorSession.shotNow(): SessionShot =
    SessionShot(_document.value, base, frame, elsewhere.value, openedWith, film.selection.value)

/** Puts [shot] back as the state of [history]'s current step. */
internal fun EditorSession.restore(history: EditorHistory, shot: SessionShot) {
    _history.value = history
    base = shot.base
    elsewhere.value = shot.elsewhere
    openedWith = shot.openedWith
    film.selection.value = shot.selection
    frame = shot.frame
    _document.value = shot.document
}

/** The layer a copy is composed over: the shared one, whichever document is on screen. */
internal val EditorSession.sharedLayer: EditDocument
    get() = if (film.linear && frame != NO_CLIP) base else _document.value
