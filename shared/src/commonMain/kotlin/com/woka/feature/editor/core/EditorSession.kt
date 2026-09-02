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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The only object allowed to change an [EditDocument]. A ViewModel in all but name.
 *
 * **Media-agnostic on purpose** (§3.3). It holds a [MediaItem] — a path and a kind —
 * and nothing that can draw. Everything that knows about pixels sits behind
 * `EditorEngine`, which is what makes the video editor reuse this class rather than
 * acquire a second one. ME-004 specified the Koin factory as taking `ViewerMedia`; that
 * type lives in `jvmMain` and carries `ImageBitmap`, `IntSize` and `@Composable`
 * members, so a `commonMain` state holder can neither see it nor accept it without
 * breaking the no-Compose rule (`code-style/state.md`).
 *
 * **Lifetime.** Built by Koin as a `factory` keyed on the media it edits. It owns
 * [scope]; whoever constructs it owns it and **must** call [close] when the editor is
 * dismissed. A session with no stated owner leaks its scope and its derivations.
 *
 * **Selection is not history** (§3.1). A `CanvasTool`'s selected shape, its drag
 * handles and its hover state live in the tool's own scope, never in the document,
 * so clicking a markup arrow never produces an undo step. Only [commit] does.
 *
 * **`initial` is what the file already carries** (ME-134). A session used to start from
 * an empty document whatever the file held, so the second `Done` on a photograph wrote
 * only that sitting and replaced the first one's edit. It is also what makes [isDirty]
 * mean "differs from the file" rather than "the document is non-empty".
 *
 * **Walking between frames is `EditorSessionFrames`**, and putting a sitting back is
 * `EditorSessionResets` — both extensions on this class, split off for size.
 *
 * **The history opens on the recipe, not on this sitting** (`EditorHistory.startingAt`,
 * decided by hand 2026-08-19). Undo therefore walks back through operations made in
 * earlier sittings and reaches the original photograph at the end of them. What the
 * *mark* in Information says is a separate question, answered by the document rather
 * than by [isDirty]: a file that carries an edit reads as edited the moment it opens.
 */
class EditorSession(
    val media: MediaItem,
    initial: EditDocument = EditDocument(),
    /** Which frame it opened on — the only frame, for a photograph (ME-169). */
    private val openedOn: Int = 0,
    /** What a frame not yet reached carries. A function: it means reading the file. */
    internal val storedFor: (Int) -> EditDocument = { EditDocument() },
    /**
     * The file's own file-wide layer, apart from any frame's work on top (2026-09-02).
     * Needed to tell a frame's **delta** from the composition it is shown as; neither
     * half is recoverable from the composed document alone.
     */
    internal val storedBase: EditDocument = EditDocument()
) {

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
    internal var openedWith = initial

    internal val _document = MutableStateFlow(openedWith)

    /** The live document, including any operation still being dragged. */
    val document: StateFlow<EditDocument> = _document.asStateFlow()

    internal val _history = MutableStateFlow(EditorHistory.startingAt(openedWith))

    /**
     * The edit made **for every frame**, unsaved — the in-memory half of ME-175's
     * payload. Without it, switching to `Single Frame` handed the shared work to
     * whichever frame was open and the rest opened blank (2026-09-02).
     */
    internal var base: EditDocument = initial

    /** Which frame the flows above are speaking about (ME-169). */
    var frame: Int = openedOn
        internal set

    /**
     * Every frame the editor has left, with its work intact (ME-169). **Not a map of
     * flows**: swapping what the one flow *holds* leaves every reader reading what it
     * always read. A `StateFlow` so [isDirty] answers for frames nobody is looking at.
     */
    internal val elsewhere = MutableStateFlow<Map<Int, FrameEdit>>(emptyMap())

    // Eagerly, not WhileSubscribed: these gate toolbar buttons and are read
    // before anything collects them. A five-second replay window would show a
    // stale Undo button for the first frame after the editor opens.
    val canUndo: StateFlow<Boolean> =
        _history.map { it.canUndo }.stateIn(scope, SharingStarted.Eagerly, false)

    val canRedo: StateFlow<Boolean> =
        _history.map { it.canRedo }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * What the Cancel dialog asks about, and **every frame rather than the open one**
     * (ME-169): work walked away from is still work.
     */
    val isDirty: StateFlow<Boolean> =
        combine(_document, elsewhere) { document, elsewhere ->
            document != openedWith || elsewhere.values.any { it.isDirty }
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun undo() = moveTo(_history.value.undo())
    
    fun redo() = moveTo(_history.value.redo())
    
    private fun moveTo(history: EditorHistory) {
        _history.value = history
        _document.value = history.current
    }

    /** Cancels [scope]. Called by the owner; the session cannot be reused after it. */
    fun close() {
        scope.cancel()
    }
}
