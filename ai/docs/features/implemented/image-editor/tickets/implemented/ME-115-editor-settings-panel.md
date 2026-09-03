# ME-115 — Editor settings panel

| | |
|---|---|
| **Order** | 47 — after ME-122; the last piece of ME-006 that has no UI |
| **Depends on** | ME-006 (the registry and `editorDisabledTools`), ME-106 (the editor exists to be configured) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Add **Settings → Editor**: one row per declared tool — icon, title, summary, switch — writing
`WokaSettings.editorDisabledTools`.

**Why now.** Everything under this row already works and nothing can reach it. `EditorToolRegistry`
has filtered on `editorDisabledTools` since ME-006, `SettingsMutations.setEditorToolEnabled` writes
it, `EditorSettingsPersistenceTest` proves it survives a restart, `EditorScreen` closes the inspector
of a tool that leaves the set, and `skippedOps` + `EditorSkippedNotice` report a disabled tool's
operations as skipped rather than dropping them. The only way to switch a tool off today is to edit
`settings.json` by hand.

## The one thing that is not obvious

**The panel cannot live where the other panels live.** `feature/settings/` is `commonMain` and
`EditorToolRegistry` is `jvmMain`, so `SettingsOverlayContent` cannot name it. The seam is the one
`NativeMediaEditor` already uses for exactly this reason: an `expect @Composable fun
EditorSettingsPanel()` in the settings package, actual in `jvmMain` beside it, resolving the registry
through a `private class ... : KoinComponent { val registry: EditorToolRegistry by inject() }` — the
`EditorDependencies` pattern from `EditorHost`.

**No per-tool settings code, now or ever** (§3.4). The panel iterates `registry.all()` and reads the
manifest; a tool that wants a switch gets one by existing, and a tool with an option of its own is a
`ParamSpec`, not a row here.

**`registry.all()`, not `registry.tools(kind)`.** You cannot re-enable what is not listed, which is
the whole reason the registry has two accessors.

## Steps

1. `core/ui/components/` is **not** where this goes — reuse `feature/settings/SettingsToggleRow.kt`
   (edit): add `icon: DrawableResource? = null` as the last parameter, drawn 16.dp at the row's
   leading edge with 12.dp after it and tinted `Theme.colors.textSecondary`, exactly as `SidebarItem`
   draws its own. Null draws nothing and every existing call site is unchanged.
2. `feature/settings/SettingsComponents.kt` (edit): move `PanelHint` here from
   `VideoPlayerSettingsPanel.kt`, where it is `private`, and drop the `private` — it is the one line
   of explanatory text every panel in this package is built to draw, and the alternative is a second
   copy of six lines. `VideoPlayerSettingsPanel` keeps calling it unchanged.
3. `feature/settings/EditorSettingsPanel.kt` (new, `commonMain`):
   `@Composable expect fun EditorSettingsPanel()`. No parameters — it reads the repository itself,
   the newer of the two conventions in this package and the one `VideoPlayerSettingsPanel` follows.
4. `feature/settings/EditorSettingsPanel.kt` (new, `jvmMain`): the `actual`. A `SectionLabel("Tools")`,
   a `PanelHint` saying a switched-off tool leaves the rail and its saved operations stop being
   applied, then `registry.all()` as one `SettingsToggleRow` each — `manifest.title`,
   `manifest.summary`, `manifest.icon`, `checked = manifest.id !in settings.editorDisabledTools`,
   `onCheckedChange = { repo.setEditorToolEnabled(manifest.id, it) }` — in a `verticalScroll`, because
   six tools at 60-odd dp plus the header is already taller than the Video Player panel.
   `registry.all()` is read once in a `remember`: the list of declared tools cannot change while the
   app runs, only its filtering can.
5. `feature/settings/SettingsOverlayContent.kt` (edit): `CategoryItem("Editor", Icons.Static.Editor,
   …)` after `"Video Player"` and before `"PiP"` — the sidebar's order follows the app's, and the
   editor sits after the players. `"Editor" -> EditorSettingsPanel()` in the `when`.
   `Icons.Static.Editor` (`square_and_pencil`) already exists and is unused; ME-128 also wants it for
   the dock item, which currently draws `Icons.Static.Settings`.

**Six files, four of them two-line changes.** Nothing in the editor itself changes: the registry, the
fold and the notice were built for this and are already tested from the other side.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/settings/EditorSettingsPanel.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/settings/EditorSettingsPanel.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/settings/SettingsToggleRow.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/settings/SettingsOverlayContent.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/settings/SettingsComponents.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/settings/VideoPlayerSettingsPanel.kt` (edit)

## Done when

- `jvmTest/ui/EditorSettingsPanelUiTest.kt` (new): the panel composed over a throwaway
  `SettingsRepository` lists one row per tool the registry declares, titled from the manifest;
  toggling one off puts that id into `editorDisabledTools` and toggling it back removes it. Built
  against a registry of fixture tools, so the assertion does not change every time a real tool is
  added.
- Same file: a tool already in `editorDisabledTools` composes with its switch **off** — the row reads
  the setting rather than its own state, which is the bug a switch written with `remember` always has.
- `jvmTest/ui/SettingsToggleUiTest.kt` (edit): a row given an icon draws it and a row given none is
  laid out exactly as before.
- By hand: switch Markup off, open the editor and find the rail one shorter and the tool's shortcut
  dead; open a photograph that already carries a markup edit and find the notice saying its operations
  were not applied, with the picture unmarked; switch it back on and find the marks return. Switch
  **every** tool off and confirm the editor opens with an empty rail rather than failing.
- `./gradlew.bat :shared:build` green.
