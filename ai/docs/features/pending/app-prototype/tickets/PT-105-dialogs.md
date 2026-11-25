# PT-105 — Dialogs section

| | |
|---|---|
| **Order** | sixth |
| **Depends on** | PT-001 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Draw the viewer's own dialogs — file information and rename — as the app has them, and
give every dialog of the redesigned screens a link in the Dialogs section.
**Why now.** The feature doc settled that file information is the app's dialog, not
`editor-next`'s popover; and the Dialogs section was the last disabled one before the library.
What the author sees: `#dialogs/info`, `#dialogs/rename`, `#dialogs/copy`, `#dialogs/keep`,
`#dialogs/revert`.

Source: `FileInfoDialog` (340 dp with streams, 300 without; Copy and Close), `FileMetaBlock` (name
with the edited mark, path, size, created), `FileInfoStreamCards` (General / Video / Audio /
Subtitles, 13 sp section titles, cards with 10 dp radius on `surfaceHover` with a 1 dp border,
12 sp rows), `RenameDialog` (Cancel / Rename). The editor's three dialogs exist in the prototype.

## Steps

1. **Dialogs.** New `woka/dialogs.js`: `infoDialog()` from per-kind facts (still, frame set, GIF,
   video, track with tags), Copy puts the text on the clipboard; `renameDialog()` with the stem
   selected, a check for empty names, `\ / : * ? " < > |` and a name taken in the folder; F2 opens
   it in the viewer. `#media-info` opens the dialog instead of the popover.
2. **Palette.** A dialog over the viewer takes the viewer's tokens (surface, accent); over the editor
   it keeps the dark room's. `modal()` takes a class; `dialog-narrow` is 360 px.
3. **Section.** `Dialogs` is enabled with five scenes, each opening its dialog over the screen it
   belongs to; the bar stays on the scene while the dialog is open and follows the screen after.

## Files

`ai/mockups/design/woka.html`, `woka/app.js`, `woka/woka.js`, `woka/sections.js`,
`woka/viewer.css` (edit); `woka/dialogs.js` (new).

## Done when

- `#dialogs/info` over the video: meta block and three cards; Close returns the bar to
  `#viewer/video`. Palms shows the `Edited` mark.
- `#dialogs/rename`: the stem is selected; `a:b` is refused with a message; `Sunset.jpg` renames the
  file in the caption.
- `#dialogs/copy`, `#dialogs/keep`, `#dialogs/revert` open the editor's dialogs in the editor.

## Result — 2026-11-25

Built and checked in headless Edge as listed above. The editor's scenes, including Create a copy,
match PT-103's commit in computed style; over the viewer only the (closed) dialog's own colours
changed, which is the palette step.

**Where the result differs from the app.** One dialog look for both: the prototype's dialog shell
(21 px title, close button, right-aligned actions) in the context's palette, not the app's
`AbstractDialog`/`RenameDialog` styling (20 sp bold title, text buttons). `#dialogs/keep` raises the
photo's exposure to have something to keep; that edit stays in the photo's session until it is
discarded.
