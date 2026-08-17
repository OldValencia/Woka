# ME-012 — Editor mockup and tokens

| | |
|---|---|
| **Order** | 13 — independent, can run in parallel with ME-003…ME-011 |
| **Depends on** | nothing |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** The visual specification, in one HTML file, plus the dp/ms constants the shell will read.

**Why now.** §4.2 of the feature doc holds design intent in prose. Prose disagrees with code silently;
a mockup disagrees visibly.

## Steps

1. `ai/mockups/design/editor.html`: the layout from §4.2 — top bar, canvas, inspector, rail — in both
   themes with a toggle, per `ai/docs/conventions/feature-docs.md` §6. Self-contained, no CDN, opens
   by double-click. Every state that has to be built:
   - a tool selected, unselected, and disabled;
   - `Done` dirty and not dirty;
   - one inspector row per `ParamSpec` type, including the preview strip and the colour swatches;
   - the "operations from a disabled tool are not applied" notice;
   - the save menu — `Save a Copy` with the generated filename, `Overwrite` as the secondary entry;
   - the overwrite confirmation, stating that no copy is kept, with the "Don't ask again this session"
     option drawn in both its states;
   - the HDR view-mode control in the top bar (§6.1);
   - the save banner in its three states, and the conversion sheet.
2. Take colours from `core/ui/theme/Theme.kt` (`accentEditor` = `#BF5AF2`) and geometry from
   `ViewerChrome` — the editor must look like the viewer's controls, not like a new app.
3. `EditorTokens.kt` (jvmMain, `feature/editor/shell/`): the same values as Kotlin constants, each
   commented with the mockup element it corresponds to. Nothing else in the editor declares a dp
   literal for chrome (`code-style/composable.md`).
4. Replace the concrete values in §4.2 of the feature doc with a pointer to the mockup, so there is one
   source and not two.

## Files

`ai/mockups/design/editor.html` (new), `jvmMain feature/editor/shell/EditorTokens.kt` (new),
`ai/docs/features/pending/media-editor/media-editor.md` (edit)

## Done when

- the mockup opens by double-click with no network access, and both themes render;
- every value in `EditorTokens.kt` appears in the mockup, and every chrome value in the mockup appears
  in `EditorTokens.kt`.

## Result — 2026-08-18

`ai/mockups/design/editor.html` (new, self-contained), `feature/editor/shell/EditorTokens.kt` (new),
`core/ui/theme/ViewerChrome.kt` (extended), `media-editor.md` (§4.2 + decision log + status),
`tickets/backlog.md` (ME-121).

### The theme question the ticket could not answer on its own

Step 1 demands both themes with a toggle. §4.2 says the editor is full-bleed over the viewer's dark
backdrop, and `ViewerChrome`'s own doc says it "stays dark in both themes". Read together that makes a
toggle produce two identical screens.

Resolved by measuring instead of reading doc comments. **The viewer does follow the theme** — 27 files
under `feature/viewer/` read `Theme.colors`; `ViewerChrome` describes one scrim layer, not the viewer.
So the editor follows it too.

But that exposed a real defect. Chrome over media took its *background* from a scrim here and its
*text* from `Theme.colors.textPrimary`, which in the light theme is `#1C1C1E`. Against `hudScrim`:

| media behind | contrast of `#1C1C1E` on the scrim |
|---|---|
| dark photograph (`#303030`) | **1.05 : 1** — invisible |
| mid photograph (`#808080`) | **2.57 : 1** — fails WCAG's 4.5 |
| bright photograph (`#E0E0E0`) | 6.68 : 1 — fine |

Legibility decided by the picture. `ViewerChrome` owned only the background half of the on-media pair,
which is why every consumer reaches past it for a text colour.

**Fixed at the shared object**, on the author's call: `ViewerChrome` gained `onScrim`,
`onScrimSecondary`, `onScrimDisabled`, `onScrimBorder`, `onScrimHover` and `onScrimSelected`, light in
both themes for the same reason the scrims are dark in both. The editor reads them from its first
commit. The viewer's ~27 call sites are **not** converted here — that is a visible change to a shipping
surface and wants the author's eyes, so it is **ME-121** in the backlog.

`EditorTokens.kt` therefore declares **no colour at all**. `Theme.viewer` owns both halves of the
on-media pair, `Theme.colors` owns everything not over media (the overwrite dialog, the conversion
sheet, Settings), and this object holds only the rail, the inspector and the top bar. `onScrimHover`
and `onScrimSelected` are the two genuinely new values — 8 % and 14 % white, denser than the dock's
2 %/3 %, because the dock sits on an opaque island and these sit on 50 % black over a photograph where
2 % is invisible.

### Two things about the existing mockup, found on the way

- **`FINAL_design_mockup.html` is dark-only.** No toggle, no light palette. `LightColors` has existed
  in `Theme.kt` since the theme landed and had never been drawn; `editor.html` is the first mockup to
  render it, which is also why the contrast problem above surfaced now rather than earlier.
- **It pulls Inter from Google Fonts** via `@import`, so its type silently falls back with the network
  off — against the convention's "no CDN, opens by double-click". `editor.html` uses a local stack
  (Inter if installed, then the platform UI face) and references nothing remote. Verified by grep.

### Step 4 was already done

§4.2 already pointed at `editor.html` and named ME-012 as its mirror — it was written that way when
the feature doc was drafted, so there were no concrete values left to replace. The pointer had been
dangling at a file that did not exist; it now resolves, and gained the theme rule the mockup settles.

### Done when

- Opens by double-click, no network: **verified** — the only match for `https?://|@import|cdn|<link`
  in the file is the comment saying there are none.
- Both themes render, and the toggle is meaningful rather than decorative: the on-media layer holds
  still by design while the dialogs, sheets and page furniture change. The legend says so, so a reader
  cannot mistake it for an oversight.
- Every `EditorTokens` value appears in the mockup and vice versa: audited value by value. Three gaps
  were found and closed — `Motion.BANNER_MS` had no representation at all, `Motion.PANEL_MS` was
  declared and never applied, and the save menu used a 6 px inset where `ViewerChrome.popoverPadding`
  is 8/12.
