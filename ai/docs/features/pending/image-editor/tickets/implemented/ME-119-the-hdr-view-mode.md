# ME-119 — The HDR view mode

| | |
|---|---|
| **Order** | after ME-177, which gave stills a tone map to switch off |
| **Depends on** | ME-106, ME-177 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Let the author override what Woka decides about a file's colour, without touching a pixel of
it.

**Why now.** Detection has always been automatic and, since ME-177, uniform across every surface. What
it cannot do is be wrong out loud: the corpus holds three files that carry a perfect PQ description
over ordinary SDR pixels, and nothing in the app can say so.

## What the tri-state actually overrides

The backlog block called it "Force HDR / Force SDR", which reads as *send HDR to the display*. **It
cannot mean that.** Woka draws into an sRGB Skia window and there is no HDR swapchain anywhere in the
stack. The switch overrides **detection**, and the wording is corrected here rather than shipped
misleading:

| Value | What it does |
|---|---|
| `Auto` | tone map when the file's transfer says HDR — today's behaviour, and the default |
| `Force HDR` | tone map as PQ / BT.2020 even when the tags say SDR — for a mistagged file |
| `Force SDR` | never tone map; draw the codes as they are |

`Force SDR` is also the answer to *"is this file actually HDR?"* — flip it and a genuine HDR picture
loses its highlights while a tags-only fake does not move.

## Steps
1. `HdrViewMode` beside the other enums in `WokaSettings.kt`, and a field defaulting to `Auto`. The
   setter follows `setScalePreset` end to end: `SettingsRepository` → `LibrarySettingsImpl` →
   `SettingsStore`'s defaults.
2. `HdrView` in `core/data/`: a holder with `install(() -> HdrViewMode)`, the shape `AppliedStillEdits`
   already established for a seam that readers consult and DI fills. Installed beside
   `SettingsRepository` in `PlatformModule`, reading `settings.value` so it is never stale.
3. `displayable` asks it instead of asking the file alone.
4. **The mode joins both cache keys** — `ImageBitmapCache` and `WokaStorage.thumbnailFile` — rather
   than a change wiping them. A key that carries the mode means switching misses, switching *back*
   hits, and nothing has to remember to invalidate. Appended only when the mode is not `Auto`, so the
   default keeps the keys it has and no one's thumbnails are rebuilt for a feature they never touch.
5. A `SegmentedControl` row in the viewer settings panel, beside Scale.

## Files
`shared/src/commonMain/kotlin/com/woka/core/data/WokaSettings.kt` · `HdrView.kt` (new) ·
`SettingsRepository.kt` · `WokaStorage.kt` · `jvmMain/.../LibrarySettingsImpl.kt` · `SettingsStore.kt` ·
`core/ui/image/ImageBitmapCache.kt` · `feature/viewer/image/StillForDisplay.kt` ·
`feature/settings/MediaViewerSettingsPanel.kt` · `app/di/PlatformModule.kt`

## Not in this ticket

**The editor's top-bar mirror**, which the backlog block also asked for. It is a convenience — the
setting reaches the editor either way, because ME-177 put the tone map under both surfaces — and this
ticket is already over the `tickets.md` §4 file line. Raised as ME-178 rather than dropped.

## Done when
- `jvmTest/unit/`: `Force SDR` returns the decoded bitmap untouched for `hdr_real_pq.avif`, and
  `Force HDR` maps `still.jpg` although its tags say SDR;
- `jvmTest/unit/`: the cache key for `Auto` is byte-identical to the key before this ticket;
- confirmed by hand: flipping to `Force SDR` dulls the picture in the viewer, the editor **and** the
  grid, and flipping back restores it without a restart.
