# ME-268 — An area for Adjust and Styles: rectangle or ellipse

> **Dropped 2026-11-10, id retained.** Built in full that day — an SkSL mask stage, the Area /
> Feather rows, a canvas box on Crop's handles, render tests green — and reverted unshipped by the
> author against the KISS brief (`video-editor.md` §6). Kept as the record of what it would take.

| | |
|---|---|
| **Order** | after ME-215 — an area's *time* is its clip |
| **Depends on** | ME-215; ME-205's shader chain |
| **Feature** | [Picture](../picture.md) · umbrella [Video Editor](../../video-editor.md) §2 |

**Goal.** Apply Adjust or a Style to part of the frame — a rectangle or an ellipse with a soft
edge — instead of the whole picture.

**Why now.** The author, 2026-11-02: where on the picture a look applies; the shape chosen the
same day — **rectangle or ellipse, with feather**, fixed in place, timed by the clip.
**This reverses the 2026-09-03 decision that put masks out of scope**, for these two shapes only;
brush masks stay out (§2, backlog §900).

## Shape
- A shared param group on Adjust and Filters: `Area` (`ChoiceParam`: Whole frame / Rectangle /
  Ellipse), `areaRect` (`ObjectParam`, source pixels, as Crop's rect is), `Feather` (`FloatParam`,
  px). Whole frame is the default and folds exactly as today.
- The area is dragged on the canvas by an overlay that reuses Crop's handles; an ellipse is drawn
  inside the same box.
- Fold: the tool's colour work becomes one `RasterStage.Shader` that blends graded and ungraded by
  a mask computed in SkSL from the rect or ellipse and the feather — so the editor, the viewer and
  the render, all through `drawThroughRecipe` / `renderAtNativeScale`, share it. Works on stills.

## Steps
1. Params and defaults; `stage()` emits the masked shader only when Area is not Whole frame.
2. SkSL mask shader and its packing; a unit test on a 64×64 fixture — inside graded, outside
   untouched, the feathered edge in between.
3. The canvas overlay, shared with Crop's handles.
4. An integration render proving the viewer and the render need nothing beyond the shader.

## Done when
- An ellipse with Brightness +50 brightens inside only, softly, in the editor, the viewer and a
  render.
- `./gradlew.bat :shared:build` green.
