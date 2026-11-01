# Phosphor icons — replacing SF Symbols

**Scope.** Every icon the app draws comes from Phosphor 2.1.1 (`@phosphor-icons/core`, MIT) instead
of Apple's SF Symbols. The logo and the note glyph drawn for AP-003 stay. Nothing about where an
icon is used, how large it is drawn or how it is tinted changes. The surfaces stay as they are, so
there is no new UI.

**Why.** SF Symbols are licensed for interfaces of apps that run on Apple platforms, and for mockups
of them. Woka is a Windows app, and its repository carries the SVG files. The author asked on
2026-11-01; Phosphor was chosen over Fluent UI System Icons ("I don't like Microsoft's style") and
over Lucide (outline only, while the app pairs outline and fill).

Spec: [`ai/mockups/design/phosphor-icons.html`](../../../mockups/design/phosphor-icons.html).

---

## 1. Inventory (measured 2026-11-01)

| | Count | What happens |
|---|---|---|
| SVGs in `composeResources/drawable/` | 121 | |
| Not referenced by any `Res.drawable.*` outside generated code | 20 | deleted |
| Ours: `woka_logo`, `music_note` | 2 | kept |
| From SF Symbols (117 written by Apple's CoreSVG, plus `pin` and `speaker_bolt_fill` built from SF parts) | 99 | replaced |
| …with one obvious Phosphor glyph | 56 | direct |
| …with several fair candidates, or none | 43 | the author picks on the mockup |

Every drawable reaches the code through two objects: `Icons.Static` and `EditorIcons`. One file
backs two constants today: `rectangle_arrowtriangle_2_inward` is both `Crop` and `ScaleFit`.

## 2. Import rules

1. **File names do not change.** Each Phosphor glyph is written over the SF file of the same name,
   so neither icon object nor any call site changes. The one exception is `Crop`, which gets its
   own `crop.svg` (Phosphor `crop`), because its shared file now becomes Fit's glyph.
2. **One weight for the whole app**, picked on the mockup. Mixing weights per icon was rejected: in
   a toolbar a 1 px outline beside a 1.5 px one looks like a rendering bug.
   **Fill** is not a weight. A glyph that marks an *on* state takes Phosphor's `fill` variant:
   play, pause, the speaker levels, the filled bells, the clear button. This matches today's
   `_fill` files.
3. **The viewBox is cropped to `12 12 232 232`** (was `16 16 224 224`, see the log). Phosphor draws inside a padded 256 grid, and SF
   Symbols are cropped tight. Uncropped, the same `Modifier.size` would draw every glyph about 12 %
   smaller. Cropping per glyph to its own bounds was rejected: a minus would then fill the box the
   way a gear does.
4. **`fill="currentColor"` becomes `fill="black"`.** Every icon is tinted with `ColorFilter.tint`
   at the call site, as the SF files (`fill="white"` at 0.85) are today.
5. **Hand-drawn glyphs**, only where the author picks one:
   - ±5 s: Phosphor's arrow with a 5 inside;
   - Sound Boost: speaker and lightning bolt side by side, as today;
   - the bell with a badge dot;
   - the bottom half-circle, which is `circle-half` turned a quarter;
   - the portrait rectangle, which is Phosphor's rectangle turned upright.
   A turn is baked into the path, not left to a `transform` attribute.
6. **Phosphor's MIT notice ships with the app.** The licence asks for it in every copy.

## 3. Status

| Item | What | State |
|---|---|---|
| mockup | [`phosphor-icons.html`](../../../mockups/design/phosphor-icons.html) — all 99 beside their candidates, weight, size and crop toggles, the controls bar in place | **accepted** 2026-11-01 — picks in [PI-001](tickets/PI-001-every-icon-from-phosphor.md) |
| [PI-001](tickets/PI-001-every-icon-from-phosphor.md) | Replace every SF Symbol, delete the unused, add the licence | **built** 2026-11-01, `:shared:build` green — open: the by-hand walk |

## 4. Open items

- None but the by-hand walk. The mockup stays as the record of the choice; it is not regenerated,
  because the SF files it drew from are gone.

## 5. Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-11-01 | Keep SF Symbols | no — replace them | licensed for Apple-platform interfaces only; Woka is a Windows app and its repository carries the files |
| 2026-11-01 | Which set | Phosphor 2.1.1, MIT | the author: no Microsoft style; closest to SF Symbols in feel, six weights including fill, 1512 glyphs; Lucide has no fill |
| 2026-11-01 | Renaming files to Phosphor's names | no — same names, new content | the icon objects and every call site stay untouched; the name is ours, the glyph is theirs |
| 2026-11-01 | Weight per icon | one for the app, fill for on-states | mixed outline weights read as a rendering fault at 13–16 dp |
| 2026-11-01 | Phosphor's padding | crop to `16 16 224 224` for every glyph | keeps today's dp sizes; a per-glyph crop would make every glyph fill its box whatever its shape |
| 2026-11-01 | Unused drawables | delete the 20 | nothing references them; replacing them would spend work on nothing |
| 2026-11-01 | The picks | Regular; a letter per row as listed in PI-001 | the author, from the mockup |
| 2026-11-01 | Two or three speaker-wave levels | two — 2 and 3 waves share `speaker-high` | the author: keep it |
| 2026-11-01 | 21 `Icons` constants no call site reads (all bells, the export states, `Share`, `Tag`, `NoSign`, `SidebarLeft`, `ZoomOut`, `DocumentOnDocument`…) | deleted with their files, not redrawn | found while answering the export question: nothing draws them; the same rule as the 20 unreferenced files |
| 2026-11-01 | The bottom half-circle | `circle-half` turned so its hatching is at the bottom | the mockup turned it the wrong way (hatching on top); `circle.bottomhalf.filled` means the bottom |
| 2026-11-01 | Where the MIT notice lives | `desktopApp/appResources/common/licenses/phosphor-icons.txt` | `appResources/common` is already packaged for every OS; a repository-root folder would need its own Gradle copy step |
| 2026-11-01 | One crop for every glyph | `12 12 232 232`, and a glyph near its edge gets a square box of its own, centred on its bounds | the author saw the gear and the camera cut off: at 16..240 the box cut through the stroke's anti-aliasing |
| 2026-11-01 | Text pen | `text-t` | the author asked for `text-aa`; that is the faces button beside it (ME-155), and the author chose `T` once reminded |
