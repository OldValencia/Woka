# ME-004 — Document, history, session

| | |
|---|---|
| **Order** | 5 |
| **Depends on** | ME-003 |
| **Feature** | [Media Editor](../media-editor.md) |

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
