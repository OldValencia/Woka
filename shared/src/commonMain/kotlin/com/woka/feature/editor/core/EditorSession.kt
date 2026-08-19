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
 *
 * **`initial` is what the file already carries** (ME-134). A session used to start
 * from an empty document whatever the file held, so the second `Done` on a
 * photograph wrote only that sitting and replaced the first one's edit. Starting
 * here is also what makes [isDirty] mean "differs from the file" rather than "the
 * document is non-empty" — reopening an edited picture is not a change to it.
 *
 * **The history opens on the recipe, not on this sitting** (`EditorHistory.startingAt`,
 * decided by hand 2026-08-19). Undo therefore walks back through operations made in
 * earlier sittings and reaches the original photograph at the end of them. What the
 * *mark* in Information says is a separate question, answered by the document rather
 * than by [isDirty]: a file that carries an edit reads as edited the moment it opens.
 */
class EditorSession(val media: MediaItem, initial: EditDocument = EditDocument()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * What the file holds. A `var` for exactly one reason: Revert removes the recipe
     * from the file, and a session still measuring itself against the old one would
     * be instantly dirty against a document that no longer exists (ME-107).
     *
     * **Named apart from the constructor parameter deliberately.** A property and a
     * parameter of the same name are both in scope in an initialiser, and the
     * *parameter* wins there — so [isDirty] would have captured the opening document
     * for the life of the session and gone on comparing against it after a revert.
     * `RevertTest` caught exactly that.
     */
    private var openedWith = initial

    private val _document = MutableStateFlow(openedWith)

    /** The live document, including any operation still being dragged. */
    val document: StateFlow<EditDocument> = _document.asStateFlow()

    private val _history = MutableStateFlow(EditorHistory.startingAt(openedWith))

    // Eagerly, not WhileSubscribed: these gate toolbar buttons and are read
    // before anything collects them. A five-second replay window would show a
    // stale Undo button for the first frame after the editor opens.
    val canUndo: StateFlow<Boolean> =
        _history.map { it.canUndo }.stateIn(scope, SharingStarted.Eagerly, false)

    val canRedo: StateFlow<Boolean> =
        _history.map { it.canRedo }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Whether anything would be lost by closing — what the Cancel dialog asks about. */
    val isDirty: StateFlow<Boolean> =
        _document.map { it != openedWith }.stateIn(scope, SharingStarted.Eagerly, false)

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

    /**
     * Back to the document the editor opened with, in one undoable step.
     *
     * **Not Revert** (ME-107): this undoes *this sitting*, and the document it
     * returns to is the one the file carries, not an empty one.
     */
    fun reset() {
        if (_document.value == openedWith) return
        _document.value = openedWith
        _history.value = _history.value.push(openedWith)
    }

    /**
     * The file no longer carries anything: document, history and [openedWith] all go back
     * to empty (ME-107).
     *
     * **It writes nothing.** The engine has already removed the recipe by the time this
     * is called — this is the session catching up with the file, which is why it is not
     * undoable: there is nothing on disk left to undo to.
     */
    fun revertedToOriginal() {
        openedWith = EditDocument()
        _document.value = openedWith
        _history.value = EditorHistory.startingAt(openedWith)
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
