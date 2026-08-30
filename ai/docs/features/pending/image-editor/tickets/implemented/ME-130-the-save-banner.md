# ME-130 — The save banner, and the save that outlives the editor

| | |
|---|---|
| **Order** | 61 — after ME-127; the first of the four that changes behaviour |
| **Depends on** | ME-132 (`Done` writes the recipe), ME-135 (readers notice a save) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make `Done` close the editor **immediately** and run the save behind it, reported by a pill in
the shell with three states — saving, saved, failed — in the shape `FrameExportBanner` already has.

**Why now.** §4.5 has said "closes immediately and runs the save in the background" since 2026-08-18 and
the code does the opposite: `EditorHost.done` launches into `rememberCoroutineScope()`, **awaits**
`engine.save`, and only then calls `onClose()`. So the editor sits on screen, unresponsive and with no
indication that anything is happening, for the length of the write — milliseconds for bucket A, a full
native decode and encode for bucket B — and there is no report of a failure at all, because the one
place that could show it has already been dismissed by the time the result arrives.

**What is left of the backlog block.** Everything about *choosing* — a `Done` menu, Save a Copy,
Overwrite…, a destructive confirmation, a session-scoped "don't ask again" — was deleted by §5 on
2026-08-19. There is one save, one destination, nothing to confirm. The **reporting** is what survives,
and it survives precisely because the editor is gone before the answer exists.

## The scope the save runs in

**This is the half that is not UI, and it has to be settled here rather than assumed.**
`rememberCoroutineScope()` is cancelled when `EditorHost` leaves the composition, so "close first, then
save" in that scope is a save cancelled between the temp file and the atomic move. No file is
half-written — the move is the last step (§5.2) — but a `.tmp` is left beside the photograph and the
edit is silently lost.

So the save moves to a scope owned by the **status object**, built exactly as `PipManager.pipScope` is
and for the same reason: the work outlives every composable that could hold it.
`CoroutineScope(Dispatchers.Main + SupervisorJob())`, matching `EditorSession` — `engine.save` already
moves itself to `Dispatchers.IO`, so what stays on Main is the status write and the `onSaved` callback,
which touches Compose state and a ViewModel and must not arrive off the main thread.

**This is not ME-125.** That ticket asks what happens to an in-flight save when the *application*
closes, and a `SupervisorJob` in an object does not answer it. What this settles is the smaller and
unavoidable question — which scope, so the save is not cancelled by its own success — and it is written
down here so ME-125 starts from a stated answer rather than from "whatever the coroutine scope happens
to do".

## One pill, two producers

`FrameExportBanner` is the right shape and the wrong scope to reuse whole: it is bound to
`FrameExportStatus` and it aligns *itself* to the bottom centre of the window, so a second copy would
draw a second pill in the same place. An export and a save can be live together — export frames from
the grid, then edit a photograph and press `Done` — and two pills at `BottomCenter` is one pill with
the other underneath it.

The split is therefore: the **pill** becomes a component, each **producer** keeps its own status object
and its own settle timer, and one host in `app/shell` stacks whatever is live. `core/` may not import a
feature, which is why the editor's banner is a `commonMain` composable under `feature/editor/` and the
host that knows about both is in `app/` — the layout's own rule, not a preference.

## Steps

1. **`core/ui/components/StatusPill.kt` (new)** —

   ```kotlin
   @Composable
   fun StatusPill(message: String?)
   ```

   The body lifted verbatim out of `FrameExportBanner`: `AnimatedVisibility` with the slide-and-fade,
   the 10.dp `RoundedCornerShape`, `surfaceElevated`, the 1.dp `border`, the 13.sp `Medium` label.
   **No `Box(fillMaxSize)` and no alignment** — placement belongs to the host. `SETTLE_MS = 2600L`
   moves here as the one place the duration is written.

2. **`core/ui/components/FrameExportBanner.kt` (edit)** — keeps its name and its `LaunchedEffect`
   settle rule (only a message that does not start with `"Exporting"` clears itself), loses its `Box`
   and its pill body, and calls `StatusPill(message)`. Behaviour identical; it is now a caller.

3. **`feature/editor/EditorSaveStatus.kt` (new, `commonMain`)** — the object, in the package
   `EditorEntry.kt` and `NativeMediaEditor.kt` already occupy, because `app/` and the editor's `jvmMain`
   shell both have to name it:

   ```kotlin
   @Stable
   object EditorSaveStatus {
       val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
       var message by mutableStateOf<String?>(null)
           private set
       fun saving(name: String)   // "Saving photo.jpg…"
       fun saved(name: String)    // "Saved photo.jpg"
       fun failed(reason: String) // EditSaveResult.Failed.reason, verbatim
       fun clear()
   }
   ```

   The file is named, not just "Saved": the editor is gone by the time this is read, so a bare word
   would be a pill with no subject. `failed` takes the reason **as it is** — `ImageEditWrites` and
   `ImageSaveJob` already write sentences aimed at the user ("The original is untouched.", "Nothing was
   written.") — and re-wording them here would create a second vocabulary for the same failures.

   Its doc carries the one limitation: **one message, one save**. Two editors open at once is ME-128,
   and a list is what that ticket would bring; until then a second save overwrites the first's message
   and nothing worse, because the messages are the only shared state.

4. **`feature/editor/EditorSaveBanner.kt` (new, `commonMain`)** — reads `EditorSaveStatus.message`,
   settles anything that is not a `"Saving"` message after `SETTLE_MS`, draws `StatusPill`. The mirror
   of step 2, four lines of logic.

5. **`app/shell/StatusBanners.kt` (new)** — one `Box(fillMaxSize, BottomCenter)` holding a `Column`
   with `spacedBy(8.dp)` and `bottom = 24.dp` padding: `FrameExportBanner()` then `EditorSaveBanner()`.
   In `app/shell` because it is the only layer permitted to name both a `core` component and a
   `feature`, which is what "app wires" means here.

6. **`app/App.kt` (edit)** — `FrameExportBanner()` becomes `StatusBanners()`, its comment updated to
   say what now sits above every screen and why. One line plus its comment; the file is at 135 lines.

7. **`feature/editor/shell/EditorHost.kt` (edit)** — `done` inverts:

   ```kotlin
   val done = {
       val document = session.document.value   // read before the session is closed
       val name = file.name
       onClose()
       EditorSaveStatus.saving(name)
       EditorSaveStatus.scope.launch {
           when (val result = engine.save(document)) {
               is EditSaveResult.Saved -> { EditorSaveStatus.saved(name); onSaved(result.path) }
               is EditSaveResult.Failed -> EditorSaveStatus.failed(result.reason)
               EditSaveResult.NothingToDo -> EditorSaveStatus.clear()
           }
       }
       Unit
   }
   ```

   Three things this has to get right, each a defect if it does not:

   - **The document is read before `onClose()`.** `onClose` clears `editorTarget`, the exit animation
     runs, `EditorHost` disposes and `session.close()` cancels the session's scope. Reading
     `session.document.value` after that is reading a closed session.
   - **`engine` is captured, not resolved.** It is a `remember`ed object with a `stamp` and a lazy
     `sourceSize`; the coroutine holds it alive past the composition, which is exactly what is wanted.
   - **`NothingToDo` shows no banner** and clears any stale one. An empty document over a file that
     carries nothing is not an event — `EditSaveResult`'s own doc says so.

   The file is at **144 lines** and this is roughly net-zero in code and about +8 in comment. If
   `wc -l` reports over 150, the split is `done`, `revert` and `leave` into `shell/EditorActions.kt`
   as three builders taking the session, the engine and the callbacks — as its own numbered step, not
   as cramming.

8. **`feature/editor/shell/EditorTokens.kt` (edit)** — delete `Motion.BANNER_MS`. ME-012 declared it
   for this ticket and nothing has ever read it; the duration now lives in `StatusPill` beside the
   animation it times, and a second copy in a token object is a value that drifts. Confirmed by
   `grep -rn "BANNER_MS"` returning nothing.

## What the user sees, including the part that looks wrong and is not

`Done` → the editor slides out over 200 ms → the frozen viewer underneath is live again, **still drawing
the pre-edit picture** → the save lands, `evictCachesFor` runs and ME-135's signal fires → the viewer
re-reads and the edit appears → the pill says "Saved photo.jpg" and takes itself away 2.6 s later.

The gap between the third and fourth steps is the whole reason the banner exists. For bucket A it is a
few hundred bytes and one atomic move, so it is not perceptible; the banner is there for the case where
it is, and for the case where the answer is "Nothing was written".

## Files

`core/ui/components/StatusPill.kt` (new), `core/ui/components/FrameExportBanner.kt` (edit),
`feature/editor/EditorSaveStatus.kt` (new), `feature/editor/EditorSaveBanner.kt` (new),
`app/shell/StatusBanners.kt` (new), `app/App.kt` (edit) — all under
`shared/src/commonMain/kotlin/com/woka/`; `feature/editor/shell/EditorHost.kt` (edit),
`feature/editor/shell/EditorTokens.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/`.

## Verification

- `jvmTest/unit/EditorSaveStatusTest.kt` (new) — `saving`/`saved`/`failed`/`clear` produce the four
  message states; `failed` passes its reason through **unchanged**; `clear` after `saved` leaves null;
- `jvmTest/ui/EditorSaveBannerUiTest.kt` (new), the ticket's real test — under `EditorUnderShell`,
  press `Done` on a dirty document and assert, in order:
  - the `editor-screen` tag is gone **before** the write has been awaited by the test,
  - the pill is displayed with the "Saving" text, then with "Saved",
  - after the settle delay the pill is gone;
- `jvmTest/ui/EditorSaveBannerUiTest.kt` — the failure path, driven by a fixture engine whose `save`
  answers `Failed("…Nothing was written.")`: the editor still closes, and the pill shows that sentence;
- `jvmTest/ui/EditorSaveBannerUiTest.kt` — the cancellation this ticket exists to prevent: a fixture
  engine whose `save` suspends past the editor's disposal still reaches its `Saved` branch and the
  banner shows it. Without step 7's scope that test never completes;
- `jvmTest/integration/EditorSaveTest.kt`, `ReopenEditTest.kt`, `RevertTest.kt` — unchanged and still
  green, which is what says the write itself was not touched;
- `jvmTest/ui/EditorShellUiTest.kt`, `EditorTopBarUiTest.kt` — still green.

## Done when

- `Done` removes the editor from the tree before the save is awaited;
- a save that outlives the editor completes, and its result reaches the banner;
- the three states read "Saving photo.jpg…", "Saved photo.jpg" and the failure's own sentence;
- an export banner and a save banner shown together do not overlap;
- `NothingToDo` shows nothing;
- `grep -rn "BANNER_MS" shared/` returns nothing;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
