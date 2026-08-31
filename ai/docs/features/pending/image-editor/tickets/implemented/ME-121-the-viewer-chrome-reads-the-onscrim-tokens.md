# ME-121 — The viewer's on-scrim chrome reads the tokens, not `Color.White`

| | |
|---|---|
| **Order** | 70 — independent of everything; nothing waits on it |
| **Depends on** | ME-012 (the tokens exist) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Replace the `Color.White` literals drawn on `ViewerChrome`'s scrims with the `onScrim*`
tokens that were declared for them, in the viewer shell's own cluster, and write the rule that decides
which surfaces follow — correctly this time.

**Why now — and it is not what the backlog block said.** Rewritten 2026-09-01, against the tree,
before a line was changed. **Nothing in the viewer is illegible, and the block's premise was wrong.**

`ViewerChrome`'s KDoc measures 1.05 : 1 *against `hudScrim`* — an always-dark plate. The viewer's
`Theme.colors` foregrounds are not on it:

- **the control pill** — `MediaViewerControlsContainer:34` paints `Theme.colors.surface`, which is
  `0xCCFFFFFF` light and `0xCC161A22` dark. The plate inverts with the theme, and so does the text on
  it. `InfoButton`, `EditButton` and `ToggleCarouselButton` are all inside it;
- **the arrows and the video bar** — `MediaViewerBackgroundPath:79` does `clipPath(path) {
  drawContent() }` over round-rects placed exactly under those controls, so the toolbar's two
  `Theme.colors.background` washes (0.35, then 0.4 over it — about 61 % coverage) land *only* there. A
  theme-coloured plate again.

Which is why the two populations the block tried to intersect are disjoint: **8** files draw on a
`ViewerChrome` scrim and take their foreground from `Color.White`; **28** read `Theme.colors` and sit
on a `Theme.colors` plate. Converting the second set is not a fix — it would put `onScrim` (`#F4F4F6`)
on an 80 %-white plate in the light theme, which is the bug the block claimed to be fixing.

So what is left is real but smaller: **58 `Color.White` literals across 25 files**, which is exactly
the pattern `ViewerChrome` exists to end — its own KDoc opens by saying it replaced "11 scattered
`Color.Black.copy(alpha = …)` literals". The scrims were tokenised in ME-012 and the foregrounds on
them never were. This buys one thing and it is worth stating plainly: a later palette edit reaches
every one of them instead of 58 hand-written whites.

**This is hygiene, not a defect fix, and it changes pixels.** `Color.White` → `onScrim` is `#FFFFFF` →
`#F4F4F6`; `White.copy(0.10f)` → `onScrimHover` is 0.10 → 0.08. Each shift is small and each is on a
shipped surface, which is why this is sliced and why the by-hand pass is the verification.

## Steps

1. **`core/ui/theme/ViewerChrome.kt` (edit, 92 lines)** — the rule, and the correction. What takes
   `onScrim*` is what is drawn on one of the three scrims **in this object**; what is drawn on a plate
   derived from `Theme.colors` keeps `Theme.colors`, because plate and foreground invert together. The
   stale sentence — "`feature/viewer/` still reads `Theme.colors` at ~27 call sites and is migrated by
   its own ticket" — is **replaced**, because it is what sent this ticket the wrong way once already.

2. **`shell/MediaOverlays.kt` (edit)** — three sites on `hudScrim`: two `ColorFilter.tint(Color.White)`
   → `onScrim`, and `hudLabel.copy(color = Color.White)` → `onScrim`.

3. **`shell/PopoverListRow.kt` (edit)** — five sites on `popoverScrim`, and the one that needs a
   decision rather than a substitution: `White.copy(0.10f)` and `White.copy(0.18f)` are two hover
   states, and the tokens offer `onScrimHover` (0.08) and `onScrimSelected` (0.14). The pair maps to
   the pair; a third alpha would mean the token set is short one value, and that is a `ViewerChrome`
   change, not a call-site one.

4. **`shell/slider/MediaSliderTooltip.kt` (edit)** — three sites on `tooltipScrim`: the border to
   `onScrimBorder`, the primary label to `onScrim`, the secondary to `onScrimSecondary`. The
   `0.7f`-when-a-chapter-is-named case is what `onScrimSecondary` is.

5. **`shell/MediaViewerCarousel.kt` (edit)** — one tint on a scrimmed thumbnail overlay.

6. **`shell/slider/MediaSliderTrack.kt` and `MediaVerticalSlider.kt` — decided, and left alone.**
   Their whites are not foregrounds: one lightens a track *toward* white by a constant, the other
   defaults an unplayed track to 20 % white, for which there is no token. Inventing one for a single
   slider would put a constant in the palette. The boundary is recorded in step 1's KDoc rather than
   in a comment on two files this ticket otherwise does not touch.

## Files

`core/ui/theme/ViewerChrome.kt` (edit) under `shared/src/commonMain/kotlin/com/woka/`;
`feature/viewer/shell/MediaOverlays.kt` (edit), `PopoverListRow.kt` (edit),
`MediaViewerCarousel.kt` (edit), `shell/slider/MediaSliderTooltip.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/`; `ai/docs/features/pending/image-editor/image-editor.md` (edit —
a §9 row and the status table), `tickets/backlog.md` (edit — ME-163 restated against the real count).

## What this ticket deliberately does not touch

- **The 28 `Theme.colors` files.** They sit on `Theme.colors` plates. Converting them is the bug.
- **`MediaSliderStyle`** — its `inactiveColor` is `Theme.colors.textSecondary` on the clipped
  `background` wash, so it inverts with the theme like everything else on that plate.
- **The two tooltips.** `SimpleTooltip` (`tooltipScrim`, white, 300 ms, seven video call sites) and
  `ViewerTooltip` (`Theme.colors.surfaceElevated`, 800 ms, the control pill and five editor sites) are
  two live implementations with different palettes *and* different delays. The split is coherent for
  the viewer and odd for the editor, which is `onScrim` everywhere else — a question for the editor,
  raised here and answered elsewhere.

## Verification

- `jvmTest/unit/ViewerChromeTest.kt` (new) — the contrast of each foreground over each of the three
  scrims, composited on a mid-grey stand-in for a photograph: primary clears 4.5 : 1, secondary clears
  3 : 1, and the three stay in order so "unavailable" cannot quietly come to look available.
  **Arithmetic rather than a rendered test**, because `Theme.viewer` is a plain getter over an object
  and "the same under both themes" is true by construction — a UI test there would assert the
  compiler. What can regress is a token being dimmed, and that is a number;
- `jvmTest/ui/FrameStripUiTest.kt`, `ViewerHarness`-driven shell tests — unchanged and still green,
  which is what says nothing but colour moved;
- **by hand**: the volume HUD, a popover list, the seek tooltip and the carousel, over a white
  photograph and a black one, in both themes. This is the verification; the tests are the guard.

## Done when

- no `Color.White` literal remains in the four converted files;
- `ViewerChrome`'s KDoc states the rule and no longer claims the viewer is mid-migration off
  `Theme.colors`;
- the 28 `Theme.colors` files are untouched;
- `grep -rn "Color.White" shared/src/jvmMain/kotlin/com/woka/feature/viewer/ | wc -l` falls from 58 to
  46 — twelve sites, and the two slider files are deliberately not among them;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
