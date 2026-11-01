# PI-001 — Every icon from Phosphor

| | |
|---|---|
| **Order** | 1 — the only ticket |
| **Depends on** | the accepted mockup, `ai/mockups/design/phosphor-icons.html` |
| **Feature** | [Phosphor icons](../phosphor-icons.md) — §2 |

**Goal.** No icon the app draws comes from SF Symbols.
**Why now.** The SF Symbols licence does not cover a Windows app, and the repository carries the files.

## Steps
1. A script (kept out of the tree; it needs the npm package `@phosphor-icons/core@2.1.1`) reads the
   author's picks and writes each Phosphor glyph over the SF file of the same name, with the
   viewBox set to `16 16 224 224` and `fill="black"`. Turns and the bolt's scale are baked into the
   path data, not left as `transform`.
2. The author's picks, weight **regular**:
   - `aqi_medium` dots-six · `aspectratio` frame-corners · `camera_filters` aperture ·
     `character_textbox` textbox · `circle_bottomhalf_filled` circle-half, turned ·
     `collapse_content` arrows-in-simple · `expand_content` arrows-out-simple · `drop` drop ·
     `eyedropper_halffull` eyedropper-sample · `file_present` file · `home` house ·
     `line_3_horizontal_decrease` funnel-simple · `moon_circle` moon-stars ·
     `paintbrush_slash` split-horizontal · `photo_on_rectangle_angled` images · `pin` push-pin ·
     `rectangle_arrowtriangle_2_inward` arrows-in · `rectangle_arrowtriangle_2_outward` arrows-out ·
     `redo` arrow-u-up-right · `undo` arrow-u-up-left · `settings` gear-six ·
     `slash_circle` sliders-horizontal ·
     `slider_horizontal_2_arrow_trianglehead_counterclockwise` clock-counter-clockwise ·
     `square_and_pencil` note-pencil · `sun_max_circle` sun-horizon · `tablecells` table ·
     `thermometer_sun` thermometer-hot · `videocam` video-camera.
   - Hand-drawn: ±5 s, which is the arrow with a 5 inside; Sound Boost, which is
     `speaker-high` and `lightning`, filled.
   - Speaker: none/low/high/slash, filled; 2 and 3 waves share `speaker-high`.
   - Every other row takes its single direct match.
3. `crop.svg` (Phosphor `crop`) is added for `Icons.Static.Crop`, which shared Fit's file.
4. Deleted: the 20 unreferenced drawables, and 21 `Icons.Static` constants nothing reads, with
   their files.
5. Phosphor's MIT notice goes to `desktopApp/appResources/common/licenses/phosphor-icons.txt`.

**Files:** `shared/src/commonMain/composeResources/drawable/*.svg`,
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt`,
`desktopApp/appResources/common/licenses/phosphor-icons.txt`.

**Done when:** no file under `drawable/` was written by Apple's CoreSVG, `:shared:build` is green,
and the author has walked the viewer, editor, sidebar and settings by hand.

## Result — 2026-11-01
- 79 glyphs written (78 replaced and `crop.svg` added), 41 files deleted, 0 CoreSVG files left;
  `drawable/` holds 81 SVGs. Every one was rendered to a contact sheet and checked to be drawn.
- `Icons.kt` 138 → 115 lines. `:shared:build` green.
- By-hand walk pending.
- **After the first walk (the author, 2026-11-01):**
  - Glyphs were cut at the edge: the gear sat exactly on 16..240 and lost its anti-aliasing, and
    the video camera reached 256. The single `16 16 224 224` box became `12 12 232 232`. A glyph
    that comes within 6 units of that box gets a square box of its own, centred on its bounds; 28
    did. The four speaker levels share one box, so the speaker does not move as the volume changes.
  - ±5 s: box enlarged 6.5 %, which draws them a little smaller.
  - `character_textbox` → `text-t`, not `text-aa`: `Aa` is the faces button beside it (ME-155).
  - 11 more `Icons.Static` constants found unread when matched as `Icons.Static.X` rather than
    as a bare word: `Delete`, `Disabled`, `Home`, `File`, `Link`, `Clear`, `Grid`, `List`, `Table`,
    `Undo`, `Redo`. They are deleted with their files, and so is the template's unreferenced
    `compose-multiplatform.xml`. `drawable/` now holds 70 files and `Icons.kt` 103 lines.
  - The Edit pill's pencil box is cropped to the glyph, 12 % larger. Auto and Reset all are drawn
    at 16 dp, up from 14 (`ParamHeaderAction`).
  - `:shared:build` green.
