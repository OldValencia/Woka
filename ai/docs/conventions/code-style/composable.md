# Composables

Everything under `core/ui/`, `app/shell/`, and any `feature/*/` file that draws.

## Design law

**Apple HIG, never Material as a look.** 1.dp borders, 8–12.dp radii, `Theme.colors.*`, `#F2F2F7` and
its dark twin for surfaces, animation instead of elevation. No `MaterialTheme`, no Material colour
roles, no shadow used as decoration. `ai/mockups/design/FINAL_design_mockup.html` is the visual truth;
a feature with its own surface has its own mockup beside it.

**No Material imports in new code.** Text is `BasicText` with an explicit `TextStyle`
(`DropdownItem.kt:68`); an icon is `Image(painterResource(res), colorFilter = ColorFilter.tint(…))`
(`DockItem.kt:73`). 23 files already read this way. The 37 that still import `material3` — for
`Text`, `Icon`, `CircularProgressIndicator` and `HorizontalDivider` — are converted by ME-014, which
is approved and lands before the editor shell; after it, this paragraph goes away and the rule is
simply "there is no Material".

## Signature

```kotlin
@Composable
fun SizeIndicatorButton(
    percent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

- `modifier: Modifier = Modifier` is the first optional parameter, always, and is applied to the
  outermost node of the composable — never swallowed, never applied twice.
- **State is hoisted.** A control takes `value` + `onValueChange`. A `remember` the caller cannot see
  is allowed only for animation and for pointer bookkeeping.
- Past ~5 parameters, collapse into an `@Immutable data class` — `ViewerContext`, `ViewerActions`,
  `ViewerEvents` are the precedent. The type is the documentation and the diff stays readable when a
  sixth thing is needed.
- Slots (`content: @Composable () -> Unit`) go last.
- No suspend, no `runBlocking`, no file access. A composable reads state and emits UI; work belongs in
  the state holder (`state.md`) or an engine (`engine.md`).

## Tokens

Every dp/ms/alpha describing chrome lives in that surface's token file — `core/ui/theme/ViewerChrome.kt`,
`feature/editor/shell/EditorTokens.kt` — with a comment naming the mockup element it comes from.
A dp literal at a call site is allowed only when it is local geometry with no counterpart anywhere
else (a 2.dp gap inside one row). The same number appearing twice is a token that has not been
extracted yet.

## Structure

- One surface per file, 130–150 lines. The split seams that work here: `*Parts.kt` for the row
  builders, `*Controls.kt` for the interactive cluster, `*Overlay.kt` for the layer above the media —
  `SubtitleCustomizePanel` / `SubtitleCustomizeControls` / `SubtitleCustomizeParts` is the precedent.
- Reuse before addition: `core/ui/components/` already has `Marquee`, `IosSwitch`, `SegmentedControl`,
  `DetentSlider`, `DropdownItem`, `RenameDialog`, `FrameExportBanner`, the folder cards and the grid
  scrollbar. A new slider, switch or dialog must first say why an existing one does not fit.
- Recomposition: read a `StateFlow` with `collectAsState()` at the **lowest** node that needs it. A
  screen that collects everything at the top and passes it down re-lays out the whole tree on every
  tick.
- `derivedStateOf` for values recomputed from frequently-changing state; `remember(key)` with the real
  key, never `remember {}` around something that depends on a parameter.

## Interaction

- Pointer handling goes through `pointerInput` with a named `key`, and consumes what it handles —
  an unconsumed drag reaching two layers is the PiP bug that shipped once already.
- Hover affordances use `pointerHoverIcon`; every clickable that is not obviously a button gets one.
- Keyboard goes through the existing shortcut plumbing (`LocalGlobalShortcuts.onPreviewKey`), scoped
  and restored on dispose. Never a raw `onKeyEvent` on a random node.

## Platform

A composable that needs AWT, Skia or a file lives in `jvmMain`. If only a leaf needs it, put the leaf
behind `expect`/`actual` and keep the layout in `commonMain` — see `engine.md`.
