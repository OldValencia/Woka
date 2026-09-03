# ME-125 — Closing the window waits for the save

| | |
|---|---|
| **Order** | 68 — after ME-123, before ME-128 |
| **Depends on** | ME-130 (`EditorSaveStatus.scope` and the banner), ME-132 (`Done` writes the recipe) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make the two exit doors hold the window open while a save is in flight, show the pill that
says so, and give up after a bounded wait — so `Done` immediately followed by closing Woka writes the
edit instead of losing it.

**Why now.** ME-130 answered half of this and said so in its own KDoc: the save runs in
`EditorSaveStatus.scope`, `Main + SupervisorJob`, so it survives the editor that started it. What a
`SupervisorJob` in an object does **not** survive is the process. `Done` closes the editor first and
writes second (§4.5), so the window is already back on the grid while the write is running — which is
exactly the moment a user reaches for the close button.

**Nothing is ever half-written**, and that is why this is a small ticket rather than a data-loss one.
`writeAtomically` puts a temp beside the target and does one `ATOMIC_MOVE` last, with three retries
120 ms apart. A process killed mid-write leaves a `.tmp` and an untouched photograph. What is lost is
the *edit*, silently, with a pill that was up when the window went away.

## The two doors, and why both

`main.kt` has `onCloseRequest = ::exitApplication` on the `Window`, **and** passes
`onCloseWindow = { exitApplication() }` into `App` for the title bar's own close button. They are two
call sites for one decision, and a gate on either alone is a gate a user walks around.

## What "wait" means, and what it must not be

**Not a blocking wait.** `writeAtomically` sleeps on an IO thread by design; holding the EDT while it
does would be a frozen window with no pill on it, which is worse than the loss. The exit is *deferred*
instead: the request sets a flag, the composition keeps running, the banner keeps drawing "Saving
photo.jpg…", and a `LaunchedEffect` calls `exitApplication` when the count reaches zero.

**Bounded, and it exits anyway.** A save that has not finished in `EXIT_GRACE_MS` gets no veto: the
user asked to close, the original is intact whatever happens, and an application that will not quit is
a worse defect than a lost recipe. 5000 ms — a recipe write is a few hundred bytes and finishes in
milliseconds, and bucket B's re-encode is the only thing that can approach the cap (§5.1, and bucket B
is empty today).

**`.tmp` sweeping is out of scope**, deliberately. A leftover temp is a file beside the photograph
that no reader lists — `LocalMediaRepository` filters by media extension — and inventing a sweeper for
a case this ticket makes rare is work with no measurement behind it.

## Steps

1. **`EditorSaveStatus.kt` (edit)** — the count, beside the message:

   ```kotlin
   var inFlight by mutableStateOf(0)
       private set

   fun started() { inFlight++ }

   fun finished() { inFlight = (inFlight - 1).coerceAtLeast(0) }
   ```

   A count rather than a boolean for the reason `EditorPresence` is one: ME-128 makes two saves at
   once reachable, and a flag would clear on the first to finish. `coerceAtLeast` so a double
   `finished()` cannot drive it negative and wedge the exit open. The file is at **79 lines**.

2. **`shell/EditorActions.kt` (edit)** — `saveAction` brackets its launch:

   ```kotlin
   EditorSaveStatus.started()
   EditorSaveStatus.scope.launch {
       try { … } finally { EditorSaveStatus.finished() }
   }
   ```

   **`started()` outside the coroutine and `finished()` in a `finally`.** Outside, because a launch on
   `Dispatchers.Main` from the main thread does not run until the current frame yields, and the close
   request can land in that window. In a `finally`, because a cancelled or throwing save must not leave
   the application unable to quit. The file is at **115 lines**.

3. **`app/shell/AppExit.kt` (new, `commonMain`)** — `@Composable fun ExitWhenQuiet(requested: Boolean,
   onExit: () -> Unit)`: a `LaunchedEffect(requested)` that returns immediately when nothing is in
   flight, and otherwise waits for `snapshotFlow { EditorSaveStatus.inFlight }.first { it == 0 }` under
   `withTimeoutOrNull(EXIT_GRACE_MS)` before calling [onExit] either way.

   In `app/shell` rather than in `feature/editor/`, because the thing being closed is the application:
   `desktopApp` may name `app/`, and a launcher reaching into a feature to ask whether it may quit is
   the dependency this avoids.

4. **`desktopApp/src/main/kotlin/com/woka/main.kt` (edit)** — one `var exitRequested by remember {
   mutableStateOf(false) }` inside the `Window`, both doors setting it instead of calling
   `exitApplication` (`onCloseRequest = { exitRequested = true }`, `onCloseWindow = { exitRequested =
   true }`), and `ExitWhenQuiet(exitRequested) { exitApplication() }` in the content.

5. **`EditorSaveStatus.kt` (edit)** — its KDoc's *"This is not ME-125"* paragraph is replaced by what
   ME-125 decided, because a file that says a question is open after it has been answered is how a
   stale sentence survives.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/EditorSaveStatus.kt` (edit),
`shared/src/commonMain/kotlin/com/woka/app/shell/AppExit.kt` (new),
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorActions.kt` (edit),
`desktopApp/src/main/kotlin/com/woka/main.kt` (edit);
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and the status table),
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit — remove the ME-125 block).

## Verification

- `jvmTest/unit/EditorSaveStatusTest.kt` (edit) — `inFlight` counts up and down, two overlapping saves
  leave it at zero only after both finish, and a stray `finished()` cannot drive it below zero;
- `jvmTest/ui/AppExitUiTest.kt` (new), the ticket's real test:
  - with `inFlight == 0`, `ExitWhenQuiet(true)` calls `onExit` on the first frame;
  - with a save held open by `FixtureEngine`'s `savingGate`, it does **not** call `onExit`; releasing
    the gate makes it call it exactly once;
  - a save that never finishes calls `onExit` after the grace period — driven on a test dispatcher
    with `advanceTimeBy`, never a real five-second wait;
- `jvmTest/ui/EditorSaveBannerUiTest.kt` — unchanged and still green, which is what says the bracket in
  step 2 did not change what the pill reports;
- `jvmTest/integration/AtomicWriteTest.kt` — unchanged: this ticket adds no failure path to the write.

## Done when

- `Done` followed immediately by closing the window leaves the recipe in the file;
- the pill is still on screen while the window is being held, and the window is not frozen;
- a save that hangs does not prevent the application from quitting;
- both exit doors go through the same gate (`grep -rn "exitApplication" desktopApp/src` hits one call);
- `EditorSaveStatus`' KDoc no longer says ME-125 is open;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
