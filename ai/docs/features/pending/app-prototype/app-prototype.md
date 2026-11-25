# App Prototype — `woka.html`

## Goal and status

One living HTML prototype of the whole app — library, viewer, editor, dialogs — in
`ai/mockups/design/woka.html`. It grows out of `editor-next.html`, the editor concept drawn for the
1.0.9 redesign (`next_steps.md`: the current editor did not justify itself in UX terms), and it
replaces `FINAL_design_mockup.html` as the visual truth.

**What it shows.** The **target** design first: the screens 1.0.9 redesigns (viewer, both editors)
are drawn as they are meant to become, every other screen is ported from the code as it is. Once a
redesigned screen ships, its scene describes the app as it is, and a later proposal changes the
prototype before it changes the code (`feature-docs.md` §6: the mockup is a spec).

**What it is not.** No media processing — the picture is one screenshot under CSS filters, as in
`editor-next`. Not the Compose implementation of the redesign: that is separate features, written
against this prototype once its viewer and editor scenes are settled.

Plan agreed with the author 2026-11-25, in conversation.

| Ticket | Status |
|---|---|
| [PT-001 — rename, one token file, sections and links](tickets/PT-001-structure.md) | done 2026-11-25, accepted by the author |
| [PT-002 — one transport, GIF, frame strip on the left](tickets/PT-002-viewer-transport.md) | done 2026-11-25, accepted by the author |
| [PT-101 — audio in the viewer and Now Playing](tickets/PT-101-audio.md) | done 2026-11-25, accepted by the author |
| [PT-102 — carousel badges and Original in the viewer](tickets/PT-102-badges-original.md) | done 2026-11-25, accepted by the author |
| [PT-103 — fullscreen and PiP](tickets/PT-103-fullscreen-pip.md) | done 2026-11-25, accepted by the author |
| [PT-105 — dialogs section](tickets/PT-105-dialogs.md) | done 2026-11-25, accepted by the author |
| [PT-201 — sidebar, toolbar, grid](tickets/PT-201-library-grid.md) | built 2026-11-25 |
| [PT-202 — search, Info, Settings, Quick Look, selection](tickets/PT-202-library-panels.md) | built 2026-11-25 |
| PT-104, PT-301 | [backlog](tickets/backlog.md) |

## Starting point (read 2026-11-25)

- `editor-next.html` + `editor-next/` — 13 files, ~190 KB, untracked in git. Five editor scenes
  (photo, portrait crop, frame set, video, video + music) and a viewer mode inside the same shell:
  sidebar, zoom pill, prev/next, video playback bar, carousel. The picture stays in place while the
  panels change around it (`experience.js`, `setViewer`).
- Styling is **five cascading layers**: `styles.css` (its own neutral palette, accent `#ffb34c`) →
  `experience.css` → `woka.css` (re-skins to `Theme.kt`: `#0e1015`, brand `#7b93ff`, editor
  `#ff9f0a`) → `refine.css` → `look.css` (a second, editor-only "dark room" palette `--e-*`). A
  value's final colour is found by reading all five. This, not the palette, is what blocks using
  the file for the whole app.
- Missing against the app: GIF, audio, fullscreen, PiP, the HDR control in the pill, every library
  screen, every dialog other than the editor's own.
- Divergent between viewer and editor of the same file: the frame strip (app viewer: vertical, left;
  `editor-next` editor: horizontal, bottom) and the playback controls (viewer bar vs. timeline
  transport).

## Prototype architecture

**Files.** `woka.html` + `woka/`, opened by double-click — no build step, no CDN (`feature-docs.md`
§6). Rejected: one self-contained HTML — 190 KB in one file, the Phosphor table alone is 26 KB.
Rejected: a framework or bundler — §6 forbids a build step, and it would be the only one in `ai/`.

**Styles.** One `tokens.css` holds every colour, both themes, named after `Theme.kt` (`Colors`,
`LightColors`, `DarkColors`) plus `ViewerChrome` and the editor's room tokens. Every other stylesheet
uses tokens only — a hex literal outside `tokens.css` is a defect. Per-area sheets (`base`, `shell`,
`library`, `viewer`, `editor`, `dialogs`) each own their selectors; a selector is written once.
Where the prototype's colour differs from `Theme.kt`, the prototype keeps its value and the gap is
listed for the author (decision log): the prototype is the target, and `Theme.kt` changes only
when a redesign ticket implements it.

**Navigation.** The prototype bar carries a section switcher (Library · Viewer · Editor · Dialogs)
and the scenes of the selected section. The location is a hash, `#viewer/gif`, read on load and on
`hashchange` — a scene can be linked from a ticket and screenshotted without clicking. Each scene
keeps its own session (`sceneSessions`, as today).

**Verification.** `ai/mockups/tools/shot.mjs` — headless Edge driven over the DevTools protocol by
Node's built-in `WebSocket`, one PNG per hash and theme. The Chrome extension is disabled by the
author's organisation; this is how a change to the prototype is seen before it is reported.
"Nothing moved" is a comparison of every element's computed style (`--styles`), not of PNG bytes:
the same page screenshotted twice already differs in bytes (PT-001, measured 2026-11-25).

## Viewer — the target design

**One viewer, not one per kind.** What differs between kinds is the bottom area and the left strip;
the shell, the pill and the carousel are the same. This is how the code is already cut
(`ViewerMedia`: still / video / audio; `FrameStripModel` for files with more than one picture).

| Media | Left strip | Bottom | Pill |
|---|---|---|---|
| Photo | — | carousel | size · info · carousel · Edit |
| Burst / HEIC set / TIFF pages / Live Photo | frames | carousel | same |
| GIF / animated WebP | frames | transport (GIF) + carousel | same |
| Video | — | transport (video) + carousel | same, HDR control when HDR |
| Audio | — | cover stage, transport (audio) | info · carousel (no size) |

**Two axes.** The bottom carousel moves between files of the folder; the left strip moves inside one
file. The strip stands in the same place in the viewer and in the editor, so entering the editor
does not move it. PDF pages (1.1.1) and comic pages (1.1.2) are "inside the file" and take the same
strip.

**One transport, three slot sets.** Same bar, same geometry; only the right-hand group changes.

| | left | centre | right |
|---|---|---|---|
| Video | play · time · −5 s | seek | +5 s · duration/remaining · subtitles · volume · settings · fullscreen |
| GIF | play · time/frame · frame back | seek by frame | frame forward · duration · loop · speed |
| Audio | play · time · −5 s | seek | +5 s · duration · volume · settings |

`,` and `.` step one frame — a GIF always, a video while paused. Arrow keys keep moving between files.

**Also in the target viewer.**
- Carousel badges: duration on a video, `GIF`, frame count on a set, a dot on a file with an edit.
- `Original` in the pill for a file with an edit — click or hold shows the source, the same control
  and shortcut (`\`) as in the editor. Absent on a file without an edit.
- The caption under the carousel: file name · `3 of 7`.
- The size menu says which kind its mode is remembered for (1.0.9 per-kind size; where the boundary
  between short video and film lies is that plan's question, not this one's).
- Fullscreen with the chrome hidden until the pointer moves; PiP compact, without the zoom buttons.
- File info is the app's dialog with stream cards, not `editor-next`'s popover.

## Editor

The five `editor-next` scenes stay as they are, with three changes that follow from the viewer: the
frame strip moves to the left, the play/time part of the timeline transport is the viewer's
component, and the pill carries the HDR control.

## Library and dialogs

Ported from the code, not redesigned: sidebar (tree, Pinned, Now Playing row, Link folder…),
toolbar (folder name, View, Pin, search, Info, Settings), grid (folder mosaic and stack cards, media
cards, watched progress), selection bar, Quick Look, search results, Info panel, Settings overlay;
dialogs — file info, rename, create a copy, keep changes, revert.

## Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-11-25 | What the prototype shows | Target design now; the app as it is once a scene ships | The author: the redesign needs a target; later the file is the reference for edits and new designs |
| 2026-11-25 | Separate viewers per kind | No — one shell, a capability table | The code already models it (`ViewerMedia`, `FrameStripModel`); kinds differ only in two slots |
| 2026-11-25 | Where the frame strip stands | Left, vertical, in viewer and editor | Bottom already holds transport and carousel; the picture must not move on entering the editor; PDF and comic pages reuse it |
| 2026-11-25 | GIF controls | The video transport with GIF slots, plus the strip | The author: today's click/Space pause is hidden; show the actions, keep the strip |
| 2026-11-25 | Video frame step | Keys only, while paused | The ±5 s buttons stay where GIF has frame buttons; one more pair of buttons on the video bar buys little |
| 2026-11-25 | Name and status | `woka.html` replaces `FINAL_design_mockup.html` as visual truth | The author agreed; references switch when the library scenes exist (PT-301), not before |
| 2026-11-25 | Library screens | Ported as they are | Not part of the 1.0.9 redesign; a proposal for them is a later change to the prototype |
| 2026-11-25 | Colours that differ from `Theme.kt` | Prototype keeps its value, the gap is listed | The prototype is the target; `Theme.kt` changes with the ticket that implements a screen |
| 2026-11-25 | Reserved place for 1.1.3 modes (slideshow, compare, eyedropper) | Not drawn; the pill's right group is where they go | An empty slot is a guess at a layout that 1.1.3 has not planned |
| 2026-11-25 | How the prototype is checked | Headless Edge over CDP, `shot.mjs` | The Chrome extension is disabled by the author's organisation |
| 2026-11-25 | What proves a restyle changed nothing | Computed styles of every element, not PNG bytes | Two runs of the unchanged page differed in 6 of 48 PNGs; their computed styles matched exactly |
