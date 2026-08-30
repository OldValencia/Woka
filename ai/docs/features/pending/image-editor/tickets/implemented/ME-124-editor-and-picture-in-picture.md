# ME-124 — Returning from the video may not destroy an open editor

| | |
|---|---|
| **Order** | 62 — after ME-130, whose save scope and banner this ticket is built on |
| **Depends on** | ME-106 (the editor's mount), ME-130 (`EditorSaveStatus`, its scope and its pill) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make "return to the main window" — `Escape`, `F`, and the two exit controls — **save the open
editor and close it** on the way through, instead of destroying it with its unsaved work, and say so in
the pill that already reports saves.

**Why now.** §10 has carried this as an undefined gap since 2026-08-18, and it is not an awkwardness:
it is a keypress in one window silently deleting work in another.

## The defect, traced

`Escape` and `F` in the video window, `MediaViewerFullscreenSurface.kt:76` and
`MediaViewerFullscreenWindow.kt:129` all call `PipManager.closePip(returnToMain = true)`, which does
`Router.replaceTop(Screen.Folder(path, openViewerForId, nonce = System.currentTimeMillis()))`.

`Screen.Folder` is a data class carrying that `nonce`, and `ScreenHost` is an
`AnimatedContent(targetState = currentScreen)` — so a fresh nonce is a **different target**: the whole
`FolderScreen` subtree is rebuilt and the old one disposed. `editorTarget` is `remember`ed inside
`FolderScreen` (`FolderScreen.kt:44`).

**So an open editor holding unsaved work is destroyed by a keypress in another window, with no
`DiscardChangesDialog` and no banner.** That dialog is reached only through `leave`, which a route
change never calls.

Note the reach: two of the four callers are the **fullscreen** window, not picture-in-picture. Guarding
the *return* rather than the keys covers both for free.

## The rule, and the three readings that were not chosen

**Chosen: the return happens, and the editor is saved and closed on the way out.** Editing in Woka is
non-destructive — the edit is a recipe inside the file — so saving costs one Revert to undo, and by
ME-134 reopening the file replays the recipe into a history that Undo can walk back to the original. A
save is the only thing `Done` does anyway; there is no target to choose and no copy to name (§5).

- **"The keyboard belongs to whichever window has focus"** — which is what the OS already does, and is
  not the question. PiP is a separate `Window` with its own `onPreviewKeyEvent`; its keys never touch
  `GlobalShortcutState`. Focus decides who *hears* a key. It does not decide what the key *acts on*, and
  `Escape` there acts on the main window by design.
- **"Refuse to open the editor while a video window is up"** — rejected: four doors reach the editor
  (`FolderItemContextMenu.kt:81`, `GlobalControlButtons.kt:113`, `MediaViewerFrame.kt:52`, the viewer's
  key handler), `isEditable` is a question about a *file* and lives in `commonMain/feature/editor` where
  `PipManager` cannot be named, and it answers a data-loss bug by deleting a feature.
- **"Ask first — pause the video and confirm"** — rejected after being designed. A Compose `Dialog` is
  modal to its own window only, so a question raised in the video window cannot freeze the main one.
  The user can therefore ignore it, keep editing, press `Done`, and answer "save" ten minutes later —
  at which point the captured action either does nothing or **writes a stale document over a newer
  one**. A confirmation that can be answered after the world moved is a race, not a safeguard.

## Steps

1. **`app/shell/EditorPresence.kt` (new)** — the one thing both features share:

   ```kotlin
   @Stable
   object EditorPresence {
       private var open by mutableStateOf(0)
       val isOpen: Boolean get() = open > 0

       /** Set by the editor while it is mounted; null when none is. */
       var saveAndClose: (() -> Unit)? = null
           private set

       fun opened(action: () -> Unit) { open++; saveAndClose = action }
       fun closed() { open--; if (open == 0) saveAndClose = null }
   }
   ```

   **A count, not a flag**, so ME-128's several live sessions need not re-open this file. In `app/shell`
   beside `GlobalShortcutState` for the reason that type is there: shell state two features read.
   Not in `core/`, which may not know an editor exists, and not in either feature, which may not know
   about the other. An object rather than a `CompositionLocal` because `PipManager.closePip` is a plain
   function and cannot read one.

   **One lambda, not a pair of facts.** The editor is asked to *leave*, and decides for itself whether
   that means writing: a session that is not dirty closes without a save. Exposing `isDirty` and letting
   the caller decide would write a file that was opened and not touched — its document is not empty, it
   holds the recipe from last time (ME-134), so `save` would answer `Saved` and the pill would report an
   edit that never happened.

2. **`feature/editor/EditorSaveStatus.kt` (edit)** — a second finished message:

   ```kotlin
   fun savedLeaving(name: String) { message = "Saved $name before returning to the video" }
   ```

   `saved` is unchanged and stays what `Done` produces. This one exists because the user is not in the
   editor when it appears — they are in the folder the film is in, and a bare "Saved photo.jpg" there is
   a pill with no cause. It settles by the same rule: it does not start with `SAVING_PREFIX`.

3. **`feature/editor/shell/EditorActions.kt` (edit)** — `saveAction` gains
   `leavingForVideo: Boolean = false` and picks between `EditorSaveStatus.saved` and
   `savedLeaving` for the `Saved` branch. Nothing else about it changes: the document is still read
   before `onClose`, the write still runs in `EditorSaveStatus.scope`, and that scope is what makes this
   ticket possible at all — the route change that follows disposes the composition the save was started
   from.

4. **`feature/editor/shell/EditorHost.kt` (edit)** — publish and withdraw the action in the
   `DisposableEffect` that already owns the session's lifetime:

   ```kotlin
   val leaveForVideo = saveAction(engine, session, file.name, onClose, onSaved, leavingForVideo = true)
   DisposableEffect(session) {
       EditorPresence.opened(leaveForVideo)
       onDispose { EditorPresence.closed(); session.close() }
   }
   ```

   The lambda closes over the session and the engine and reads `session.document.value` **when it is
   called**, never at publication — which is what makes a stale write impossible. The file is at 138
   lines; if this pushes it past 150, the split is `revert` and `leave` joining `done` in
   `EditorActions.kt`, as its own numbered step.

5. **`feature/viewer/pip/PipManager.kt` (edit)** — the router branch of `closePip` saves first:

   ```kotlin
   if (returnToMain && activeItem != null) {
       EditorPresence.saveAndClose?.invoke()
       … existing replaceTop and transition …
   }
   ```

   Order matters and is the whole ticket: `saveAndClose` closes the editor synchronously — `onClose`
   clears `editorTarget` — and leaves the write running in a scope that outlives the composition. Only
   then does the route change rebuild `FolderScreen`. The comment says what it is protecting, because
   the hazard is invisible from here: `replaceTop` with a fresh nonce rebuilds that subtree, and
   `editorTarget` lives inside it.

   The `onSaved` callback fires into the `FolderScreen` that is about to be replaced, so its
   `scrollToMediaId` lands nowhere. That is correct rather than tolerated: the destination is the film's
   folder, not the photograph's.

6. **`feature/viewer/shell/MediaViewerShortcutEffect.kt` (edit)** — `onDispose` restores the handler
   captured on entry instead of writing `null` (`MediaViewerShortcutEffect.kt:71`), symmetric with
   `EditorKeys`:

   ```kotlin
   val previous = globalShortcuts.onPreviewKey
   …
   onDispose { globalShortcuts.onPreviewKey = previous }
   ```

   Behaviour is identical today — the viewer is the first claimant, so `previous` is null — and the
   asymmetry is what makes "whoever disposes last wins" possible at all. Two lines.

7. **`../image-editor.md` §10 (edit)** — the "Editor plus PiP" gap is answered rather than listed: the
   rule, the route hazard it closes, and the fact that the fullscreen window shares the path.

8. **`../image-editor.md` §9 (edit)** — a row dated 2026-08-30: *Editor open, and the video window asks
   to go back? → **The return happens; the editor is saved and closed on the way, and the pill says
   so.*** Why: focus decides who hears a key, not what it acts on; a confirmation across two windows
   cannot be made modal and can be answered after the document moved; and a save here is one Revert away
   from undone.

9. **`backlog.md` (edit)** — remove the ME-124 block.

## Files

`app/shell/EditorPresence.kt` (new), `feature/editor/EditorSaveStatus.kt` (edit) under
`shared/src/commonMain/kotlin/com/woka/`; `feature/editor/shell/EditorActions.kt` (edit),
`feature/editor/shell/EditorHost.kt` (edit), `feature/viewer/pip/PipManager.kt` (edit),
`feature/viewer/shell/MediaViewerShortcutEffect.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/`; `ai/docs/features/pending/image-editor/image-editor.md` (edit);
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit).

## Verification

- `jvmTest/unit/EditorPresenceTest.kt` (new) — `isOpen` false at rest, true after one `opened`, still
  true after a second, false only when both have `closed`; `saveAndClose` is null at rest and null again
  once the last has closed;
- `jvmTest/unit/EditorSaveStatusTest.kt` (edit) — `savedLeaving` names the file and says why, and is
  distinct from `saved`;
- `jvmTest/ui/EditorLeavesForVideoUiTest.kt` (new) — under the `EditorUnderShell` scene
  `EditorSaveBannerUiTest` already builds: with a dirty document, invoking
  `EditorPresence.saveAndClose` removes the `editor-screen` tag **before** the write has answered, and
  the pill then reads "Saved photo.jpg before returning to the video";
- the same file — a **clean** session: invoking it closes the editor and leaves
  `EditorSaveStatus.message` null, because a save that was not needed was not made;
- the same file — the stale-write case this design exists to prevent: the document is changed *after*
  `saveAndClose` was published and before it is invoked, and the engine receives the **new** document;
- `jvmTest/integration/PipReturnTest.kt` (new) — over `StartupRouteTest`'s Koin-and-router setup:
  `closePip(returnToMain = true)` with no editor open replaces the top screen exactly as today, and with
  `EditorPresence.opened { … }` it both runs the action and replaces the screen, in that order;
- `jvmTest/ui/ViewerNavigationUiTest.kt`, `EditorShellUiTest.kt`, `EditorKeysUiTest.kt`,
  `EditorSaveBannerUiTest.kt` — unchanged and still green.

## Done when

- `Escape` or `F` in the video window, with a dirty editor open, returns to the film's folder **and**
  the edit is in the file;
- the pill in the main window reads "Saved *name* before returning to the video";
- a clean editor closes with no save and no pill;
- with no editor open, every video-window shortcut and the return route behave exactly as today;
- both the viewer and the editor restore the shortcut handler they found;
- §9 and §10 record the rule with its date;
- the ME-124 block is gone from `backlog.md`;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
