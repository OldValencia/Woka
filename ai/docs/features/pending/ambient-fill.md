# Plan: Ambient Fill (Ambilight)

Fills the empty side bars of a fullscreen video with a slow, heavily blurred, dimmed derivative of
the picture, instead of leaving flat black. Extends Phase 4 (The Viewer); no roadmap phase changes.

## Decisions taken up front

- **Side bars only.** The layer is composed only when the drawn picture is narrower than its
  container. Top/bottom letterbox on 2.39:1 content is authorial and stays black; a 16:9 file on a
  21:9 panel is a monitor mismatch and is what this fills.
- **Fullscreen only.** The windowed viewer keeps its plain background. One boolean at the two call
  sites, so widening the scope later is a one-line change.
- **Video only.** Stills are out of scope for this plan — `AmbientFillLayer` takes an `ImageBitmap`
  and knows nothing about the player, so a still can be wired in later without touching it.
- **One global setting, two entry points.** The ⚙ player popover and the Settings → Video Player
  panel write the same `WokaSettings.ambientFill`. This follows Repeat / Shuffle / Playback Speed
  (§6), which already work that way in `PlayerSettingsMenuList`. No per-file override, no intensity
  slider: if the shipped constants need a slider, the constants are wrong.
- **HDR goes through the existing shader.** The sampled bitmap keeps the source's encoded values and
  the frame's `HdrInfo`, and is drawn through the same painter selection as `MediaViewerFrame` —
  extracted in Phase 3 so there is exactly one copy of that decision. Frames are `RGBA_8888`
  regardless of transfer (`VideoPlayerExt.kt:30`), so the downscale is format-neutral.
- **Off by default.** It is a taste feature and it changes every fullscreen video.

## Phase 1: Setting

- Add `val ambientFill: Boolean = false` to `WokaSettings`, in the media-viewer group near
  `mediaViewerCarouselMode`, with a comment stating: fullscreen side bars only, off by default.
- Add `fun setAmbientFill(enabled: Boolean)` to the `SettingsRepository` interface.
- Implement it in `SettingsRepositoryImpl` as `update { it.copy(ambientFill = enabled) }`.

**File-size note:** `SettingsRepositoryImpl.kt` is at 147 lines. The override above puts it at ~149 —
inside the mandate but with nothing left. Any further setter added to it needs a split first.

**Files:** `core/data/WokaSettings.kt`, `core/data/SettingsRepository.kt`,
`core/data/SettingsRepositoryImpl.kt`

## Phase 2: Settings UI

- New `feature/settings/AmbientSettingsSection.kt` (commonMain): a `@Composable
  AmbientSettingsSection(settings, repo)` rendering `SectionLabel("Ambient fill")` plus one
  `SettingsToggleRow(title = "Ambient fill", subtitle = "Fill the black bars beside a fullscreen
  video with a soft glow taken from the picture", checked = settings.ambientFill, onCheckedChange =
  { repo.setAmbientFill(it) })`.
- Call it from `VideoPlayerSettingsPanel` (which reads the repository itself) after the `System`
  section, with a preceding `Spacer(height = 20.dp)`. Its own section rather than inline rows:
  `VideoPlayerSettingsPanel.kt` is at 134 lines and cannot absorb the body.
- In `PlayerSettingsMenuList`, add a `PopoverSectionLabel("Picture")` plus a `PopoverListRow(label =
  "Ambient fill", selected = settings.ambientFill, onClick = { repo.setAmbientFill(!settings.ambientFill) })`,
  placed after `PlayerSyncSection` and before the `Tracks` section.

**Files:** `feature/settings/AmbientSettingsSection.kt` (new),
`feature/settings/VideoPlayerSettingsPanel.kt`, `feature/viewer/video/PlayerSettingsMenu.kt`

## Phase 3: Extract the painter decision

`MediaViewerFrame` currently decides `HdrImagePainter` vs `BitmapPainter` inline
(`MediaViewerFrame.kt:49-61`). The ambient layer needs the identical decision, and duplicating it is
how the two drift apart.

- New `feature/viewer/shell/ViewerFramePainter.kt` (jvmMain) holding:
  - `fun frameColorInfo(media: ViewerMedia, drawn: ImageBitmap): StillColorInfo` — the existing
    "decoded frame answers for itself, otherwise the still's own description" logic, moved verbatim.
  - `@Composable fun rememberFramePainter(bitmap: ImageBitmap, color: StillColorInfo): Painter` —
    the existing `if (color.needsColorManagement)` branch, moved verbatim.
- Rewrite those lines in `MediaViewerFrame` as two calls. No behaviour change.

**Files:** `feature/viewer/shell/ViewerFramePainter.kt` (new),
`feature/viewer/shell/MediaViewerFrame.kt`

## Phase 4: The sampler

New package `feature/viewer/shell/ambient/` (jvmMain), alongside `zoom/` and `slider/`.

- `ambient/AmbientSampler.kt` — a `@Stable class AmbientSampler` holding:
  - `var image by mutableStateOf<ImageBitmap?>(null)` and `var color by mutableStateOf(StillColorInfo.Sdr)`
    — the published low-resolution accumulator and the transfer it was sampled under.
  - A private `FloatArray(AMBIENT_W * AMBIENT_H * 4)` accumulator.
  - `fun blend(frame: ImageBitmap, hdr: HdrInfo?)`:
    1. `Image.makeFromBitmap(frame.asSkiaBitmap())`, `scalePixels` into a
       `Bitmap().allocN32Pixels(AMBIENT_W, AMBIENT_H).peekPixels()` with `SamplingMode.MITCHELL`.
    2. Exponential moving average into the accumulator at weight `BLEND`; first sample seeds it
       outright so a newly opened file does not fade up from black twice.
    3. `SkiaImage.makeRaster(ImageInfo(AMBIENT_W, AMBIENT_H, ColorType.RGBA_8888,
       ColorAlphaType.OPAQUE), bytes, AMBIENT_W * 4).toComposeImageBitmap()` into `image` — the same
       construction as `BitmapSubtitleRects.kt:60-61`. A fresh 9 KB bitmap per tick, because
       mutating the published one would not invalidate the draw.
  - Constants: `AMBIENT_W = 64`, `AMBIENT_H = 36`, `SAMPLE_INTERVAL_MS = 100L`, `BLEND = 0.12f`
    (≈800 ms time constant — this is what stops the fill flickering on hard cuts).
- **Frame retention (§6):** `blend` copies and never stores the reference it was handed. It must not
  call `frameRetention.acknowledge` — it does not draw the decoder's bitmap, and acknowledging would
  advance the close mark for a frame no scene has drawn. The copy is safe because a frame is closed
  only after 16 strictly newer frames have been drawn (`FrameRetention.kt:14`), ≈660 ms at 24 fps,
  against a downscale measured in milliseconds.

**Files:** `feature/viewer/shell/ambient/AmbientSampler.kt` (new)

## Phase 5: The layer

- `ambient/AmbientFillLayer.kt` (jvmMain) — `@Composable internal fun AmbientFillLayer(player:
  VideoPlayerState)`:
  - `val sampler = remember(player) { AmbientSampler() }`.
  - `LaunchedEffect(player)`: loop `while (isActive)` reading `player.currentFrame` /
    `player.currentFrameHdrInfo`, calling `sampler.blend(...)`, then `delay(SAMPLE_INTERVAL_MS)`.
  - Draws `sampler.image` through `rememberFramePainter(image, sampler.color)` in an `Image` with
    `contentScale = ContentScale.Crop`, `colorFilter` = saturation `AMBIENT_SATURATION`, and
    `Modifier.fillMaxSize().graphicsLayer(scaleX = AMBIENT_OVERSCAN, scaleY = AMBIENT_OVERSCAN,
    alpha = fade, renderEffect = BlurEffect(AMBIENT_BLUR, AMBIENT_BLUR, TileMode.Decal))`.
  - `fade` is `animateFloatAsState(if (sampler.image != null) AMBIENT_ALPHA else 0f, tween(400))`, so
    it comes up rather than snapping on the first sample.
  - Starting constants: `AMBIENT_ALPHA = 0.42f`, `AMBIENT_SATURATION = 1.15f`, `AMBIENT_BLUR = 64f`,
    `AMBIENT_OVERSCAN = 1.06f`. `Crop` covers the container by scaling on width, so the vertical
    crop on a 16:9 source in a 21:9 panel is the intended one; the overscan hides the blur's edge
    falloff.
  - The blur is what turns a 64×36 bilinear upscale into a gradient. If banding or shimmer shows on
    high-detail content, the first knob is an intermediate ÷8 downscale pass in `blend`, not a
    larger blur radius.

**Files:** `feature/viewer/shell/ambient/AmbientFillLayer.kt` (new)

## Phase 6: Wiring

- `MediaViewerContent` gains `ambientFill: Boolean = false` (last parameter, defaulted so
  `ViewerPanModeUiTest` is untouched). Inside the root `Box`, **before** the `AnimatedContent`:
  ```
  if (ambientFill && media is ViewerMedia.Video && hasSideBars) AmbientFillLayer(media.player)
  ```
  `hasSideBars` is computed from the values already in scope: fitted width is
  `nativeSize.width * min(cw / nw, ch / nh)`, drawn width is that times `renderScale`, and the bars
  exist when drawn width is below `containerSize.width - 2f`. Guard `containerSize`/`nativeSize`
  being zero.
- `ViewerChromeState` gains `val ambientFill: Boolean = false`.
- `MediaViewerScreen` passes `settings.ambientFill` when it builds `ViewerChromeState`
  (`MediaViewerScreen.kt:116`).
- `MediaViewerPlayer` passes `ambientFill = chrome.isFullscreen && chrome.ambientFill` to
  `MediaViewerContent`.
- `MediaViewerFullscreenSurface` reads `settingsRepo.collectField { it.ambientFill }` alongside the
  existing `pipAlwaysOnTop` read and passes `ambientFill = enabled && !PipManager.isPipWindow` — the
  window serves both PiP and fullscreen, and a PiP window has no bars worth filling.

**Files:** `feature/viewer/shell/MediaViewerContent.kt`, `feature/viewer/shell/ViewerContext.kt`,
`feature/viewer/shell/MediaViewerScreen.kt`, `feature/viewer/shell/MediaViewerPlayer.kt`,
`feature/viewer/shell/MediaViewerFullscreenSurface.kt`

## Verification

- `./gradlew.bat :shared:compileKotlinJvm -q`, then the anti-FQN grep over every edited `.kt`.
- 16:9 SDR file, fullscreen on a wide panel: bars carry a glow, the picture's own edge stays sharp,
  and a hard cut moves the fill over ≈1 s rather than snapping.
- Same file at a zoom that fills the width: the layer is not composed at all.
- 2.39:1 file on a 16:9 panel: top/bottom bars stay black.
- HDR10 (PQ) file: the fill is tone mapped like the picture — no clipped, blown-out bars.
- Toggle off in the ⚙ popover mid-playback: the fill disappears and the Settings panel row follows.
- PiP window: no fill. Windowed viewer: no fill.
- Task Manager / frame pacing on a 4K HDR file with the fill on versus off — the sampler is one
  64×36 resample per 100 ms, so a measurable drop means the blur radius, not the sampler.
