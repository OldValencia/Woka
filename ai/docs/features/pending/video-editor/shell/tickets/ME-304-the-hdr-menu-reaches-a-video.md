# ME-304 — The HDR menu reaches a video

| | |
|---|---|
| **Order** | third of the round: ME-237 → ME-301 → **ME-304** → ME-305 |
| **Depends on** | ME-301 (`toColour()`, `hdrStageFor(StillColorInfo?)`) |
| **Feature** | [The shared editor shell](../shell.md) |

**Goal.** Show the `HDR · Auto / On / Off` pill on a video in the viewer and the editor, and make
every surface that draws the video honour it the way a photograph does.

**Why now.** `showHdrMode` is false for video (ME-180) because the switch reached nothing; the
stage (ME-205) and a stable white point (ME-301) were what it was waiting for. The author chose
all three modes, the photograph's meanings, 2026-11-05: **On** reads an untagged file as PQ
BT.2020, **Off** never tone maps.

**The mode is applied where the colour is read, not in the player.** A paused film is not
re-published when the mode flips, so a player-side answer would leave the frame on screen in the
old mode — the bug `HdrModeAccess` was written to close for stills (ME-180).

## Steps

1. `core/data/HdrView.kt`: `fun HdrViewMode.applyTo(color: StillColorInfo): StillColorInfo` —
   `ForceSdr` → `StillColorInfo.Sdr`; `ForceHdr` → `StillColorInfo(PQ, isWideGamut = true)` when
   `!needsToneCurve(color.transfer)`, else `color`; `Auto` → `color`. KDoc: the video's twin of
   `colourToBelieve`, which a still applies at the decode; the white point survives `ForceHdr` on
   a file that was HDR already, and an untagged one gets the fallback.
2. The viewer. `MediaViewerFrame`: `moving` moves above `color`, and
   `val color = frameColorInfo(media, frameToDraw).let { if (moving) mode.applyTo(it) else it }`
   with `mode` from `rememberHdrModeAccess().current()`. **Only a moving frame:** a still answered
   the mode at its decode (`colourToBelieve`) and arrives as sRGB pixels, so applying it again
   would tone map a still twice (ME-177). `AmbientFillLayer.kt:88` and `VideoHoverExcerpts.jvm.kt`
   draw moving frames only and apply it unconditionally. `SeekPreviewPainter` applies it to
   `streamHdrInfo?.toColour()`.
3. The editor. `VideoEditorEngine.streamColour` returns the moded colour
   (`mode.applyTo(streamHdrInfo?.toColour() ?: StillColorInfo.Sdr)`), so `Surface` and
   `Thumbnail` re-fold when it flips. `override val sourceColor by lazy { readStillColor(file) }`
   — `readStillColor` reads the first video stream's codec parameters, which a film has — so the
   editor's `CanvasZoomPill` shows the pill for an HDR film. The rail is unaffected: `HdrTool`
   supports `MediaKind.Image` only, so `VideoRailToolsTest` stands untouched.
4. The render follows the same mode: `VideoRender` passes `HdrView.mode.applyTo(probe.hdr.toColour())`.
   **Off** on a PQ film therefore encodes the signal untone-mapped — what the canvas showed — and
   with no other pixel work the render copies packets, which keeps it HDR.
5. Visibility. `MediaViewerScreen`:
   `showHdrMode = activeItem !is MediaItem.Audio && hdrControlVisible(videoState?.streamHdrInfo?.toColour() ?: state.stillColor)`;
   the comment above it and `MediaViewerEffects.kt:26`'s KDoc lose "false while a video is open".
6. Tests: `jvmTest/unit/HdrModeOnVideoTest.kt` — `applyTo` over the three modes × SDR / PQ-with-
   white-point / HLG, asserting `ForceHdr` keeps a PQ film's white point and `ForceSdr` returns
   `Sdr`. By hand: flip the pill on a paused S02E02 frame in the viewer and in the editor — the
   frame changes without playing.

## Files

`commonMain/.../core/data/HdrView.kt` · `.../viewer/shell/MediaViewerFrame.kt` ·
`.../viewer/shell/ambient/AmbientFillLayer.kt` · `.../viewer/VideoHoverExcerpts.jvm.kt` ·
`.../viewer/video/SeekPreviewPainter.kt` · `.../editor/engine/video/VideoEditorEngine.kt` ·
`.../video/render/VideoRender.kt` · `.../viewer/shell/MediaViewerScreen.kt` ·
`.../viewer/shell/MediaViewerEffects.kt` · `jvmTest/.../unit/HdrModeOnVideoTest.kt` (new)

## Done when

- the pill shows on an HDR film in the viewer and the editor and not on an SDR one under Auto;
- a still's look under all three modes is unchanged (`CanvasPillHdrUiTest`, `HdrBakeTest` green);
- `VideoEditorEngine.kt`, `MediaViewerFrame.kt` ≤ 150 lines; anti-FQN clean;
  `./gradlew.bat :shared:build` green.

## Built — 2026-11-05

As planned, with `VideoEditorEngine.streamColour` split into `frameColour` (the canvas, ME-305)
and `streamColour` (the thumbnail), both through one `moded()` helper. The two KDocs that still
said "false for video" (`MediaViewerGlobalControls`, `MediaViewerEffects`) were rewritten.
`HdrModeOnVideoTest` green; **not yet looked at by the author**.

**Changed the same day, by the author:** *«как пользователь видит, так и рендерить»*. Step 4's
"Off copies packets and stays HDR" is withdrawn: a mode that changes the picture — Off on a PQ
film, On on an SDR one — now rules the copy out (`VideoRender.modeMoves`), so the file is always
what the canvas showed.
