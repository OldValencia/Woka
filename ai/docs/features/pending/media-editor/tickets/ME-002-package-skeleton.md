# ME-002 — Editor package skeleton and DI seam

| | |
|---|---|
| **Order** | 3 |
| **Depends on** | nothing (run it after ME-013 so the tree is settled) |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Create the packages both editors live in, and the one Koin module tools register into.

**Why now.** `feature/editor/image/` and `feature/editor/video/` currently hold nothing but `.gitkeep`,
and they encode the wrong split — the axis is *core / shell / engine*, not *image / video*.

## Steps

1. Delete `shared/src/commonMain/kotlin/com/woka/feature/editor/image/.gitkeep` and
   `.../editor/video/.gitkeep`.
2. Create, in `commonMain`: `feature/editor/core/`, `feature/editor/shell/`, `feature/editor/tools/`.
   Create, in `jvmMain`: `feature/editor/engine/image/`, `feature/editor/save/`, `feature/editor/di/`.
   A package with no file yet gets no `.gitkeep` — ME-003 puts the first real file in `core/`.
3. `feature/editor/di/EditorModule.kt` (jvmMain): `val editorModule = module { }`, empty, with KDoc
   stating that this is **the single place a tool is declared** (§3.4) and that the registry is built
   from `getAll<EditorTool>()` so joining is one line.
4. `app/di/PlatformModule.kt`: compose `editorModule` into `platformModule`, or list both in
   `initKoin` — whichever keeps `Koin.kt` untouched in `commonMain`, since `editorModule` is a JVM
   symbol and `appModule` cannot name it.
5. Update the layout block in `CLAUDE.md` under `feature/editor/` if the directory names here differ
   from what it already lists.

## Files

`feature/editor/di/EditorModule.kt` (new), `app/di/PlatformModule.kt` (edit), `CLAUDE.md` (edit if
needed), two `.gitkeep` deletions

## Done when

- `./gradlew.bat :shared:compileKotlinJvm` green;
- a Koin verification test resolves the container with `editorModule` loaded — extend
  `jvmTest/support/KoinOnce.kt`, do not start a second container.

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **484 tests, 0 failures, 0 errors** — 483 before, plus the one
added here.

Both `.gitkeep` files deleted with their `image/` and `video/` directories; the wrong axis is gone.

`EditorModule.kt` created in `jvmMain` with `val editorModule = module { }`, empty, and composed into
`platformModule` via `includes(editorModule)`. Step 4 offered two options and only one of them holds:
listing both in `initKoin` was impossible, because `initKoin` lives in `commonMain` and cannot name a
JVM symbol. `Koin.kt` is untouched, as the step asked.

`EditorModuleTest` (`jvmTest/integration/`) starts the shared container through the existing
`startKoinOnce()` and resolves `Router` and `SettingsRepository`. Koin throws on a missing or
duplicated definition, so those resolutions are the assertion — `editorModule` is empty until ME-006,
so what this actually guards is the composition. Without it a broken `includes` would surface as
every Koin-using test dying at once with no indication of the cause. No second container is started.

**`CLAUDE.md` needed no edit.** Its `feature/editor/` block already lists `core/`, `shell/`, `tools/`,
`engine/image/`, `save/`, `di/` — the names this ticket creates. Step 5's condition did not fire.

**One thing the ticket asks for that git cannot hold.** Step 2 creates six packages and step 2 also
forbids `.gitkeep`. Git tracks files, not directories, so the five that received no file —
`core/`, `shell/`, `tools/`, `engine/image/`, `save/` — exist on disk and in no commit. Only
`di/EditorModule.kt` is committed. That is the right outcome, not a gap: ME-003 puts the first real
file in `core/`, and the directory arrives with it.
