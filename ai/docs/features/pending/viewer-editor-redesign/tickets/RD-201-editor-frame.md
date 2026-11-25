# RD-201 — The editor's frame: header, right-hand inspector with tabs, the room

| | |
|---|---|
| **Order** | second, after RD-101; RD-202…RD-204 restyle what this one places |
| **Depends on** | RD-101 |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Give the editor the prototype's frame (`#editor/photo`, `#editor/video`): one header,
a right-hand inspector with tabs and `Revert to original…` at its foot, and the dimmed room —
both themes.

**Why now.** Every later editor ticket draws into this frame; RD-202's wells and RD-203's panels
have nowhere to stand while the inspector is a floating card over the picture.

**One ticket by the author's call (2026-11-25).** `tickets.md` §4a's size limit is waived: 1.0.9
ships after every step, so the block is built whole rather than in slices.

**Decided with the author (2026-11-25).**
- Resize, HDR and Cover leave the tabs now. Their ops stay registered and keep folding; they are
  unreachable until RD-401 (`Create a copy`: size, SDR) and RD-301 (Cover in the clip menu).
- Crop and Transform are **one tab**, Transform: the crop frame on the canvas, Crop's rows, then
  Transform's rotate/flip rows. RD-203 redraws the panel.

**Left for the tickets that own them.** `?` and the help sheet, `Create a copy…` (RD-401); the scope
line (RD-204 frame sets, RD-302 film); the video timeline's look (RD-301) — the band keeps today's
look under the canvas. `Done ▾` loses its caret here: on a film that keeps edits the other exports
are unreachable until RD-401's button.

## The spec, measured (`editor.css`, `shell.css`, 1440 px)

- **Room:** window and header `--e-room`; canvas padding 26 × 34; picture radius 4, shadow
  `--e-picture-shadow`. The app sidebar slides out while editing; the inspector slides in, so the
  picture glides between them rather than jumping.
- **Header** (the shell's bar): left `Cancel` (13 sp, muted; hover text on `hoverStrong`), undo,
  redo (32 dp icon buttons, 18 dp glyphs, muted); centre the viewer's pill on `--e-well`, no border
  or shadow; right `Original` (icon + label, 32 dp, 11 padding, 13 sp, muted; held:
  `accentText` on `accentFill`), the panel toggle (F6), `Done` (32 dp, radius 16, padding 18,
  `--e-accent` with `--e-on-accent`, 13 sp semibold). No Back chevron, no Revert.
- **Inspector:** 294 dp, `--e-panel`, 1 dp `--e-line` on its left, full height under the header.
  Tabs in a `--e-track` well: margin 12/12/0, padding 4, gap 2, radius 12; each tab equal width,
  54 dp tall, radius 9, 20 dp glyph over an 11 sp label, muted; hover text; selected `accentSoft`
  fill, text, semibold, glyph `--e-accent`. Body scrolls, padding 14/14/22. Foot: 1 dp `--e-line`
  top, padding 8 × 12, `Revert to original…` 12 sp muted, hover on `hoverStrong`.
- **Tabs:** photo — Styles, Adjust, Transform, Markup; film — Clip, Styles, Adjust, Transform,
  Markup, Sound. One tab is always selected; the first is selected on open.

## Steps

**Tokens**
1. `core/ui/theme/EditorRoom.kt` (new): `data class RoomColors` with the `--e-*` colours of
   `tokens.css` (room, panel, well, well2, track, knob, hover, hoverStrong, line, lane, text,
   muted, faint, accent, accentHover, onAccent, accentText, accentFill, accentLine, danger, sound,
   soundBg, switch, switchKnob, menu, tip, tipText); `DarkRoom`, `LightRoom` (light inherits the
   dark accent, accentHover and onAccent, as `tokens.css` does).
2. `Theme.kt`: `LocalRoomColors`, provided by `Theme()` from `darkTheme`; `Theme.room` getter.

**Shell**
3. `MainShell`: the background animates to `Theme.room.room` while `canvasOverride.editing`.
4. `Sidebar`: shown only while `sidebarVisible && !editing` (same 250 ms tween).
5. `GlobalTopBar`: no show-sidebar button while editing.
6. `MediaViewerControlsContainer`: while editing, `Theme.room.well`, no border, no shadow.

**Header**
7. Glyphs (new drawables from the prototype's Phosphor set): `arrow_uturn_backward`,
   `arrow_uturn_forward`, `sidebar_right`, `compare`; `Icons.Static` entries for them.
8. `EditorTopBarControls.kt`: `TopBarButton` and `TopBarIconButton` in room colours per the spec.
9. `EditorTopBarSetup`: leading = Cancel, undo, redo; `backActionOverride` and
   `centerTrailingAccessory` null while editing (both restored on dispose); right =
   `EditorHeaderActions` — Original, panel toggle, Done.
10. `EditorPeekButton`: the labelled `Original` button per the spec; press-and-hold unchanged.
11. `DoneButton`: room colours and size per the spec; caret and `DoneMenu` removed; `DoneChoices`
    keeps `instead` (a film that cannot keep the edit) and moves to `DoneChoices.kt`.

**Inspector**
12. `ToolManifest`: `tabbed: Boolean = true` (false: no tab, ops still fold) and
    `joins: String? = null` (the id of the tool whose tab this one shares). Resize, HDR, Cover
    `tabbed = false`; Crop `joins = "transform"`; Trim's title `Clip`, Sound's `Sound`.
13. `EditorTabs.kt` (new): `EditorTab(id, title, icon, tools)`, `tabsOf(tools)` — grouped by
    `joins`, ordered by the owning tool's `order`, untabbed dropped; the tab's *canvas tool* is its
    `CanvasTool`, else its first tool. Unit test `unit/EditorTabsTest`.
14. `EditorTabBar.kt` (new): the tab well per the spec, tag `editor-tab-<id>`, the
    unavailable reason as a tooltip (as the rail had it).
15. `EditorInspectorPanel.kt` (new): the panel — tab bar, scrolled body with one section per tool
    of the tab (a tool's title is drawn only when the tab holds two), the foot with Revert
    (`editor-revert`), and `EditorPanelState` (shown / toggle). The tool sections are today's
    `EditorInspector` content without its card plate.
16. `EditorScreen`: `activeTabId` replaces `activeToolId` (first tab by default, re-chosen when its
    tab leaves); the active tool is the tab's canvas tool while the panel is shown, null while it
    is hidden; layout = canvas area (canvas, notices, the medium's band) beside the panel, the
    panel's width animated 0 ↔ 294 dp on the sidebar's tween. Moved to keep the file under the
    mandate: the tab state and its effects (`EditorTabState.kt`).
17. The side panel a row opens (`ParamAccessory`) floats left of the inspector, top-aligned, on
    `Theme.room.menu` with a `line` border and radius 12, sliding in; `GooeyPair`,
    `PairLayout`, `GooeyEffect` and `EditorInspectorCard`, `EditorRail` are deleted if nothing
    else reads them.
18. `EditorBottomPanel`: notices and the band only.
19. `EditorKeys`: F6 toggles the panel. `EditorKeyEffect` takes the session (reads undo/redo
    itself), which keeps `EditorHost` under the mandate as it gains the panel state.
20. **Room colours in the inspector.** Every file drawn inside the panel reads `Theme.room`
    instead of `Theme.viewer`: `onScrim` → text, `onScrimSecondary` → muted, `onScrimDisabled` →
    faint, `onScrimBorder` → line, `onScrimHover` → hover, `onScrimSelected` → hoverStrong,
    `popoverScrim` → well; `accentEditor` as text → accentText, as a fill → accent. Component
    presets for the room (`SegmentedControl`, `DetentSlider`, `SliderSwitch`) in
    `EditorRoomPresets.kt`. Notices and the band stay on `ViewerChrome` — they sit over media.
21. `EditorCanvasHost`: radius 4 and the picture shadow; canvas padding 26 × 34.

**Tests**
22. UI tests: `editor-tool-<id>` → `editor-tab-<id>` (Crop's → `editor-tab-transform`);
    `EditorRailHdrUiTest`, `ResizeInspectorUiTest` deleted (their tools left the tabs, RD-401
    brings their replacements); tests of the header (`EditorTopBarUiTest`, Revert, Done) follow
    the new places. New `ui/EditorInspectorPanelUiTest`: tabs for a photo fixture, Transform holds
    crop + transform rows, F6 hides and restores the panel, Revert sits in the foot.

## Files

New: `core/ui/theme/EditorRoom.kt`, four drawables, `EditorTabs.kt`, `EditorTabBar.kt`,
`EditorInspectorPanel.kt`, `EditorTabState.kt`, `DoneChoices.kt`, `EditorRoomPresets.kt`,
`unit/EditorTabsTest.kt`, `ui/EditorInspectorPanelUiTest.kt`. Edited: `Theme.kt`, `Icons`,
`MainShell`, `Sidebar`, `GlobalTopBar`, `MediaViewerControlsContainer`, `ToolManifest`, the tool
manifests, `EditorScreen`, `EditorHost`, `EditorKeys`, `EditorTopBarSetup`, `EditorTopBarControls`,
`EditorPeekButton`, `DoneButton`, `EditorBottomPanel`, `EditorCanvasHost`, `EditorCanvasLayer`, the
inspector's row files (step 20), the UI tests (step 22). Deleted: `EditorRail`,
`EditorInspectorCard`, `DoneMenu`, the gooey files if unread, two UI tests.

## Done when

- The editor opens with the sidebar gone, the room dimmed, the header and inspector as in
  `#editor/photo` / `#editor/video`, in both themes.
- `Grep "Theme.viewer" ` over the inspector's row files: no match.
- `Grep "editor-tool-"` over `jvmTest`: no match.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## Result — 2026-11-25

Built as specified, both themes. Where the build differs from the steps:

- **Styles before Adjust** needed `FilterTool.order = 0`, `AdjustTool.order = 1` — `order` is only
  the listing position (the fold ranks by domain), so nothing else moves. Tab order by domain is
  `EditorTabs.kt`'s own list, not `ToolDomain`'s, which is the fold's.
- **Marks stay movable while a non-drawing tab is open** (`EditorCanvasOverlay`): with a tab always
  open, ME-149's "no tool open" became "no canvas tool open" — Styles, Adjust, or the panel hidden.
- **`EditorKeyEffect`** kept its signature and gained `onTogglePanel`; `EditorHost` stayed at 149
  lines by reading undo/redo inline instead of through two `val`s.
- The accessory float lives in `EditorTabState.kt` beside the tab state; the header buttons stay
  clickable while disabled so their semantics read "not enabled".
- `EditorUnderShell` starts Koin: the pill the editor brings reads a setting through it, and the
  header tests passed or failed by test order.
- Not checked by eye in the app; the prototype comparison is the author's.
