# ME-170 — On-scrim tokens: the overlays

| | |
|---|---|
| **Order** | after ME-163; second of three slices |
| **Depends on** | ME-163 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Convert the seven remaining pure-scrim files — the overlays, the tooltip, the PiP flash —
to the `onScrim*` tokens.

**Why now.** The other half of ME-163's sweep, split at the ~8-file line `tickets.md` §4 sets. Same
mapping, same rule, different surfaces.

## Steps
Same substitution table as ME-163. Two sites need a decision rather than a lookup:

1. **`MediaViewerVolumeOverlay.kt:109`** — the bare `Color.White` is the fallback of an elvis, so the
   non-null branch decides the role. Read what feeds it before choosing the token.
2. **`PipCornerFlash.kt:39`** — `Color.White.copy(alpha = alpha)` where `alpha` is animated. This is
   a **flash**, not a foreground: it is the corner of the screen lighting up, and its colour is the
   effect. Leave it a literal and write one line of KDoc saying why, the way ME-121 did for the
   slider tracks.

**Excluded, and stated so in the code:**
- `MediaVerticalSlider.kt:47` — `Color.White.copy(alpha = 0.2f)`, the unplayed track's 20 % default.
  ME-121's KDoc names this exact line: a value used by a single slider is a constant, not a palette
  entry. Line 46's `activeColor = Color.White` **is** a foreground and does convert.
- `MediaSliderTrack.kt:68-69` — both `Color.White`s are `lerp` targets for `HOVER_LIGHTEN` and
  `PREVIEW_LIGHTEN`. The file is not touched at all by this ticket.

## Files
| File | lines | sites | converted |
|---|---|---|---|
| `feature/viewer/video/MediaViewerVolumeOverlay.kt` | 124 | 2 | 2 |
| `feature/viewer/video/PlaybackHealthNotice.kt` | 121 | 2 | 2 |
| `feature/viewer/video/VideoReloadOverlay.kt` | 43 | 1 | 1 |
| `feature/viewer/video/VolumeSliderFill.kt` | 91 | 1 | 1 |
| `feature/viewer/shell/MediaViewerTooltip.kt` | 42 | 1 | 1 |
| `feature/viewer/shell/slider/MediaVerticalSlider.kt` | 119 | 2 | 1 |
| `feature/viewer/pip/PipCornerFlash.kt` | 98 | 1 | 0 — documented instead |

Nine sites, eight converted.

## Done when
- `grep -n "Color\.White"` over the seven files returns only `MediaVerticalSlider.kt:47` and
  `PipCornerFlash.kt:39`, each carrying a KDoc line saying why it stays;
- `MediaSliderTrack.kt` is untouched — `git diff --stat` does not name it;
- confirmed by hand: the volume overlay, the reload spinner and the tooltip over a white photograph;
- `./gradlew.bat :shared:build` green.
