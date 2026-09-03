# ME-147 — A chip row that lights more than one

| | |
|---|---|
| **Order** | 50 — after ME-141; the shell half of the backlog's ME-147 |
| **Depends on** | ME-102 (the row renderers), ME-144's chip as it landed in `ParamRowChoice` on 2026-08-27 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Add a tenth `ParamSpec` — `ToggleSetParam`, one of a fixed set of which **any number** may be
on — and render it with the same chip `ChoiceParam` already draws, extracted so the two cannot drift.

**Why now.** Bold, italic, underline and strikethrough are four *independent* booleans and the schema
has no way to say that: `ChoiceParam` picks **one** of a set, `BoolParam` is one full-width `IosSwitch`
row, and four of those under `Shape`, `Color`, `Fill` and `Text size` is a wall (settled 2026-08-27,
decision log). Nothing can be built for the faces until the schema can express them.

## What this ticket is not

The backlog block ME-147 covered the schema **and** markup's four faces. Only the schema half is here.
**ME-151** is the other half — `TextFace`, the `faces` field on `MarkupShape.Text`, the pen row, the
four SVGs and the caret — and it assumes everything below exists. Together they are ~20 files, which is
§4's split, not a preference.

This ticket touches no tool and no markup file. It is verified against `FixtureTool`, which is what that
fixture is for.

## The two decisions this settles

**The wire format is the constants' names, joined — no new `ParamValue` subtype.** `TextValue`, exactly
as `ChoiceParam` already uses it, joined on `,` in **`options` order** rather than in the set's iteration
order, so the same set always encodes to the same string. A name that no longer resolves is **dropped**
and the rest of the set survives — the same "retiring an option" rule `ChoiceParam` follows, applied per
element. The empty set is `""`, which is a legal value and not an absent one.

**The chip is extracted, not copied.** `ChipRow` inside `ParamRowChoice.kt` carries the
one-scale-for-the-whole-row glyph sizing that 2026-08-27 was spent measuring — SF Symbols export each
glyph cropped to its own canvas, so fitting each into the same square scaled markup's arrow 1.6× thicker
than its rectangle. A second renderer drawing its own chips is that measurement quietly diverging. The
extraction is also what pays `ParamRowChoice.kt`'s size mandate: it is at **149 lines**.

## Steps

1. **`core/ParamSpecToggleSet.kt` (new)** — its own file beside `ParamSpecChoice.kt`,
   `ParamSpecPad.kt` and `ParamSpecPreview.kt`, which is the shape this schema already has;
   `ParamSpecChoice.kt` is at 114 lines and this is ~40 with its doc.

   ```kotlin
   data class ToggleSetParam<E : Enum<E>>(
       override val key: String,
       val label: String,
       val options: List<E>,
       override val default: Set<E>,
       val labelOf: (E) -> String = { it.name },
       val iconOf: (E) -> DrawableResource? = { null }
   ) : ParamSpec<Set<E>>
   ```

   `encode` is `TextValue(options.filter { it in value }.joinToString(",") { it.name })`; `decode`
   splits on `,`, maps each name through `options.firstOrNull { it.name == name }` and drops what does
   not resolve, returning the set — never null for a `TextValue`, because "no names I know" is the
   **empty set**, which is a meaning, not a failure. Null stays for a genuine type mismatch, which is
   `ParamSpec`'s contract.

2. **`shell/ParamRowChip.kt` (new)** — move `ChipRow`, `CHIP_SIZE` and `CHIP_GLYPH` out of
   `ParamRowChoice.kt` **verbatim** in behaviour, generalised over its data only:

   ```kotlin
   internal data class ParamChip(
       val tag: String,
       val label: String,
       val icon: DrawableResource?,
       val selected: Boolean,
       val onClick: () -> Unit
   )

   @Composable
   internal fun ParamChipRow(chips: List<ParamChip>)
   ```

   The glyph scale is still taken once for the whole row, from the widest intrinsic size in it, and a
   chip with no glyph is still drawn as its word — including when *some* of the row has one, which is
   what the existing `glyph == null || unit == null` branch already says. `ViewerTooltip` still carries
   the label. `tag` is the whole test tag, so the two callers spell their own.

3. **`shell/ParamRowChoice.kt` (edit)** — `ChipRow` becomes
   `ParamChipRow(spec.options.map { ParamChip("param-${spec.key}-${it.name}", spec.labelOf(it), spec.iconOf(it), it == selected) { writer.edit(spec, it) } })`.
   Nothing else in the file changes; it drops to ~75 lines.

4. **`shell/ParamRowToggleSet.kt` (new)** — `ToggleSetParamRow`, a `ParamRowFrame(spec.label)` around
   `ParamChipRow`, one chip per option, `selected = option in params[spec]`, and a click writing
   `params[spec] - option` or `+ option`. **Always `settled = true`**: a chip is a press, not a drag, so
   it is one undo step each — the same as a segment and for the same reason (`ParamWriter`).

5. **`shell/ParamRows.kt` (edit)** — one branch in the deliberately exhaustive `when`:
   `is ToggleSetParam<*> -> ToggleSetParamRow(spec, params, writer)`. The file does not compile until it
   is there, which is the guard that sentence exists for. `groupRuns` is **untouched**: a toggle set is a
   labelled row and rows stack, so it starts its own run like every scalar.

6. **`jvmTest/support/FixtureParams.kt` (edit)** — `val Toggles = ToggleSetParam("toggles", "Toggles",
   FixtureChoice.entries.toList(), default = emptySet())`, added to `all`. Empty by default so
   `defaults()` writing it is distinguishable from it being absent — `isSet` must still be true, which
   is what `EditorFixtureTest` already asserts for every spec.

7. **`jvmTest/unit/EditorFixtureTest.kt` (edit)** — `"toggleSet"` into `EVERY_SPEC_KIND`, a branch in
   `kindOf`, and a round trip through `with(FixtureParams.Toggles, setOf(First, Third))`.

## Files

`core/ParamSpecToggleSet.kt` (new), `shell/ParamRowChip.kt` (new), `shell/ParamRowToggleSet.kt` (new),
`shell/ParamRowChoice.kt` (edit), `shell/ParamRows.kt` (edit)

Under `shared/src/commonMain/kotlin/com/woka/feature/editor/` and
`shared/src/jvmMain/kotlin/com/woka/feature/editor/` respectively.

Tests: `jvmTest/support/FixtureParams.kt` (edit), `jvmTest/unit/EditorFixtureTest.kt` (edit),
`jvmTest/unit/ParamToggleSetTest.kt` (new), `jvmTest/ui/ToggleSetRowUiTest.kt` (new).

## Verification

- `jvmTest/unit/ParamToggleSetTest.kt` — a set of two encodes to a `TextValue` of two names **in
  `options` order** whichever order the set was built in; it decodes back to the same set; a string
  carrying one unknown name decodes to the known ones alone; `""` decodes to the empty set; a
  `FloatValue` decodes to null;
- `jvmTest/unit/EditorFixtureTest.kt` — the tenth kind is covered and round-trips (step 7);
- `jvmTest/ui/ToggleSetRowUiTest.kt`, against `FixtureTool` under `onEditor` — the row draws one chip
  per option; clicking two of them leaves **both** in the document's value, which is the whole claim
  this spec makes and the one `ChoiceParam` cannot satisfy; clicking a lit chip again clears that one
  and leaves the other; each click is one `session.canUndo` step.

## Done when

- `ToggleSetParam` is the tenth `ParamSpec` and `ParamRows`' `when` has a branch for it;
- `ParamRowChoice.kt` and `ParamRowToggleSet.kt` both draw their chips through `ParamChipRow`, and
  `ParamRowChoice.kt` is under 150 lines (`wc -l`);
- markup's five shape chips look and behave exactly as they did before the extraction —
  `ParamChipRowUiTest` passes unchanged;
- two options can be on at once and survive a document round trip;
- `./gradlew.bat :shared:build` green.
