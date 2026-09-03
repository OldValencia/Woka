# ME-004 — Document, history, session

| | |
|---|---|
| **Order** | 5 |
| **Depends on** | ME-003 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** The immutable edit document, a snapshot history, and the only object allowed to mutate them.

**Why now.** This is the media-agnostic heart — the image session and the video session are the same
class, and nothing above it can be written until it exists.

## Steps

1. `ToolOp.kt` (commonMain): `@Serializable data class ToolOp(val toolId: String, val params: ParamSet)`.
2. `EditDocument.kt` (commonMain): `@Serializable data class EditDocument(val ops: List<ToolOp> =
   emptyList(), val pending: ToolOp? = null)` with `withPending(op)`,
   `commitPending(cardinality)` — `Single` replaces the tool's existing op, `Repeating` appends —
   `opsOf(toolId)` and `isEmpty`. Every mutator returns `.copy()`; `ops` is replaced, never mutated.
3. `EditorHistory.kt` (commonMain): `data class EditorHistory(val entries: List<EditDocument>, val
   index: Int)` with `push`, `undo`, `redo`, `canUndo`, `canRedo`. Cap 50, dropping from the **old**
   end; `push` truncates the redo tail. Pure — no coroutines, no flows, no clock.
4. `EditorSession.kt` (commonMain): holds `MutableStateFlow<EditDocument>` and the history; exposes
   `document`, `canUndo`, `canRedo`, `isDirty` as `StateFlow`; methods `preview(op)`, `commit()`,
   `undo()`, `redo()`, `reset()`. `preview` pushes **no** history entry (§3.1). No `androidx.compose.*`
   import — the anti-FQN grep and the ViewModel rule both apply here.
5. Lifetime: constructed by Koin as `factory { (media: ViewerMedia) -> EditorSession(media) }` in
   `EditorModule`; the scope is owned by the caller and cancelled on close. Write that in the KDoc —
   a session with no stated owner leaks. The composable that owns it arrives with the shell (ME-101).
6. Selection is **not** history. Note in the KDoc that a `CanvasTool`'s selected shape lives in the
   tool scope, not the document, so selecting something never produces an undo step.

## Files

`feature/editor/core/ToolOp.kt`, `core/EditDocument.kt`, `core/EditorHistory.kt`,
`core/EditorSession.kt` (all commonMain, new), `feature/editor/di/EditorModule.kt` (edit)

## Done when

`jvmTest/unit/EditDocumentTest.kt` and `unit/EditorHistoryTest.kt` cover:

- `Single` replaces the tool's previous op; `Repeating` appends;
- the cap holds at 50 and drops the oldest, not the newest;
- `push` after an `undo` truncates the redo tail;
- 200 `preview` calls followed by one `commit` produce exactly one history entry;
- `isDirty` is false on a fresh session and false again after undoing back to the start.

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **506 tests, 0 failures, 0 errors** — 490 before, plus sixteen
here. Largest new file 110 lines.

### The session does not take `ViewerMedia`

Step 4 requires `EditorSession.kt` in `commonMain` with no `androidx.compose.*` import; step 5
specifies `factory { (media: ViewerMedia) -> EditorSession(media) }`. Those cannot both hold:
`ViewerMedia` lives in `jvmMain`, so `commonMain` cannot see it at all, and it carries `ImageBitmap`,
`IntSize` and `@Composable` members, so accepting it would break the no-Compose rule in the same
breath.

Resolved in favour of step 4, which has three independent backers against one word in step 5 — step 4
itself, §3.3 ("the session knows nothing about pixels"), and `code-style/state.md`. The session takes
**`MediaItem`**: `core/data`, `commonMain`, Compose-free, and already the app's domain type for a
media file. The factory keeps the shape the step wanted.

### `ToolCardinality` is declared here, not in ME-005

`commitPending(cardinality)` needs the type and the manifest that reads it (§3.4) arrives in ME-005,
one ticket later. The enum is in `core/ToolCardinality.kt`; ME-005's `ToolManifest` names it rather
than redeclaring it.

### A test-construction trap worth knowing before ME-101

The first version of `EditorSessionTest` built the session in a **field initialiser** and three
assertions silently passed for the wrong reason: every derived flow read as its initial `false`.
A field initialiser runs before `MainDispatcherRule` installs the test dispatcher, so the session
captured the real `Dispatchers.Main` and its `stateIn(…, Eagerly)` collectors never ran. Only the one
assertion expecting `true` failed, which is what exposed it.

The fix is the house pattern, not a workaround: `jvmTest/support/EditorHarness.kt` with
`onEditorSession { }`, constructing inside the block and closing in a `finally` — the same shape and
the same reason as `onFolder`. Any later test that builds a session must go through it.

### Beyond the ticket

- `EditDocument.clearPending()` and `EditorSession.cancelPending()` — an aborted drag has to leave no
  trace, and without them the only way to drop an in-flight operation was to commit it and undo,
  which would put a step in history that the user never took.
- `EditorSessionTest.kt` — the done-when lists five behaviours under two file names, but the last two
  are session-level and belong beside the session.
- `EditorSession.reset()` pushes a history entry, so it is itself undoable. The step lists `reset()`
  without saying which; making it silent would be the one action in the editor that cannot be taken
  back.
