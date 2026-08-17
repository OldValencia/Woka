# ME-102 — Parameter row renderers

| | |
|---|---|
| **Order** | 16 |
| **Depends on** | ME-003, ME-101 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** One row renderer per `ParamSpec` type, so a tool's inspector is drawn from its schema and no
tool ever writes panel code.

**Why now.** ME-101 built the inspector card with an empty content slot. Until this fills it, every tool
ticket from ME-105 on would have to draw its own controls — which is the failure §3.4 exists to prevent.

## Scope

**`PreviewChoiceParam` is deferred to ME-112.** It renders each option *through that option's own
`stage()`*, so it needs something to render and something to preview — and the only tool that uses it is
the filter set. A strip built now would have no filters to draw and no way to be tested. ME-112 adds the
renderer beside the presets it exists for.

**`ObjectParam` renders nothing**, by design (§3.4): its value belongs to a `CanvasTool`'s overlay. The
dispatcher skips it rather than drawing a placeholder.

So this ticket ships **six** renderers: `FloatParam`, `IntParam`, `BoolParam`, `ChoiceParam`,
`ColorParam`, `ActionParam`.

## The palette, settled 2026-08-18

- **Sliders white**, matching the zoom slider — `DetentSlider` already takes explicit colours, so this is
  a call-site value and no component change.
- **Switches green** (`#34C759`), the same "on" as every switch in Settings. A control that means "on"
  must not change colour depending on which screen it is on.
- **Buttons, checkboxes, selected segments and the markup shape colour** on the editor accent, which
  becomes **`#FF9F0A`** — the golden/orange already used for the enabled eye toggle and the active
  `Library` dock item.

**The accent cannot carry white text, and that changes three controls.** Measured: white on `#FF9F0A`
is **2.06 : 1** — a bad failure — while near-black `#1C1C1E` is **8.28 : 1**. So every place the accent
is a *ground* takes a dark label: the `Done` pill, a filled primary button, a checked box. There is no
darkened variant of the accent, because a light ground with a dark label needs none (red still does —
white on `#E5484D` is 2.9 : 1, so `--danger-solid` stays). As a *glyph* on the inspector scrim the new
accent measures **6.15 : 1** against the old purple's 3.59, so selection marks get better, not worse.
`ai/mockups/design/editor.html` is already repainted to all of this — it is the spec, and a spec
disagreeing with its ticket is worse than none.

`Theme.colors.accentEditor` is **repainted** to that value rather than call sites reaching for
`accentFolders`: "the editor's accent" deserves a named token, and using the folders one would be a lie
the next reader has to decode. The old `#BF5AF2` survives nowhere. The dock's `Editor` item shared a hue
with `Library` as a result — acceptable because ME-128 makes that item a badge dot rather than a tinted
tab, and the dock is hidden while an editor is open anyway.

## Reuse, and the two components that did not fit

`DetentSlider` fits as-is. `SegmentedControl` and `IosSwitch` do **not**: the first grounds itself in
`Theme.colors.surfacePressed` and the second's "off" track is `Theme.colors.border` — in the light theme
black at 8 % and 8 %, invisible over the inspector's dark scrim. Both therefore gain a **defaulted
colours parameter**, the shape `DetentSlider` already has, so no existing call site changes and there is
still one implementation of each control in the app.

Colour types carry **no vendor prefix** — `SwitchColors`, not `IosSwitchColors`. Renaming the
`IosSwitch` composable itself is ME-129: a rename across every call site is not the same change as
making one control readable.

## Steps

1. `core/ui/theme/Theme.kt`: `accentEditor = Color(0xFFFF9F0A)` in both `LightColors` and `DarkColors`.
2. `core/ui/components/SegmentedControl.kt`: add `data class SegmentedControlColors(track, indicator,
   selectedText, text)` and `colors: SegmentedControlColors = SegmentedControlDefaults.onSurface()`,
   plus an `onScrim()` factory reading `Theme.viewer.onScrim*`. Replace the four hardcoded colour reads
   with the parameter; change no call site.
3. `core/ui/components/IosSwitch.kt`: add `data class SwitchColors(on, off, thumb)` and
   `colors: SwitchColors = SwitchDefaults.onSurface()` with `on = Color(0xFF34C759)` kept, plus an
   `onScrim()` factory whose `off` is `Theme.viewer.onScrimBorder`. Change no call site.
4. `shell/ParamValues.kt`: `internal fun paramsOf(document: EditDocument, toolId: String, tool:
   EditorTool): ParamSet` — the operation in flight wins, then the tool's last committed op, then
   `tool.defaults()`. Pure, and the one place that precedence is written: a row reading the committed op
   while a drag is live would snap the slider back under the user's finger.
5. `shell/ParamWriter.kt`: `internal class ParamWriter(session, tool, document)` with
   `fun <T> edit(spec: ParamSpec<T>, value: T, settled: Boolean)`. It rebuilds the op from
   `paramsOf(...).with(spec, value)` and then, **reading `tool.manifest.interaction` and never a
   parameter of its own**, calls `preview` or `commit`: `Instant` commits on every edit, `Continuous`
   previews while `settled` is false and commits when it is true, `Canvas` behaves as `Continuous` for
   inspector rows because the overlay owns the canvas gesture, not these controls. This is where §3.4's
   "a slider cannot accidentally produce 200 undo steps" is enforced.
6. `shell/ParamRows.kt`: `@Composable internal fun ParamRow(spec: ParamSpec<*>, params: ParamSet,
   writer: ParamWriter)` — an **exhaustive** `when` over the sealed `ParamSpec`, dispatching to the
   renderers and drawing nothing for `ObjectParam`. Exhaustive so that adding a ninth spec type stops
   this file compiling, the same guard `EditorFixtureTest` uses.
7. `shell/ParamRowScalars.kt`: the `FloatParam` row — label, `DetentSlider` in white with the spec's
   `detent` as its only detent, double-click resetting to `detent`, and a signed readout with `unit`
   appended, signed only when the range crosses zero; the `IntParam` row — label and a stepper whose
   unit is a suffix on the number, not a separate column; the `BoolParam` row — label and the switch.
8. `shell/ParamRowChoice.kt`: the `ChoiceParam` row — `SegmentedControl` at four options or fewer and a
   wrapping chip row above that (§3.4), keyed on `Enum.name` never ordinal; the `ColorParam` row —
   swatch circles with the accent ring on the selected one plus a trailing button opening a custom
   popover; the `ActionParam` row — icon buttons committing on press.

## Files

`commonMain core/ui/theme/Theme.kt` (edit), `core/ui/components/SegmentedControl.kt` (edit),
`core/ui/components/IosSwitch.kt` (edit), `jvmMain feature/editor/shell/ParamValues.kt`,
`shell/ParamWriter.kt`, `shell/ParamRows.kt`, `shell/ParamRowScalars.kt`, `shell/ParamRowChoice.kt`
(new), `jvmTest unit/ParamValuesTest.kt`, `jvmTest unit/ParamWriterTest.kt`,
`jvmTest ui/ParamRowUiTest.kt` (new)

## Verification

`unit/ParamValuesTest.kt` — pending beats committed; committed beats defaults; a tool with no op at all
reads its defaults; a pending op belonging to a *different* tool is ignored.

`unit/ParamWriterTest.kt`, the undo-granularity contract:

- `Instant` — one `edit` produces one history entry;
- `Continuous` — fifty unsettled edits then one settled produces **one** entry, and the document holds
  the last value;
- `Continuous` — unsettled edits with no settle leave `canUndo` false and the value visible;
- a `Repeating` tool's two settled edits produce two ops; a `Single` tool's two produce one.

`ui/ParamRowUiTest.kt`, over `FixtureTool` (which declares one spec of every type) — each of the six
renderers appears for its spec, `ObjectParam` draws no row, the switch reflects and toggles its value,
the segmented control shows four options and a chip row at five, and a slider drag followed by release
leaves exactly one undo step.

## Done when

- the six renderers exist and `ParamRow`'s `when` is exhaustive with no `else`;
- no call site of `SegmentedControl` or `IosSwitch` outside the editor changed — `git diff` proves it;
- no new type name contains `Ios`, `Apple` or `Mac`;
- `#BF5AF2` appears nowhere in `shared/` — grep proves it (the mockup is already clear of it);
- no accent-filled control carries a white or light label — the `Done` pill, `.btn.primary` and the
  checked box all take `#1C1C1E`;
- `./gradlew.bat :shared:build` green, with the tests above passing.

## Result — 2026-08-18

Six renderers plus the writer and the value reader. `:shared:build` green, **588 tests, 0 failures**
(575 before, +13). Largest new file 106 lines.

| Done-when | |
|---|---|
| `ParamRow`'s `when` is exhaustive, no `else` | ✅ — `PreviewChoiceParam` and `ObjectParam` are explicit `Unit` branches, so a ninth spec type still breaks the build |
| no call site of `SegmentedControl`/`IosSwitch` outside the editor changed | ✅ — the diff touches neither `feature/viewer`, `feature/settings` nor `app/` |
| no new type name contains `Ios`, `Apple` or `Mac` | ✅ — `SwitchColors`, `SegmentedControlColors` |
| `#BF5AF2` anywhere in `shared/` | ✅ gone; `accentEditor` is `#FF9F0A` in both palettes |
| anti-FQN | ✅ across all nine files |

### Three things found while writing it

- **`onDragEnd` cannot re-send the value.** The first version committed `params[spec]` on release —
  the value captured at *composition*, not the one just dragged, so a released slider would commit what
  it held two frames earlier. `ParamWriter.settle()` exists for exactly this: the drag's last frame has
  already previewed what it wants, and release only needs to fold it in.
- **`Color(argb.toULong() shl 32)` was nonsense.** `ColorParam` stores ARGB as a `Long` in the same
  `0xAARRGGBB` form Compose's own `Color(Long)` takes, so the shift produced a colour from another
  planet. Caught by reading it back, not by the compiler — both spellings type-check.
- **A vacuous test nearly shipped.** `a stepper press moves the value…` originally asserted
  `assertEquals(1, FixtureParams.Count.default)` — a fact about the fixture, not about the stepper,
  which would have passed with the stepper deleted. The stepper's buttons had no test tags, which is
  *why* it was written that way. Tags added, and the test now presses `+` once, presses `−` three times
  from 2, and asserts the value floors at 0 and that the button stops being pressable there rather than
  accepting presses that clamp to the same number.

### Two splits, both at 150 lines and both along a real seam

- `ParamRowFrame` out of `ParamRowScalars`: the label column, the readout column and their widths are
  shared furniture. Each renderer laying out its own label is how six rows end up at five widths.
- `ParamRowColorAction` out of `ParamRowChoice` at 164 lines: an enum picker *chooses among named
  options*; a swatch row and a button *set a value directly*. Different question, different file.

### Carried forward

`IntStepper` takes a `unit` parameter that nothing passes yet — `FixtureParams.Count` declares none.
ME-110's resize rows are the first real caller, and the rendering is specified and built so that ticket
does not have to invent it.
