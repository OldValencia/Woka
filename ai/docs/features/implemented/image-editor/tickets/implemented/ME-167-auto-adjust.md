# ME-167 — Auto adjust

| | |
|---|---|
| **Order** | after ME-166 and ME-162 |
| **Depends on** | ME-166, ME-162 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** One button in the Adjust tool that reads the picture and writes the twelve sliders.

**Why now.** Lifted out of ME-111 on 2026-08-22 because it could not be a tool as tools are defined.
ME-166 built the hook, so the objection is gone. Ordering it after ME-162 means Auto can set
Highlights and Shadows too, which is most of what makes an automatic correction look automatic.

## Steps
1. Add `ActionParam("auto", "Auto", icon = …)` to `AdjustTool.params`, first in the list.
   `ActionParam` renders as an icon button and is always `Instant` (§3.4) — no new row renderer.
2. Override `resolve` in `AdjustTool`: when `spec` is `Auto` and `stats` is non-null, return a
   `ParamSet` with the computed values; when `stats` is null, return null and the button does nothing.
3. The correction, in this order, each clamped to the existing `SPAN`:
   - **Black point** from the 0.1 percentile of `stats.luma`, **white point** from the 99.9 —
     percentiles, not min and max, so one hot pixel cannot decide the exposure of a photograph;
   - **Exposure** from the gap between `stats.meanLuma` and mid-grey, at half strength;
   - **Shadows** and **Highlights** from how much of the histogram is clipped at each end;
   - **Contrast** left alone. The level stretch above has already added contrast, and adding it twice
     is what makes an auto button look cheap.
   - Saturation, Warmth, Tint, Sharpness and Noise Reduction are **not touched**. Colour balance from
     a luma histogram is a guess, and sharpening a picture nobody asked to sharpen is a change the
     user did not make.
4. Auto is one commit in history: it writes every value in a single `ParamSet`, so one `Undo` takes
   all of them back. `ToolCardinality.Single` on Adjust already gives this; confirm rather than add.
5. Auto is not idempotent-by-accident: pressing it twice must give the same result as pressing it
   once, because it computes from `stats` (the unedited source) and not from the current params.
   State this in the KDoc — it is the property that will be broken by a later edit if nobody wrote it
   down.
6. Delete the "Auto is deliberately absent" paragraph from `AdjustTool.kt`'s KDoc and replace it with
   what Auto does and does not touch, and why.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustTool.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustAuto.kt` (new — the pure function, so it is testable without a tool)

## Done when
- `jvmTest/unit/` asserts: a flat mid-grey fixture yields all-zero params (Auto does nothing to a
  picture that needs nothing); a black-clipped fixture yields a positive black-point correction;
  applying Auto twice yields an identical `ParamSet`;
- one `Undo` after Auto returns every slider to where it was;
- confirmed by hand on a real photograph — the numbers are a judgement and the test only checks they
  are consistent;
- `./gradlew.bat :shared:build` green.
