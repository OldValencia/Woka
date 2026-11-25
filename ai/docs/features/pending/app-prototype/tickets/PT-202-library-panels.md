# PT-202 — Search, Info panel, Settings, Quick Look, selection

| | |
|---|---|
| **Order** | eighth, built with PT-201 |
| **Depends on** | PT-201 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** The library's overlays and panels, ported from the app: selection and its bar, Quick Look,
search results, the Info panel, the Settings overlay with its eight panels.
**Why now.** With PT-201 they finish the Library section. What the author sees: `#library/select`,
`#library/search`, `#library/info`, `#library/quicklook`, `#library/settings`.

Source: `FolderSelectionBar`, `FloatingPanelModifier`, `AccessoryPill`, `QuickLook`,
`SearchResults`, `LibrarySearch`, `FolderLayers`, `FileInfoPanel*`, `SettingsOverlay*`,
`SettingsComponents`, `SettingsToggleRow`, `SettingsActionRow`, `SettingsDangerRow`, every settings
panel — read 2026-11-25.

## Steps

1. **Selection.** Check circle on hover, Alt+click, Shift+click ranges, Ctrl+A; the floating bar
   "N selected | Select All · Copy… · Move… · Duplicate | Delete | Done"; Esc and Done end it.
2. **Keyboard and Quick Look.** Arrows move a focus ring through the grid; Space opens Quick Look
   (74 % of the grid, max 860 px, 3:2, photo fitted, video poster with badge) over the grid only;
   Esc, Space or the scrim close it; Enter opens the viewer.
3. **Search.** The toolbar box filters after 200 ms; results replace the grid: This Folder / All
   Folders, "N results for “q”", square previews with the match marked, the parent below; a hit opens
   the folder or the file.
4. **Info.** Ctrl+I or the Info button: 272 px panel following the focused item — preview, name,
   parent, General / Video / Audio rows; "Select an item to see its details" otherwise.
5. **Settings.** `woka/settings.js`: the overlay with the eight panels and their rows as the app has
   them; segments, switches, steppers and the two-step danger rows respond for the session.

## Files

`ai/mockups/design/woka.html`, `woka/sections.js` (edit); `woka/library.js`,
`woka/library-actions.js`, `woka/settings.js`, `woka/library.css` (new, shared with PT-201).

## Done when

- `#library/select`: three selected, the bar at the bottom centre; Done clears it.
- Arrows then Space open Quick Look on the focused item; Esc closes it; a playing video underneath
  does not start.
- Typing `palm` shows two results; clicking one opens the viewer on it.
- `#library/info` shows the video's rows; `#library/settings` the overlay on Appearance.

## Result — 2026-11-25

Built as above and checked in headless Edge, with PT-201.

**Where the result differs from the app.** Selection actions (Copy, Move, Duplicate, Delete) answer
with a toast and end the selection. The Settings controls do not reach the rest of the prototype —
switching Appearance does not change the theme; the prototype bar's switch does that. The About
panel's description and credits are placeholders.
