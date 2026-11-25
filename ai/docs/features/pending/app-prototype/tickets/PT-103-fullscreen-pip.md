# PT-103 — Fullscreen and PiP

| | |
|---|---|
| **Order** | fifth |
| **Depends on** | PT-002 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Draw the video's fullscreen window and the same window shrunk to picture-in-picture, with
the chrome that leaves while the film plays.
**Why now.** The viewer's target design names both (feature doc, "Also in the target viewer"), and
the 1.0.9 items — PiP without zoom buttons, a resize that keeps the frame's proportions, Alt to
free them — have nowhere to be drawn until they exist. What the author sees: `#viewer/fullscreen`
and `#viewer/pip`.

Source: the app — `MediaViewerFullscreenWindow`, `MediaViewerFullscreenControls` (the pill is the
size control, a divider and the PiP button), `FullscreenChromeVisibility` (2.5 s idle while playing;
a paused film or an open popover keeps the chrome), `PipWindowMode` (480 px wide, height from the
film, 24 px from the work area's bottom-right corner).

## Steps

1. **Window mode.** New `woka/fullscreen.js`: `windowMode` (`window | fullscreen | pip`) and
   `setWindowMode`. Fullscreen is the existing `focus-view` class (sidebar, header, carousel and
   caption already go); PiP adds `pip-view`. Only a video in the viewer has either; `syncWokaChrome`
   drops back to `window` otherwise. Esc leaves either.
2. **Pill.** `#pip-toggle` after the size control, shown only in fullscreen and PiP, tinted
   `--accent-folders` in PiP (`PipWindowButton`). Info, the viewer group (Original, carousel, Edit),
   the HDR control and the prev/next arrows are hidden in both; in PiP the size control too.
3. **Transport.** The fullscreen slot enters fullscreen and, once there, leaves it (collapse
   glyph). In PiP the ±5 s, subtitles and settings buttons are hidden so the bar fits 480 px.
4. **Idle chrome.** Pointer movement or a key shows the pill and the transport and restarts a
   2.5 s clock; when it runs out while the film plays and no popover is open, both fade and the
   cursor hides. Pausing brings them back.
5. **PiP window.** `#editor` becomes a 480 × 270 box anchored 24 px from the bottom-right corner,
   over a blurred stand-in for the desktop (`#pip-desktop`). A handle on its top-left corner resizes
   it from there: the width drives a 16:9 height, Alt frees the height; minimum 320 px wide.
6. **Links.** `fullscreen` and `pip` are viewer scenes in `sections.js`; the bar follows the mode.

## Files

`ai/mockups/design/woka.html`, `woka/transport.js`, `woka/woka.js`, `woka/app.js`,
`woka/sections.js`, `woka/viewer.css` (edit); `woka/fullscreen.js` (new).

## Done when

- `#viewer/fullscreen`: picture on black, pill `size | PiP`, transport at the bottom; no sidebar,
  carousel, arrows, Info or Edit. Paused, the chrome stays; playing, it fades after 2.5 s and comes
  back on pointer movement.
- `#viewer/pip`: 480 × 270, 24 px from the bottom-right; the pill holds only the PiP button; the
  handle resizes at 16:9, with Alt freely.
- Esc returns to the viewer and the bar to `#viewer/video`.

## Result — 2026-11-25

Built as above and checked in headless Edge: fullscreen shows size | PiP and the transport, hides
the rest; paused, no idle after 2.8 s; playing, idle after 2.8 s; a pointer move clears it. PiP is
480 × 270 at 24/24 px with only the PiP button; dragging the handle 160 px gives 640 × 360, with
Alt 640 × 464. Esc → `window`, hash `#viewer/video`. Every scene of PT-101/102's commit matches it
in computed style once the inserted elements are allowed for.

**Where the result differs from the app.** The app's fullscreen pill also carries a carousel button
that is deliberately inert there; it is not drawn — a control that does nothing in this window.
The PiP window's right-click menu (always on top) is not drawn.
