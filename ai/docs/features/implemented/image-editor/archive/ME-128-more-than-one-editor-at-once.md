> **DROPPED 2026-09-01 — not a source of truth, and not work waiting to be picked up.**
> The id is retained and never reused (`conventions/tickets.md` §2). Kept because the survey in
> "What is already true" is accurate and the two defects found while dropping it are worth reading.
>
> **Why.** Its "Why now" — "an edit in progress is lost by opening something else" — stopped being
> true. Every door out of the editor now asks or saves: `Cancel`/`Esc` and a frame change confirm,
> the return from the video window saves (ME-124), and the window's close button asks (ME-160). This
> was never a data-loss ticket after 2026-08-31; it was convenience, at the price of moving session
> ownership out of the composition, ~20 MB of proxy bitmap resident per open editor, and reopening
> ME-124 and ME-160 — both of which speak about *the* editor, singular.
>
> **Two defects found in it, recorded so a later reading does not have to find them again.**
> It contradicts itself on `Cancel`: line 46 says a session ends on it, line 111 says closing "sets
> it to null without disposing". And with `Cancel` ending the session and the dock hidden while
> editing, **no door suspends an editor** — the store could never hold more than one entry. Making
> it work needs a new "minimise" control beside `Cancel`, which is against §4.2: the editor is a
> mode, not a panel.
>
> The cheap alternative, if the itch returns: one suspended slot keyed by `EditorTarget`, restored
> when the same file is reopened in the same run. No dock list, no "minimise", one visible editor,
> and ME-124 and ME-160 untouched.

# ME-128 — More than one editor at once, and the dock list that reaches them

| | |
|---|---|
| **Order** | 69 — after ME-125, whose exit gate this makes count more than one save |
| **Depends on** | ME-106 (entry points), ME-124 (`EditorPresence`), ME-125 (`inFlight` is a count) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Let an edit in progress survive opening something else: sessions live in a store keyed by
target, the dock's `Editor` item lists the open ones on hover, and picking one returns to it with its
document and history intact.

**Why now.** Requested 2026-08-18. Today `FolderScreen` holds one `editorTarget` and `EditorHost`
builds the engine, the stored document and the session in `remember(target.path, frameIndex)` — so
leaving the editor disposes all three and the work is gone. That is correct for one editor and is the
only thing standing between the user and "open the other photograph for a second".

## What is already true, checked before writing this

Two of the block's three bullets need **no work**, and finding that out is why this ticket is smaller
than the block reads:

- **The dock already hides while an editor is open.** `FolderScreen` sets
  `dockAccessories.isVisible = viewerMediaId == null && editorTarget == null`. Nothing to add.
- **`EditorPresence` is already a count**, not a flag — its own KDoc says it was made one "so ME-128's
  several live sessions need not re-open this file". It is re-opened here, but to *extend* the count
  into a list, which is the growth it was shaped for.
- **`Icons.Static.Editor` exists** (`square_and_pencil`) and `FloatingDock` draws the Editor item with
  `Icons.Static.Settings` under a stale `// Replace with Editor icon if available`. One line, folded in.

Also checked: `EditorModule` declares `factory { (media: MediaItem) -> EditorSession(media) }` and
**nothing resolves it** — `EditorHost` constructs the session directly. The store is what finally gives
that declaration a caller, or the declaration goes; this ticket picks the first.

## The store, and who disposes what

ME-004 made `EditorSession` a Koin `factory` whose constructing composable **owns it and must
`close()` it**. More than one live session means a composable can no longer be that owner — closing
the editor stops being the same event as ending the session, and that store is what this ticket really
adds. The dock list is its UI.

**Keyed by `EditorTarget`**, which is already `(path, frameIndex)` and already a `data class`: two
frames of one burst are two editors, which is the honest reading after ME-158 made frames separately
editable.

**A session ends on `Done`, on `Cancel`, and on a confirmed Revert-and-leave — never on being hidden.**
That is the whole feature, and it is also the leak: a store that never forgets holds a proxy bitmap per
entry. The cap is that entries are only created by an `Edit` the user asked for, and `ME-123`'s gate
keeps each proxy out of the shared cache.

## Steps

1. **`app/shell/EditorPresence.kt` (edit)** — the count becomes a list of what is open, as a
   `commonMain` value the dock can draw without naming a `feature` type:

   ```kotlin
   @Immutable
   data class OpenEditor(val path: String, val name: String, val isDirty: Boolean)

   var openEditors by mutableStateOf<List<OpenEditor>>(emptyList())
       private set
   ```

   `isOpen` becomes `openEditors.isNotEmpty()`, and `opened`/`closed` are replaced by
   `publish(List<OpenEditor>)` written by the store. **No `EditorSession` here**: `app/shell` is read by
   `core`-level composables and may not learn what a session is. The file is at **60 lines**.

2. **`feature/editor/shell/EditorSessionStore.kt` (new, `jvmMain`)** — the live sessions:

   ```kotlin
   internal class EditorSessionStore(private val factory: EditorEngineFactory) {
       fun entryFor(target: EditorTarget): EditorEntry?
       fun close(target: EditorTarget)
       fun closeAll()
   }
   ```

   `EditorEntry` holds the engine, the stored document and the session. `entryFor` builds on a miss and
   returns the held one on a hit — which is what makes returning to an editor return to *its* document
   and history. Every mutation republishes step 1's list, reading `session.isDirty.value` for each.
   A null entry is a target the factory refused, exactly as `EditorHost` handles today.

3. **`di/EditorModule.kt` (edit)** — `single { EditorSessionStore(get()) }`, and the unused
   `factory { (media: MediaItem) -> EditorSession(media) }` is what the store resolves rather than
   constructing a session inline. `single`, because a per-open store is not a store.

4. **`shell/EditorHost.kt` (edit)** — `remember(target.path, frameIndex) { … }` for the engine, the
   stored document and the session becomes one lookup: `val entry = store.entryFor(target.path,
   frameIndex) ?: return`. The `DisposableEffect` in `EditorPresenceEffect` no longer closes the
   session — it publishes presence only; `session.close()` moves to `EditorSessionStore.close`, called
   from `Done` and from a confirmed `Cancel`. **This is the ownership move**, and it is the step that
   can leak a scope if it is done by halves. The file is at **150 lines**, so this step is also a
   split: the frame state and its dialog plumbing move to `shell/EditorFrameNavigation.kt`.

5. **`shell/EditorPresenceEffect.kt` (edit)** — it stops owning the lifetime and keeps the video
   return (ME-124): `saveAndClose` closes the *visible* editor, which is the one PiP is returning past.

6. **`app/shell/FloatingDock.kt` (edit)** — the Editor item gains `Icons.Static.Editor`,
   `hasBadge = EditorPresence.openEditors.isNotEmpty()` and the hover popup, copying the `Library`
   item's shape verbatim: a `MutableInteractionSource`, `isPopupHovered`, and the 150 ms
   `delayedShowPopup` that keeps the popup alive while the pointer crosses the gap. **The badge only**
   — the item is deliberately *not* tinted the way `Library` tints itself when active: one is "you are
   here", the other is "something is waiting".

7. **`app/shell/OpenEditorsPopup.kt` (new, `commonMain`)** — the list, modelled on `LibraryPopup`: one
   row per `OpenEditor`, the file name, a dirty mark, and a click that raises the target. It takes the
   list and a callback and reads nothing global.

8. **`feature/folder/FolderScreen.kt` (edit)** — `editorTarget` stays exactly what it is, the
   **visible** editor; the dock's callback sets it. Closing sets it to null without disposing, and the
   store is what forgets. `dockAccessories.isVisible` is unchanged.

## Files

`app/shell/EditorPresence.kt` (edit), `app/shell/FloatingDock.kt` (edit),
`app/shell/OpenEditorsPopup.kt` (new), `feature/folder/FolderScreen.kt` (edit) under
`shared/src/commonMain/kotlin/com/woka/`; `feature/editor/shell/EditorSessionStore.kt` (new),
`feature/editor/shell/EditorFrameNavigation.kt` (new, step 4's split),
`feature/editor/shell/EditorHost.kt` (edit), `feature/editor/shell/EditorPresenceEffect.kt` (edit),
`feature/editor/di/EditorModule.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit),
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit).

**Nine files is over the eight this project aims at.** The line that would split it is between step 5
and step 6 — the store and the list — and it is not taken, because a store nothing can reach is a
change with no observable effect and no way to be tried by hand.

## Verification

- `jvmTest/unit/EditorPresenceTest.kt` (edit) — `openEditors` reflects what was published, `isOpen`
  follows it, and publishing an empty list clears `saveAndClose`;
- `jvmTest/integration/EditorSessionStoreTest.kt` (new), the ticket's real test:
  - `entryFor` twice on one target returns the **same** session, and its `canUndo` survives the round
    trip — which is the whole feature;
  - two targets are two sessions with independent documents;
  - two frames of one file are two entries (`EditorTarget(path, 1)` ≠ `EditorTarget(path, 3)`);
  - `close` closes the session's scope and drops the entry; a later `entryFor` builds a fresh one;
  - `closeAll` leaves no live scope — asserted through `session.isActive`, not through the map;
- `jvmTest/ui/OpenEditorsDockUiTest.kt` (new) — no badge and no popup with nothing open; a badge with
  one; hovering lists both names with two; clicking a row raises that target once;
- `jvmTest/ui/EditorShellUiTest.kt`, `EditorRevertUiTest.kt`, `EditorKeysUiTest.kt`,
  `EditorLeavesForVideoUiTest.kt`, `EditorFrameStripUiTest.kt` — unchanged and still green;
- `jvmTest/integration/EditorModuleTest.kt` (edit) — the store resolves, and resolving it twice answers
  the same instance.

## Done when

- opening a second photograph leaves the first editor's document and history intact, and returning to
  it through the dock shows them;
- the `Editor` dock item carries its own icon and a badge dot whenever something is open, and is not
  tinted for it;
- a session is closed by `Done` and by `Cancel`, and by nothing else — `grep -rn "session.close()"
  shared/src/jvmMain` hits `EditorSessionStore` only;
- an editor left open and the application closed does not leak a scope (`closeAll` on exit, through
  ME-125's gate);
- a single-editor session behaves exactly as it does today;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
