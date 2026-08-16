# ME-006 — Tool registry, enablement, settings

| | |
|---|---|
| **Order** | 7 |
| **Depends on** | ME-005 |
| **Feature** | [Media Editor](../media-editor.md) |

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
