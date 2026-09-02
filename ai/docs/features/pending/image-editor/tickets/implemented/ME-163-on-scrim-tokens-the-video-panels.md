# ME-163 — On-scrim tokens: the video panels

| | |
|---|---|
| **Order** | after ME-169; first of three slices |
| **Depends on** | ME-121 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Convert the eight video panel files that sit purely on `ViewerChrome` scrims from
`Color.White` literals to the `onScrim*` tokens.

**Why now.** ME-121 converted twelve sites in four files and left 46 across 21 — its own KDoc says so
("58 of them at the last count"). Every site here is drawn on one of the three scrims in
`ViewerChrome`, which are dark in both themes, so the foreground is a token by the rule ME-121 wrote.

**The mapping is not a table in the KDoc — check it.** `onScrimSecondary` (`#CDCDD2`) and
`onScrimDisabled` (`#838893`) are **solid hex**, not white-with-alpha, so converting
`Color.White.copy(alpha = 0.72f)` to `onScrimSecondary` changes the pixels slightly and deliberately —
that is the point of the token, and it is why this wants the author's eyes rather than a green build.
Only `onScrimBorder` (0.08f), `onScrimHover` (0.08f) and `onScrimSelected` (0.14f) are alpha-on-white.

## Steps
Per file, per site, with the token chosen by role and not by arithmetic:
- bare `Color.White` on a scrim → `Theme.viewer.onScrim`
- `alpha = 0.72f` / `0.8f` / `0.85f` secondary text → `onScrimSecondary`
- `alpha = 0.45f` / `0.5f` / `0.6f` / `0.3f` on a disabled or inactive control → `onScrimDisabled`
- a `0.07f`–`0.10f` fill behind a row → `onScrimHover`
- a `0.12f`–`0.15f` fill on a selected row → `onScrimSelected`
- a hairline or divider → `onScrimBorder`

A conditional like `alpha = if (enabled) 0.85f else 0.3f` becomes
`if (enabled) onScrimSecondary else onScrimDisabled` — the branch survives, the literals do not.

## Files
| File | lines | sites |
|---|---|---|
| `feature/viewer/video/PlayerSettingsSync.kt` | 149 | 6 |
| `feature/viewer/video/SubtitleCustomizePanel.kt` | 120 | 2 |
| `feature/viewer/video/SubtitleCustomizeParts.kt` | 60 | 2 |
| `feature/viewer/video/PlayerSpeedSlider.kt` | 53 | 3 |
| `feature/viewer/video/PlayerSpeedNotice.kt` | 74 | 2 |
| `feature/viewer/video/PlayerResetRow.kt` | 76 | 2 |
| `feature/viewer/video/SoundBoostRow.kt` | 91 | 2 |
| `feature/viewer/video/SubtitlePopover.kt` | 96 | 1 |

All under `shared/src/jvmMain/kotlin/com/woka/`. Twenty sites. None of these eight reads
`Theme.colors`, which is what makes this slice mechanical.

## Done when
- `grep -n "Color\.White" ` over the eight files returns nothing;
- no file grew or shrank except by the substitution — no reformatting, per the zero-unprompted-changes
  mandate;
- confirmed by hand: the sync panel, the speed slider, the subtitle customise panel and the sound
  boost row all still read against a bright photograph and a dark one;
- `./gradlew.bat :shared:build` green.
