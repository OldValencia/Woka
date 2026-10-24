# VF-002 — "Associate Formats" stops opening Windows Settings

| | |
|---|---|
| **Order** | 2 — same 1.0.4 pass, after VF-001 (so the 17 new extensions are registered too) |
| **Depends on** | VF-001 (only for the extension count) |
| **Feature** | [Video formats](../video-formats.md) |

**Goal.** Pressing *Settings → OS integration → Associate Formats* registers the formats and stays in
Woka — no jump to *Apps › Default apps*.
**Why now.** The author's request (2026-10-24): the Settings window opening every time is unwanted.

## Facts (2026-10-24)
- `feature/settings/OsIntegrationSettingsPanel.kt:62–76` (112 lines): on success it calls
  `WindowsFileAssociations.openDefaultAppsSettings()` and shows
  *"Registered N formats. Choose Woka in Windows Settings."* for 4 s.
- `core/system/WindowsFileAssociations.kt:93–101` (102 lines): `openDefaultAppsSettings()` is
  `cmd /c start ms-settings:defaultapps`; `register()` (`:71`) imports a `.reg` built by
  `AssociationRegContent.kt` — ProgID, `Capabilities\FileAssociations`, `RegisteredApplications`,
  `MuiCache`, and **`.ext\OpenWithProgids` only**. It never sets an extension's default and never
  calls `SHChangeNotify`.
- **What that means without the Settings jump:** Windows 10/11 protects the per-user default
  (`UserChoice`) with a hash an app cannot write, so Woka becomes an *"Open with"* candidate for every
  format, and the default only for extensions that have no default yet. Making Woka the default
  elsewhere stays a choice the user makes in Windows — Woka can no longer walk them there from the
  button.

## Steps
1. `OsIntegrationSettingsPanel.kt` — drop the `openDefaultAppsSettings()` call; the success message
   becomes *"Registered N formats. Woka is in “Open with” for each."* (exact wording: ask the author).
   **No replacement link** to Default apps — decided, see below.
2. `WindowsFileAssociations.kt` — remove `openDefaultAppsSettings()` if nothing else calls it
   (`Grep` first; on 2026-10-24 the panel was its only caller).
3. Measure, then decide, whether Explorer needs `SHChangeNotify(SHCNE_ASSOCCHANGED, …)` to pick the
   new entries up without a restart: register, then right-click a `.nsv` in Explorer → *Open with*.
   If Woka is missing until Explorer restarts, add the call (FFM to `shell32`, the `BundledNatives`
   / `JxlNative` downcall style — no JNA). If it shows up, add nothing.
4. `WindowsFileAssociationsTest` unchanged unless step 3 adds code.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/settings/OsIntegrationSettingsPanel.kt`,
`shared/src/jvmMain/kotlin/com/woka/core/system/WindowsFileAssociations.kt`.

## Done when
- pressing the button opens no other window; the status line says what happened;
- a newly listed format (e.g. `.nsv`) offers Woka under *Open with* — checked by hand, dated here;
- `./gradlew.bat :shared:build` green; anti-FQN clean.

## Decided (2026-10-24)
- **The window goes, with no replacement link.** The author: making Woka the default already means
  clicking through every format in Windows by hand, so the jump to *Default apps* bought nothing.
- **No `SHChangeNotify`:** checked by the author 2026-10-24 — Woka shows under *Open with* right after
  registering, no Explorer restart. Step 3 adds nothing.
- **Status line:** *"Registered N formats. Set Woka as default in Windows › Default apps."* — a hint,
  not a link.
