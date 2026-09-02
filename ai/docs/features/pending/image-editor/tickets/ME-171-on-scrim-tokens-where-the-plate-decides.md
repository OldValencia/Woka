# ME-171 — On-scrim tokens: where the plate decides

| | |
|---|---|
| **Order** | after ME-170; last of three slices |
| **Depends on** | ME-170 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Settle the five files that draw on **both** a `ViewerChrome` scrim and a `Theme.colors`
plate, converting only the foregrounds that sit on the scrim.

**Why now.** ME-121's KDoc states the rule and the hazard in one breath: *"A foreground drawn on a
plate derived from `Theme.colors` keeps `Theme.colors`, because plate and text invert together…
Converting those would put `#F4F4F6` on an 80 %-white plate."* These five files are where that
mistake is available to make, which is why they are their own ticket and not folded into ME-163.

`SubtitleCustomizeControls.kt:89` is the case in miniature: one expression already branches between
`Theme.colors.accent` and `Color.White.copy(alpha = 0.3f)`. Whether that literal converts depends on
what is behind it, and nothing but reading the composable answers that.

## Steps
1. For each of the five files, first establish **what each site is drawn on**: walk up to the
   enclosing `Surface`/`background` and record whether its colour comes from `ViewerChrome` or from
   `Theme.colors`. Write the finding as a comment in the ticket's own notes before editing.
2. Convert only the scrim-backed sites, using ME-163's mapping.
3. Leave every plate-backed site alone. Where one file holds both, add a KDoc line naming the split —
   the next reader will otherwise "finish the job" and reintroduce the bug.
4. Check both themes on every file. A site that looks right in dark and wrong in light is a plate
   misread as a scrim, and that is exactly the failure this slice exists to avoid.

## Files
| File | lines | sites |
|---|---|---|
| `feature/viewer/video/SubtitleCustomizeControls.kt` | 136 | 6 — incl. the `Theme.colors.accent` branch at :89 |
| `feature/viewer/video/PlayerSettingsPlayback.kt` | 86 | 3 |
| `feature/viewer/video/MediaViewerAudioExtension.kt` | 89 | 2 |
| `feature/viewer/video/PlayerSpeedSliderParts.kt` | 44 | 2 |
| `feature/viewer/video/SubtitleActionRow.kt` | 78 | 1 |

Fourteen sites, an unknown number of them converted — deciding that is the work.

## Done when
- every remaining `Color.White` in the five files carries a comment naming the plate it sits on;
- checked in **both** themes by hand, which is the only verification that distinguishes a plate from
  a scrim;
- across ME-163, ME-170 and this ticket, `grep -rn "Color\.White" shared/src/*/kotlin/com/woka/feature/viewer/` returns only documented exclusions — the 46 sites are accounted for, converted or
  explained;
- update `ViewerChrome`'s KDoc: the sentence "58 of them at the last count" is now history, and the
  count is zero-undocumented;
- `./gradlew.bat :shared:build` green.
