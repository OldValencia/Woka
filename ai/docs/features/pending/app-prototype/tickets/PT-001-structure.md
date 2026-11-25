# PT-001 — Rename, one token file, sections and links

| | |
|---|---|
| **Order** | first |
| **Depends on** | nothing |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Turn `editor-next` into `woka.html` with one token file, per-area stylesheets, a section
switcher and hash links, without changing what any existing scene looks like.
**Why now.** Every later ticket adds screens; on five cascading layers each addition means reading
all five to know a colour. What the author sees after this ticket: the new name, the section bar,
links that open a scene, both themes — and the editor scenes exactly as before.

## Steps

1. **Baseline.** Add `ai/mockups/tools/shot.mjs`: launches
   `C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe --headless=new` with a temporary
   profile and `--remote-debugging-port`, connects with Node's built-in `WebSocket`, sets the
   viewport (`Emulation.setDeviceMetricsOverride`), and for each `name=js` argument evaluates the
   script, waits 900 ms and writes `<outDir>/<name>.png` (`Page.captureScreenshot`). A `#hash`
   argument navigates instead of evaluating. Run it on `editor-next.html` at 1440×900 for the
   five editor scenes and the two viewer states, dark and light, and keep the 14 PNGs as the
   baseline outside the repo.
2. **Rename.** Move `ai/mockups/design/editor-next.html` → `woka.html` and `editor-next/` → `woka/`.
   Both are untracked, so this is a filesystem move, no git. Update every `href`/`src` in
   `woka.html`; `<title>` becomes `Woka — prototype`.
3. **Tokens.** Create `woka/tokens.css`: `:root` = `DarkColors`, `:root[data-theme=light]` =
   `LightColors`, one custom property per `Colors` field in kebab case (`--background`,
   `--surface-elevated`, `--text-secondary`, …), plus `ViewerChrome`'s values, the editor room
   tokens from `look.css` (`--e-*`), `--sound`/`--sound-bg`, `--danger`, the font stack and the
   motion curves (`--spring`, `--ease`). Where the value `woka.css`/`look.css` resolves to today
   differs from `Theme.kt`, `tokens.css` keeps today's value under the `Theme.kt` name and the
   difference goes into the list in step 7.
4. **Fold the layers.** Replace `styles.css`, `experience.css`, `woka.css`, `refine.css`,
   `look.css` with `base.css` (reset, type, buttons, segmented, scrub, toggle, tooltip, toast,
   context menu, `<dialog>`), `shell.css` (prototype bar, app shell, sidebar, header, pill),
   `viewer.css` (viewer layer, nav buttons, playback bar, carousel, caption) and `editor.css`
   (inspector, tools, panels, crop, dial, timeline). Each selector appears once with the value the
   five-layer cascade produced; every colour is a `var(--…)` from `tokens.css`. Delete the five
   old files.
5. **Sections.** New `woka/sections.js`: a `SECTIONS` table — `library` (no scenes yet, button
   disabled, tooltip `PT-201`), `viewer` (`photo`, `video`), `editor` (`photo`, `crop`, `frames`,
   `video`, `audio`), `dialogs` (disabled, tooltip `PT-105`). The prototype bar renders the section
   buttons and the selected section's scenes. A viewer scene calls `openMedia(id)`; an editor
   scene calls `setScene(id)`, which already leaves the viewer (`viewerMode=false` in `app.js`). Replace the hard-coded
   `.scenarios` buttons in `woka.html`.
6. **Links.** `sections.js` reads `location.hash` as `#<section>/<scene>` on load and on
   `hashchange`, and writes it on every switch (`history.replaceState`). No hash → `#viewer/photo`,
   which is today's first screen.
7. **Gap list.** In this ticket, under "Theme gaps", list each token whose value differs from
   `Theme.kt`: name, prototype value, `Theme.kt` value. No decision is made here.

## Files

`ai/mockups/tools/shot.mjs` (new), `ai/mockups/design/woka.html` (moved from `editor-next.html`),
`ai/mockups/design/woka/` (moved from `editor-next/`): `tokens.css`, `base.css`, `shell.css`,
`viewer.css`, `editor.css`, `sections.js` (new); `styles.css`, `experience.css`, `woka.css`,
`refine.css`, `look.css` (deleted); `app.js`, `woka.js` (edit: scene buttons and initial scene move
to `sections.js`).

## Done when

- `shot.mjs` on `woka.html` with `#editor/photo`, `#editor/crop`, `#editor/frames`,
  `#editor/video`, `#editor/audio`, `#viewer/photo`, `#viewer/video`, both themes, gives PNGs
  byte-identical to the baseline except the prototype bar; any other difference is listed here and
  explained.
- `Grep` for `#[0-9a-fA-F]{3,8}\b|rgba?\(` in `woka/*.css` matches only `tokens.css`.
- `Grep` for `editor-next` in `ai/` matches nothing outside `docs/` history.
- Opening `woka.html#editor/video` by double-click lands on the video editor.
- The Theme gaps list below is filled.

## Theme gaps

Measured 2026-11-25 by comparing the values the five layers resolved to with `Theme.kt`. The
prototype keeps its value; nothing here is decided.

| Token | Theme | Prototype | `Theme.kt` |
|---|---|---|---|
| `--surface-hover` | dark | `rgba(255,255,255,.055)` | `rgba(255,255,255,0.02)` |
| `--text-secondary` | dark | `#9a9faa` | `#515764` |
| `--border` | dark | `rgba(255,255,255,.07)` | `rgba(255,255,255,0.039)` |
| `--danger` | dark | `#f29a95` | `#ff5c5c` |
| `--danger` | light | `#a3302b` | `#e5484d` |
| `--accent-editor` | light | `#ad6500` | `#ff9f0a` |

Colours the prototype uses that `Theme.kt` has no field for: `--surface-raised`,
`--accent-editor-soft`, `--canvas`, `--glass`, `--sound`, `--sound-bg`, the editor room `--e-*`,
and `--primary-fill` / `--primary-fill-hover` / `--on-primary` (`#ffb34c`, `#ffc574`, `#20170c` —
the dialogs' primary button, left from the first layer's palette and not the editor's orange).

## Result — 2026-11-25

Built. `woka.html` + `woka/`; `tokens.css`, `base.css`, `shell.css`, `viewer.css`, `editor.css`,
`sections.js`; the five layers deleted; `app.js` lost its handling of the old scene buttons.

**How it was checked.** The PNG criterion above assumed byte-identical screenshots; they are not —
two runs of the unchanged `editor-next.html` already differed in 6 of 48 PNGs (dialogs and menus
animating in), and at 1600 px in every scene with the scaled photograph. `shot.mjs --styles` dumps
every element's computed style instead, and that is what was compared: 24 states × 2 themes at
1440×900 (viewer, popovers, focus view, every editor tool, menus, dialogs, toast) and 7 states ×
2 themes at 1600, 1100, 880, 800 and 600 px wide. **Before the section bar: identical, every
element, every property. After it: identical everywhere outside the prototype bar.** Links checked
by screenshot for all seven hashes; an unknown hash falls back to the current scene.

**Where the result differs from the steps.**
- *Each selector once* holds except for 26 selectors that the old cascade interleaved: a media
  query or an equal-specificity rule hitting the same element stands between their copies, and
  merging them would change what wins. Whether two rules hit the same element was taken from the
  DOM of 34 states (the 24 above plus folder grid, frame and audio dialogs, a missing audio file, a
  running job, motion ghosts, transitions, drop target, empty markup).
- 92 rules were dropped: every class or id they name appears in no markup or script of the
  prototype (`.file-heading`, `.canvas-toolbar`, `.style-card`, `.monitor`, …) — the first layer's
  layout, superseded by the later ones.
- The bar also follows the prototype's own navigation (Edit, Cancel, Done, the carousel, a script
  calling `setScene`), by wrapping `setScene` and `setViewer` in `sections.js`; without it the bar
  went stale the first time Edit was clicked.
- `history.replaceState` throws on a `file://` page, so the hash is written with
  `location.replace('#…')`.
