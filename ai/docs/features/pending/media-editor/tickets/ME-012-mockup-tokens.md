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

## Review pass — 2026-08-18, in Chrome, both themes

The first version was never opened in a browser (the extension was not connected), and it showed. Two
CSS bugs, one layout error, three contrast failures and a specificity trap. All fixed; recorded because
every one of them is a mistake that repeats.

### Two bugs of the same family: a `<span>` given a size

- **`BoolParam` drew only its knob.** `.ios` is a `span`, so it was an **inline** box — width and
  height are simply ignored on one. The track vanished and the absolutely-positioned knob was all that
  remained. `display: inline-block` fixes it.
- **Button labels sat on the top edge** (§5, §8). `.btn` is also a `span`, blockified by its flex
  parent so the height *did* apply — but with no vertical centring the text lays out on the first line
  box, against the top. `inline-flex` + `align-items: center`.

The general rule, since it will recur: **a height on a `span` means nothing without a `display` that
accepts one, and means nothing useful without centring.** Every other sized element in the file was
already a flex or grid child and so escaped by accident, not by design.

### The specificity trap — the one worth remembering

The checked checkbox showed a purple square with no tick. Not a colour choice: `.sheet .askagain span`
(0,2,1) outranks `.sheet .box` (0,2,0) **and comes later**, and the box *is* a span inside `.askagain`
— so the tick was being painted in the label's colour, `--text-2` `#6E6E76`, on purple. It had looked
passable only while the fill was the lighter `#BF5AF2`; darkening the fill for contrast is what exposed
it. Scoped to `.askagain > span:not(.box)`.

### The layout error

The inspector covered the bottom third of the photograph. That is not a drawing slip — it is the layout
being wrong about what `Fit` means: **the canvas fits the space left over after the chrome, not the
whole frame.** `.canvas` now reserves the stack's height. Also `.picture` had both `height` and
`max-width` set, so whichever cap bound first broke the aspect ratio; width drives it now, with
`max-height` as the guard.

### Three contrast failures, and a new token because of them

`accentEditor` `#BF5AF2` and `danger` `#E5484D` are **content** colours. Used as a ground under white
text they give **3.5 : 1** and **2.9 : 1** — both under WCAG's 4.5, and `Done` at 13.5 px bold does not
qualify for the 3 : 1 large-text allowance. In the light theme a white tick on `#BF5AF2` sitting on a
white sheet disappeared outright.

So the mockup now carries `--editor-solid` `#8B2FC9` and `--danger-solid` `#C0272C`: the same hues taken
down until a white label passes. `accentEditor` keeps its real job — the selected tool's glyph, the
slider fill, the markup outline, the switch track. **`EditorTokens.kt` does not need changing**, since it
declares no colour; if the shell wants these they belong in `Theme.colors` as a pair beside the accents,
and ME-101 is where that call gets made.

Also fixed: `Cancel` was outlined in `--border` (4 % white), invisible on an elevated sheet, so it read
as plain text rather than a button — now `--border-glow`. The unchecked checkbox was outlined at 8 %,
which is not a control anyone can find — now `--text-3` at 1.5 px.

### A real design contradiction, found by trying to draw it

Step 1 asks for a **disabled** rail item. §3.4 and ME-115 say a tool switched off in Settings *leaves*
the rail, the shortcut table and the fold — so by that reading a disabled rail item cannot exist and
there is nothing to draw.

Resolved by giving the state the meaning it actually has: **this tool cannot apply to this file** — an
HDR output with no encoder for the format — dimmed, struck through, with a tooltip saying why. Settings
removal and per-file unavailability are different states with different treatments, and the mockup and
its note now say so. Previously the note claimed the struck item was the Settings case, which
contradicted §3.4.

### Smaller things

- The selected rail tool carried **three** signals — fill, accent glyph and a dot — and the dot
  collided with a 16 px icon in a 40 px button. Dropped to two, matching the dock's own `active-*`
  idiom.
- `IntParam` put `px` in the readout column while `FloatParam` put `+36%` there, so one column meant
  two things. The unit is now a suffix on the number and the column always means "the value".
- The HDR menu overrode `popoverWidth` to 230 px with an inline style — in the file that specifies the
  token. Removed.
- The `Cool` filter preview was `hue-rotate(30deg)` on an orange source, which comes out **yellow**,
  contradicting its own label.
- Seven colour literals were sitting in rules (`#0b0d11`, `#14171d`, `#30D158`, `#FF5C5C`, `#FFC062`,
  `#FF8A8A`, `rgba(255,159,10,…)`) in a file whose whole claim is that colours come from tokens. Named
  in the token block — `--warn`, `--ok`, `--shot-bg`, `--shot-plate` — or mapped to existing ones.
- The banner spinner was a static ring and read as a broken glyph; it spins, and `--banner` finally has
  a use marking which banner self-dismisses.
- Rail glyphs are text stand-ins for `Icons.*`. The note now says so, so nobody implements the arrows.

### How it was checked

`file://` is refused by the browser extension, so the directory was served on `127.0.0.1:8765` and the
page driven in Chrome — both themes, all eight sections, zoomed on the switch and the checkbox. Tag
balance verified mechanically after the edits (138/138 `div`, 49/49 `span`, 4/4 `figure`), and the CSS
variable audit re-run: no undefined, no unused but `--banner`, no dark-only declaration.
