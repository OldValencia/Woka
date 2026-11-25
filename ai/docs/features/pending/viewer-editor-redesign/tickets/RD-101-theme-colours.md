# RD-101 — Theme colours: readable dark text, visible hovers, the editor orange as text

| | |
|---|---|
| **Order** | first ticket of the redesign; every later surface draws with these values |
| **Depends on** | nothing |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Give `Theme.kt` the prototype's dark hover, secondary text and hairline, and a text tone for
the editor orange, and draw the one themed foreground that needs that tone with it.

**Why now.** Theme's dark `textSecondary` (#515764) is darker than its `textTertiary` (#838893) —
2.6 : 1 against 5.4 : 1 on the background — so every secondary label in the dark app reads as
disabled, and its 2 % hover and 3.9 % hairline barely show. Every later ticket draws on these
values; building surfaces first would tune them against the wrong ones.

**Re-cut from the backlog block (`tickets.md` §4).** The block also brought the prototype's extra
colours (`surfaceRaised`, `accentEditorSoft`, `canvas`, `glass`, `sound`, `soundBg`) and the room's
`--e-*` palette into this ticket. Nothing in today's app would draw them — the room does not dim
until RD-201 — so they would be fields nothing reads. Each joins in the ticket that first draws it:
the room palette, `surfaceRaised`, `accentEditorSoft`, `canvas` and `glass` with RD-201; `sound` and
`soundBg` with RD-301.

**Where the orange stays a fill.** Every other reader of `accentEditor` today is a fill (render
banner, edited-frame dot, trim handles, playhead, clip outline, Done) or sits on a `ViewerChrome`
scrim, which is dark in both themes (rail, chips, value fields, preview tiles, swatch ring). They keep
`accentEditor`. Only the peek glyph sits on the themed top bar, where #FF9F0A on white is 2.1 : 1.

## Steps

1. **Prototype first** (`feature-docs.md` §6). In `ai/mockups/design/woka/tokens.css`: light
   `--accent-editor` back to `#ff9f0a`; add `--accent-editor-text` — dark `#ff9f0a`, light `#ad6500`.
   Switch the foreground uses to the new token: `base.css` — the `:focus-visible` outline and
   `button.selected`'s `color`; `editor.css` — `dialog a` and `#compare[aria-pressed=true]`'s `color`;
   `viewer.css` — `.info-edited`'s `color`. Fills keep `--accent-editor`.
2. **`Colors`** (`core/ui/theme/Theme.kt`): add `val accentEditorText: Color` after `accentEditor`,
   with a one-line KDoc — the editor orange as a foreground on a themed surface; `accentEditor` is
   the fill.
3. **`LightColors`**: `accentEditorText = Color(0xFFAD6500)`.
4. **`DarkColors`**: `surfaceHover = Color(0x0EFFFFFF)` (5.5 %), `textSecondary = Color(0xFF9A9FAA)`,
   `border = Color(0x12FFFFFF)` (7 %) — their trailing comments updated to the new values;
   `accentEditorText = Color(0xFFFF9F0A)`.
5. **`EditorPeekButton`** (`feature/editor/shell/EditorPeekButton.kt:109`): the held tint becomes
   `Theme.colors.accentEditorText`.
6. **Contrast helpers to `support/`.** Move `linear`, `luminance`, `flatten` and `contrast` out of
   `unit/ViewerChromeTest.kt` into `jvmTest/.../support/Contrast.kt` as `internal` top-level
   functions, `contrast(foreground, background)` taking two opaque colours; `ViewerChromeTest` keeps
   its mid-grey backdrop and calls `contrast(fg, flatten(scrim, MID))`. Its assertions do not change.
7. **`unit/ThemeColorsTest.kt`** (new), arithmetic like `ViewerChromeTest`, on `LightColors` and
   `DarkColors` directly:
   - in both themes, against `background` and `surfaceElevated`: primary > secondary > tertiary;
   - dark `textSecondary` ≥ 4.5 : 1 on `background` and on `surfaceElevated`;
   - dark `surfaceHover.alpha` ≥ 0.05; dark `border.alpha` ≥ 0.06; `borderGlow.alpha` >
     `border.alpha` in both themes;
   - light `accentEditorText` ≥ 4.5 : 1 on `surfaceElevated` and ≥ 3 : 1 on `background` (a glyph);
     dark `accentEditorText` ≥ 4.5 : 1 on `surfaceElevated`.

## Files

`ai/mockups/design/woka/tokens.css`, `base.css`, `editor.css`, `viewer.css` (edit) ·
`shared/src/commonMain/kotlin/com/woka/core/ui/theme/Theme.kt` (edit, 130 → ~134 lines) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorPeekButton.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/support/Contrast.kt` (new) ·
`shared/src/jvmTest/kotlin/com/woka/unit/ViewerChromeTest.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/ThemeColorsTest.kt` (new)

## Done when

- `node ai/mockups/tools/shot.mjs woka.html <dir> --styles` over `#viewer/photo`, `#viewer/frames`,
  `#editor/photo`, `#editor/video`, `#dialogs/copy`, run before and after step 1 and compared with
  `--compare`: dark identical; light differs only in properties that resolve from `--accent-editor`
  or `--accent-editor-text`, and the light edited dots (`.media-edited`, `.frame-dot`) are
  `#ff9f0a`. (Done, the playhead and the room's controls draw with `--e-accent` and do not move.)
- `Grep "accent-editor" ai/mockups/design/woka/*.css`: every `color:` and outline use reads
  `--accent-editor-text`, every `background`/`border` use `--accent-editor`.
- `Theme.kt` dark values are 5.5 % hover, #9A9FAA, 7 % border; `accentEditorText` exists in both.
- The only reader of `accentEditorText` is `EditorPeekButton`.
- Seen in the app: dark theme — secondary labels (settings rows, file info, grid captions) read as
  secondary rather than disabled, hovers and hairlines show; light theme — holding the peek button
  tints it brown-orange, not pale orange.
- `ThemeColorsTest` and `ViewerChromeTest` green; `./gradlew.bat :shared:build` green.
