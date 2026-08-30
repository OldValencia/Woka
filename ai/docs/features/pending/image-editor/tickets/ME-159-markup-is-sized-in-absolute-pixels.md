# ME-159 — Markup is sized in absolute pixels, so it does not fit the picture

| | |
|---|---|
| **Order** | 70 — a defect in shipped behaviour; ahead of the remaining backlog |
| **Depends on** | ME-113 (the markup engine), ME-122 (text), ME-141/ME-150 (the size rows) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Make a markup shape's default size, and the range its slider offers, relative to the picture
being edited rather than a constant in source pixels.

**Why now.** Reported by hand 2026-08-30. Every markup default is an absolute number of **source**
pixels, and the fold applies operations in source space (§3.4), so the same default lands completely
differently depending on the file:

- on a **300 × 400** picture, a caption at the default size of 64 px is a quarter of the frame's
  height — the type is the picture;
- on an **8000 × 6000** picture, the same 64 px is under 1 % of the height — a caption nobody can read
  without zooming, and an arrow that reads as a hairline scratch.

The user meets this as "the defaults are wrong on everything except one size of photograph", and there
is no way to fix it by choosing a better constant: the two cases are two orders of magnitude apart.

## Where the constants are

`MarkupTool` declares `val TextSize = MarkupTextSizeParam`, whose `range` is also what
`MarkupTextFrame` clamps a corner drag into (`(width * ratio).coerceIn(MarkupTextSizeParam.range)`).
The stroke width is the same story — `MarkupTool`'s own doc records type's size as separate "because a
stroke's 1–64 px is nowhere near it (ME-122)". Both numbers, and both ends of both ranges, are in
source pixels.

## The question this ticket has to answer first

**What is the size relative to?** The three candidates are genuinely different and the author should
pick before any code moves:

- **The shorter edge of the source.** A caption at "6 % of the shorter edge" is the same apparent size
  on both files above. Simple, and it is what the *result* is looked at.
- **The drawn size after the fold** (`EditorEngine.drawnSize`) — which moves when a crop lands, so a
  caption placed before a crop would resize itself when the crop is applied. Almost certainly wrong,
  and worth writing down as rejected.
- **The canvas**, i.e. what is on screen. Wrong for certain: the same document would bake different
  pixels depending on the window size, and `RecipeStillEdits` folds with no canvas at all.

**Whatever is chosen, the wire format is the decision.** `MarkupShape.width` is serialised into the
recipe (ME-113) and files already carry it in absolute pixels. Either the stored number stays absolute
and only the *default* and the *range* are derived from the picture — cheap, and every existing
document keeps meaning what it meant — or the stored number becomes a fraction, which is a schema
change needing the `code-style/model.md` treatment: a new key, a default that reproduces today's
behaviour, and no reuse of the old one. **The first is almost certainly the right answer** and is what
this ticket should be written against unless something argues otherwise.

## Verification

- `jvmTest/unit/` — the derived default for a 300 × 400 source and for an 8000 × 6000 source differ by
  the ratio of their shorter edges, and both land inside the offered range;
- `jvmTest/integration/MarkupTextRenderTest.kt`, `MarkupPlateRenderTest.kt` — a caption occupies the
  same *fraction* of the frame on a small and a large fixture;
- every existing markup document decodes and renders exactly as it does today — the regression this
  ticket must not cause.

## Done when

- the default caption and the default stroke look the same size on a 300 × 400 and on an 8000 × 6000
  picture;
- the slider's range is usable on both;
- a recipe written before this ticket renders unchanged;
- the answer to "relative to what" is in §9 with its rejected alternatives;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
