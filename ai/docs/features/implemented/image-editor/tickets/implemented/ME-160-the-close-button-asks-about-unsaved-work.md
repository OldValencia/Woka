# ME-160 — The close button asks about unsaved work

| | |
|---|---|
| **Order** | 69 — after ME-125, before ME-128 |
| **Depends on** | ME-125 (`inFlight` and `ExitWhenQuiet`), ME-124 (`EditorPresence`), ME-158 (`DiscardChangesDialog(body =)`) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make the window's close button ask **Save / Don't save / Cancel** when an editor is open
with unsaved work, and route `Save` through ME-125's gate so the write lands before the process ends.

**Why now.** Found by hand 2026-08-31: open the editor, draw a rectangle, press the window's close
button — Woka quits with no question and the edit is gone. ME-125 is not at fault and did not regress;
it gates a save that is *already running*, and in this sequence none ever started. What the sequence
exposes is that the exit door asks the editor nothing at all.

Every other way of throwing an edit away already asks. `Cancel` and `Esc` go through
`EditorHost.leave`, which raises `DiscardChangesDialog` when `isDirty`; ME-158 made a frame change ask
the same question through the same component; ME-124 made the return from the video window save on the
way out. The window's close button is the one door with no opinion, and it is the most destructive of
them — the others lose an edit, this one loses the process with it.

**Three buttons, not two, departing from the dialog the editor already has.** `DiscardChangesDialog`
offers `Discard` / `Keep editing`, which is the right pair when the editor stays up: keeping is
*continuing to edit*. On quit there is a third answer — write it and then quit — and folding that into
"keep editing" would make the user press `Cancel`, press `Done`, and then press close again, watching
for a pill to disappear. Asked by hand 2026-08-31, over the silent save ME-124's decision log would
otherwise have licensed.

## The seam, and why the question is not asked in `app/`

`ExitWhenQuiet` may not learn whether a document is dirty. ME-124 says why in `EditorPresence`'s own
KDoc: a file opened and never touched still has a document — the recipe it already carried (ME-134) —
so dirtiness is the editor's answer to give and nobody else's. The exit door therefore *asks* and
*waits*; the editor *answers*.

```
main.kt          both doors → AppExitRequest.request()
AppExitRequest   requested: Boolean · verdict: ExitVerdict? — the handshake, app/shell
ExitWhenQuiet    no editor open → today's behaviour. Editor open → wait for a verdict,
                 then for inFlight == 0, then exit. Cancel clears the request instead.
EditorExitEffect the editor's side: not dirty → answers Leave at once; dirty → raises the dialog
```

`AppExitRequest` rather than a third field on `EditorPresence`: that object is about *presence* and
what to invoke on the way out, and an exit state machine parked in it would make one object two.

## Steps

1. **`core/ui/dialogs/ChoiceDialog.kt` (new, `commonMain`)** — `@Composable fun ChoiceDialog(title,
   onSave, onDiscard, onCancel, width, saveText, discardText, cancelText, content)`: `AbstractDialog`
   with three `DialogButton`s in one `Row` aligned `End` — cancel in `textSecondary`, discard in
   `danger`, save in `accent` — and `onDismissRequest = onCancel`.

   **Its own file, not a fifth function in `Dialogs.kt`.** That file is at **141 lines** and a third
   button pushes it past 150. Every existing dialog there stays as it is; this reuses
   `AbstractDialog`, `DialogTitle` and `DialogButton` and adds no chrome.

2. **`app/shell/AppExitRequest.kt` (new, `commonMain`)** — an `@Stable object` beside
   `EditorPresence`:

   ```kotlin
   enum class ExitVerdict { Save, Leave }

   var requested by mutableStateOf(false)
       private set
   var verdict by mutableStateOf<ExitVerdict?>(null)
       private set

   fun request()
   fun answer(verdict: ExitVerdict)
   fun cancel()   // clears both — the next close button press asks again
   ```

   `cancel()` clearing `requested` is the whole reason this is state and not a parameter: a flag left
   true is a `LaunchedEffect(requested)` that will not restart, and the second press of the close
   button would do nothing at all.

3. **`app/shell/AppExit.kt` (edit)** — `ExitWhenQuiet` gains the editor branch before the count it
   already waits on:

   - `EditorPresence.isOpen` false → exactly today's path, unchanged;
   - open → `snapshotFlow { AppExitRequest.verdict }.first { it != null }` and then, for **either**
     verdict, the `inFlight` wait ME-125 built. **`AppExitRequest` carries no action**: the editor
     runs its own save in step 6 and answers `Save` only once `saveAction` has started, so by the time
     this resumes the count is already 1 and the existing wait is the whole of the `Save` branch;
   - the grace `delay` is merged into the `inFlight` wait only, never into the wait for a verdict, so
     a dialog nobody answers does **not** quit the application after five seconds. The cap bounds the
     *write*, never the question.

   The file is at **71 lines**.

4. **`feature/editor/shell/EditorExitEffect.kt` (new, `jvmMain`)** — `@Composable fun
   EditorExitEffect(isDirty: Boolean, onSave: () -> Unit, askingToLeave: MutableState<Boolean>)`:
   a `LaunchedEffect(AppExitRequest.requested)` that answers `Leave` immediately when the document is
   clean and sets `askingToLeave` when it is not.

   Beside `EditorPresenceEffect.kt` (48 lines) and shaped like it, for the reason that file exists:
   `EditorHost` is at **exactly 150 lines** and may not grow by a statement. This is step 4's own
   split, not an optional tidy.

5. **`feature/editor/shell/EditorHostDialogs.kt` (edit, 43 lines)** — a fourth question,
   `askingToLeave`, drawn with the new `ChoiceDialog`: title `"Save before quitting?"`, body
   `"This edit has not been saved — the original file is untouched either way."`, buttons
   `Save` / `Don't save` / `Cancel`.

6. **`feature/editor/shell/EditorHost.kt` (edit)** — `var askingToLeave by remember { … }`,
   `EditorExitEffect(isDirty, done, …)` beside `EditorPresenceEffect`, and the three answers wired
   into `EditorHostDialogs`: `Save` → `done()` then `AppExitRequest.answer(Save)`; `Don't save` →
   `answer(Leave)`; `Cancel` → `AppExitRequest.cancel()` and clear the flag. **`done` is
   `saveAction`, not `saveAndClose`** — the latter is `leaveForVideoAction` and its pill reads "before
   returning to the video", which is the wrong sentence for a quit.

   The file is at its limit; if these lines push it past 150, the state moves into step 4's file and
   the host reads it from there.

7. **`feature/editor/shell/EditorHostControls.kt` (new, `jvmMain`)** — added while shipping, because
   step 6's one call line did carry `EditorHost` to 151. `leave`, `done`, `revert`, the four booleans
   behind the questions and the `EditorHostDialogs` call move here behind a
   `content: @Composable (EditorHostActions) -> Unit` slot; the host keeps building the editor —
   engine, document, session, frame — and stops acting on it. 151 → 126.

## Files

`shared/src/commonMain/kotlin/com/woka/core/ui/dialogs/ChoiceDialog.kt` (new),
`shared/src/commonMain/kotlin/com/woka/app/shell/AppExitRequest.kt` (new),
`shared/src/commonMain/kotlin/com/woka/app/shell/AppExit.kt` (edit),
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorExitEffect.kt` (new),
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHostDialogs.kt` (edit),
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHost.kt` (edit),
`desktopApp/src/main/kotlin/com/woka/main.kt` (edit — both doors call `AppExitRequest.request()`);
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §4.1, a §9 row, the status table).

## Verification

- `jvmTest/unit/AppExitRequestTest.kt` (new) — `request`/`answer`/`cancel` move the two fields;
  `cancel` clears `requested`, which is what lets a second press ask again;
- `jvmTest/ui/AppExitUiTest.kt` (edit) — the four ME-125 cases unchanged and still green, plus: with
  `EditorPresence.isOpen` true and no verdict, `ExitWhenQuiet(true)` does **not** exit, and the grace
  period does not make it;
- `jvmTest/ui/EditorExitUiTest.kt` (new), the ticket's real test, over `EditorUnderShell` and
  `FixtureEngine`:
  - a **clean** editor and a close request quits with no dialog on screen,
  - a **dirty** one draws the dialog and does not quit,
  - `Save` puts a document in `FixtureEngine.saveRequests` and quits only after the gate opens —
    driven with `savingGate`, so the two halves are visibly ordered,
  - `Don't save` quits at once and leaves `saveRequests` empty,
  - `Cancel` quits nothing, dismisses the dialog, and a **second** request re-asks;
- `jvmTest/ui/EditorShellUiTest.kt`, `EditorRevertUiTest.kt`, `EditorFrameDiscardUiTest.kt` — unchanged
  and still green, which is what says the fourth question did not disturb the other three;
- `jvmTest/integration/PipReturnTest.kt` — unchanged: ME-124's door does not go through this gate.

## Done when

- the close button over a dirty editor asks, and the window is still there behind the dialog;
- `Save` writes the recipe into the file and the application quits after the write, not before;
- `Don't save` quits immediately and writes nothing;
- `Cancel` leaves the editor exactly as it was, and pressing close again asks again;
- a clean editor, and no editor at all, quit with no dialog;
- `grep -rn "exitApplication" desktopApp/src` still hits one call;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
