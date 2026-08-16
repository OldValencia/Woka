package com.woka.feature.editor.core

import com.woka.core.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The only object allowed to change an [EditDocument]. A ViewModel in all but name.
 *
 * **Media-agnostic on purpose** (§3.3). It holds a [MediaItem] — a path and a kind
 * — and nothing that can draw. Everything that knows about pixels sits behind
 * `EditorEngine`, which is what makes the video editor reuse this class rather
 * than acquire a second one. ME-004 specified the Koin factory as taking
 * `ViewerMedia`; that type lives in `jvmMain` and carries `ImageBitmap`,
 * `IntSize` and `@Composable` members, so a `commonMain` state holder can neither
 * see it nor accept it without breaking the no-Compose rule this class is built
 * under (`code-style/state.md`). [MediaItem] is the media-agnostic stand-in.
 *
 * **Lifetime.** Built by Koin as a `factory` keyed on the media it edits. It owns
 * [scope]; whoever constructs it owns it and **must** call [close] when the editor
 * is dismissed — the composable that does arrives with the shell in ME-101. A
 * session with no stated owner leaks its scope and its derivations.
 *
 * **Selection is not history** (§3.1). A `CanvasTool`'s selected shape, its drag
 * handles and its hover state live in the tool's own scope, never in the document,
 * so clicking a markup arrow never produces an undo step. Only [commit] does.
 */
class EditorSession(val media: MediaItem) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _document = MutableStateFlow(EditDocument())

    /** The live document, including any operation still being dragged. */
    val document: StateFlow<EditDocument> = _document.asStateFlow()

    private val _history = MutableStateFlow(EditorHistory())

    // Eagerly, not WhileSubscribed: these gate toolbar buttons and are read
    // before anything collects them. A five-second replay window would show a
    // stale Undo button for the first frame after the editor opens.
    val canUndo: StateFlow<Boolean> =
        _history.map { it.canUndo }.stateIn(scope, SharingStarted.Eagerly, false)

    val canRedo: StateFlow<Boolean> =
        _history.map { it.canRedo }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Whether anything would be lost by closing — what the Cancel dialog asks about. */
    val isDirty: StateFlow<Boolean> =
        _document.map { !it.isEmpty }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Replaces the operation in flight. Pushes **no** history entry, so a slider
     * dragged across its range costs one undo step rather than two hundred (§3.1).
     */
    fun preview(op: ToolOp) {
        _document.value = _document.value.withPending(op)
    }

    /** Folds the operation in flight into the document and records one undo step. */
    fun commit(cardinality: ToolCardinality) {
        val committed = _document.value.commitPending(cardinality)
        if (committed == _document.value) return
        _document.value = committed
        _history.value = _history.value.push(committed)
    }

    /** Abandons the operation in flight — an aborted drag, not an undo. */
    fun cancelPending() {
        _document.value = _document.value.clearPending()
    }

    fun undo() = moveTo(_history.value.undo())

    fun redo() = moveTo(_history.value.redo())

    /** Back to the document the editor opened with, in one undoable step. */
    fun reset() {
        if (_document.value.isEmpty) return
        val cleared = EditDocument()
        _document.value = cleared
        _history.value = _history.value.push(cleared)
    }

    /** Cancels [scope]. Called by the owner; the session cannot be reused after it. */
    fun close() {
        scope.cancel()
    }

    private fun moveTo(history: EditorHistory) {
        _history.value = history
        _document.value = history.current
    }
}
