# ME-006 — Tool registry, enablement, settings

| | |
|---|---|
| **Order** | 7 |
| **Depends on** | ME-005 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** One `StateFlow` of live tools, filtered by media kind and by the user's Settings toggles.

**Why now.** Rail, inspector, shortcut table, settings rows and the fold all read from it. Building any
of them against a plain list means rewriting them later.

## Steps

1. `WokaSettings.kt`: add `editorDisabledTools: Set<String> = emptySet()`, with a comment on why the
   default is a *disabled*-set rather than an enabled-set — a tool added in a later release must
   appear for users whose `settings.json` predates it.
2. `SettingsRepository.kt`: add `fun setEditorToolEnabled(id: String, enabled: Boolean)`; implement it
   in `SettingsRepositoryImpl` as a one-liner delegating to a new pure transform in
   `SettingsMutations.kt`. **Check `SettingsRepository.kt` against the 150-line mandate before
   editing** and offer the split if it is already over.
3. `app/SettingsProjection.kt`: project `editorDisabledTools` the way the other fields are projected.
4. `EditorToolRegistry.kt` (jvmMain, `feature/editor/core/`): constructor takes `List<EditorTool>` and
   `SettingsRepository`. Exposes `tools(kind: MediaKind): StateFlow<List<EditorTool>>` — sorted by
   `domain` then `order`, filtered by `supports` and by the disabled set — `all(): List<EditorTool>`
   for the settings panel, disabled ones included (you cannot re-enable what is not listed), and
   `toolFor(op: ToolOp): EditorTool?`.
5. Register the registry as a `single` in `EditorModule`, built from `getAll<EditorTool>()`, so a tool
   joins by being declared and by nothing else.

## Files

`core/data/WokaSettings.kt`, `core/data/SettingsRepository.kt`, `core/data/SettingsMutations.kt`,
`jvmMain core/data/SettingsRepositoryImpl.kt`, `app/SettingsProjection.kt`,
`feature/editor/core/EditorToolRegistry.kt` (new), `feature/editor/di/EditorModule.kt`

## Done when

- `jvmTest/unit/EditorToolRegistryTest.kt`: sorting by domain then order; `supports` filtering by
  kind; a disabled id disappears from `tools()` but stays in `all()`; toggling the setting re-emits;
- an integration test proves the flag survives a `settings.json` round trip — `support/SettingsWriter.kt`
  already does this for other fields.

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **517 tests, 0 failures, 0 errors** — 509 before, plus eight.

### The split step 2 asked about was needed, and it was the class

`SettingsRepositoryImpl.kt` was at **149 lines** before this ticket, so the one-line setter would have
pushed it over. The first split, recorded in `SettingsMutations.kt`'s own KDoc, had already moved the
setter *bodies* out; there was nothing left to move, so the seam had to be the contract.

`SettingsRepository` now extends two interfaces — `LibrarySettings` (folders, listing, appearance,
subtitle styling) and `PlaybackSettings` (playback, tracks, watch state) — and
`SettingsRepositoryImpl` composes them with Kotlin delegation. **No call site changed**: anything
holding a `SettingsRepository` still sees every method.

| file | lines |
|---|---|
| `SettingsRepositoryImpl.kt` | 39 |
| `LibrarySettingsImpl.kt` | 79 |
| `PlaybackSettingsImpl.kt` | 63 |
| `SettingsMutator.kt` | 65 |

`SettingsMutator` is the shared piece: the live `WokaSettings` cell plus the copy-then-schedule-a-save
rule. Named for what it does rather than where it sits — `SettingsStore` is already the disk beneath
it, and `*State` would promise an immutable snapshot (`code-style/naming.md`).

Two details that are load bearing rather than incidental:

- **One mutator, passed as a constructor parameter** so the delegation list can reach it. It must stay
  per-instance. The first attempt made it a `companion object` — which compiles, and would have made
  every `SettingsRepositoryImpl` in the suite share one cell and one storage root, destroying test
  isolation silently.
- **`update` is no longer `inline`.** Settings are written when a person changes one, so the lambda
  allocation is irrelevant, and inlining across files would have dragged the mutable cell into the
  public API through `@PublishedApi`.

### Step 3 did not apply

`app/SettingsProjection.kt` holds one generic helper, `collectField(select)`. There is no list of
per-field projections to add `editorDisabledTools` to, so the file is untouched.

### `toolFor` ignores enablement, deliberately

§3.4 requires the fold to report "operations from a **disabled** tool were not applied", which it
cannot do if a disabled tool and an unknown one both resolve to null. `toolFor` therefore resolves
against every declared tool and the caller checks enablement. `tools(kind)` is the filtered view;
`all()` is the settings list, disabled included, because you cannot re-enable what is not shown.

### One more file at the ceiling

`SettingsPersistenceTest.kt` is at exactly 150 lines, so the round-trip cases went into a new
`EditorSettingsPersistenceTest.kt` rather than growing it. A test file is not exempt from the mandate.

### Not yet visible

This ticket adds no UI. `editorDisabledTools` and `setEditorToolEnabled` exist and round-trip, but the
Settings row that toggles them is derived from `all()` by the shell, in ME-101.
