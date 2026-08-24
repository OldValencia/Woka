# ME-009 — Save targets and the atomic write

| | |
|---|---|
| **Order** | 10 |
| **Depends on** | ME-002 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Decide *where* a save lands and write it without ever leaving a half-file behind. No backup
store — §5: the original is protected by not being written to.

**Why now.** It is the one part of this feature that can destroy a user's file, it is shared by both
editors, and it has no UI — so it can be finished and proven before anything can call it.

## Steps

1. `SaveTarget.kt` (commonMain, `feature/editor/core/`): `sealed interface SaveTarget` —
   `Copy(name: String)` and `Overwrite`. `Copy` is the default everywhere; `Overwrite` is only ever
   constructed by an explicit user action, and the KDoc says so.
2. `SaveFileNaming.kt` (commonMain): pure. `fun copyName(baseName: String, ext: String, isTaken:
   (String) -> Boolean): String` → `photo-edited.jpg`, then `photo-edited-2.jpg`, `-3`… Takes a
   predicate rather than a directory, so it is unit-testable with no filesystem. Handles a changed
   extension — a converted HEIC becomes `photo-edited.jpg`, not `photo.heic-edited.jpg` — and a name
   that already ends in `-edited`.
3. `AtomicWrite.kt` (jvmMain, `feature/editor/save/`): `writeAtomically(target: File, bytes:
   ByteArray)` — write `<name>.woka-tmp` beside the target, `Files.move` with `ATOMIC_MOVE` +
   `REPLACE_EXISTING`, 3 retries at 120 ms because the viewer may still hold the file on Windows, and
   delete the temp file on every failure path. **Grep `SettingsStore` first**: if it already
   implements this, lift that implementation into `core/data/` and use it from both rather than
   writing a second one.
4. `SourceStamp.kt` (jvmMain, same package): capture `length` + `lastModified` when the editor opens a
   file, and a `matches(file): Boolean` to check before writing. A save whose source changed on disk
   meanwhile stops and reports it instead of overwriting someone else's change (§5, stale-source
   guard).
5. `OverwriteConsent.kt` (commonMain): a Koin `single` holding one in-memory boolean —
   `shouldAsk(): Boolean` and `rememberForSession()`. Registered in `EditorModule`. The KDoc states
   why it is not a `WokaSettings` field: a persisted opt-out would make destructive overwrite the
   permanent default, and the whole save model exists to prevent that. It resets by virtue of the
   process ending; nothing writes it to disk.
6. `EditSaveResult` (ME-008) gains the written path if it does not have it yet.

## Files

`feature/editor/core/SaveTarget.kt`, `core/SaveFileNaming.kt`, `core/OverwriteConsent.kt` (commonMain,
new), `jvmMain feature/editor/save/AtomicWrite.kt`, `save/SourceStamp.kt` (new),
`core/EditSaveResult.kt` (edit), `feature/editor/di/EditorModule.kt` (edit)

## Done when

- `jvmTest/unit/SaveFileNamingTest.kt`: first copy, second copy, tenth copy, changed extension, a
  source already named `x-edited.jpg`, a name with dots in it;
- `jvmTest/unit/OverwriteConsentTest.kt`: asks by default, stops asking after `rememberForSession()`,
  and a freshly built instance asks again;
- `jvmTest/integration/AtomicWriteTest.kt` under `support/TempStorageRoot.kt`: replacing an existing
  file leaves no `.woka-tmp`; a failure mid-write leaves the original byte-identical; writing while
  the target is held open by another handle succeeds within the retries or fails cleanly;
- `jvmTest/integration/SourceStampTest.kt`: a file touched after the stamp was taken does not match.

## Result — 2026-08-17

`./gradlew.bat :shared:build` green. **548 tests, 0 failures, 0 errors** — 530 before, plus eighteen.

### The grep step 3 demanded: `SettingsStore` had it, and lifting it was not a copy

`SettingsStore.writeToDisk` already wrote a temp beside the target and moved it with `ATOMIC_MOVE` +
`REPLACE_EXISTING`. Two things it does **not** do: retry, and refuse to fall back. On failure it does
`settingsFile.writeText(text)` — a plain, non-atomic overwrite.

That fallback is right for settings and catastrophic for the editor. A damaged `settings.json` is
read once and replaced (there is a test for exactly that); a half-written photograph is the destroyed
original §5 exists to prevent. So what was lifted into `core/data/AtomicFileReplace.kt` is the
**mechanism** — temp beside the target, one atomic move, temp deleted on every failure path — and
each caller keeps its own policy:

| | on failure |
|---|---|
| `SettingsStore` | falls back to a plain overwrite |
| `editor/save/AtomicWrite` | 3 attempts 120 ms apart, then reports failure. **Never** a plain write |

### A test that would have stopped testing, silently

The lifted helper names its temp `<name>.woka-tmp`; `SettingsStore` used `settings.json.tmp`. An
existing assertion in `SettingsPersistenceTest` watches for that temp being cleaned up — against the
old name it would have passed forever by watching a file that can no longer exist. Updated, with the
reason in a comment beside it.

### Steps 1 and 6 were already done

`SaveTarget` and `EditSaveResult.Saved(path)` were both created in ME-008, which needed them for the
`EditorEngine` signature. Nothing to add.

### Why the failure test uses a directory and not an open file handle

The obvious way to force a mid-write failure is to hold the target open. That is **not** portable:
Windows refuses to replace a file with an open handle, macOS allows it — and Woka targets both, so
the test would pass here and fail on the other machine. A non-empty directory cannot be replaced by
a move on any platform, which makes the failure deterministic everywhere.

The retry test asserts the elapsed time is at least 240 ms — two 120 ms gaps between three attempts.
It is the only thing that can catch the retries quietly disappearing, since a failing write looks
identical with and without them.

`copyName` numbers from **2**, because `-edited` is itself the first copy.
