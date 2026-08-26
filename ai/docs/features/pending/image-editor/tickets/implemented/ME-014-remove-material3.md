# ME-014 — Remove the `material3` dependency

| | |
|---|---|
| **Order** | 14 — approved; **must land before ME-101** |
| **Depends on** | ME-001 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Delete a pre-release dependency held for four symbols, and make 37 files look like the 23
that already do it right.

**Why now.** Before the editor shell. It adds ~15 new UI files; converting them later doubles the work,
and writing them against Material contradicts the mandate.

**The measurement (2026-08-16).** `material3` is imported by 37 files — 2 in `commonMain`, 35 in
`jvmMain`, all in the viewer — for exactly four symbols: `Text` (28), `Icon` (12),
`CircularProgressIndicator` (2), `HorizontalDivider` (1). Meanwhile 23 files already use `BasicText`
with an explicit `TextStyle`, and `Image(painterResource(...), colorFilter = tint)` is the established
icon idiom. Material is the exception here, not the convention — and `material3.Text` silently
inherits Material's `LocalTextStyle`, which is why viewer typography and shell typography do not quite
match today.

## Steps

1. `Text` → `BasicText` with an explicit `TextStyle`, copying the idiom in `DropdownItem.kt:68`. Where
   a call passed no style, take size/weight/colour from the surface it sits on rather than inventing
   one — this is the pass where those defaults become visible.
2. `Icon` → `Image(painter = painterResource(res), contentDescription = …, colorFilter =
   ColorFilter.tint(colour))`, as `DockItem.kt:73` already does.
3. `CircularProgressIndicator` (2 files) → `WokaSpinner` in `core/ui/components/`: a HIG spoke wheel
   drawn with `Canvas` + `rememberInfiniteTransition`, sized and coloured by parameters. The only new
   component in this ticket.
4. `HorizontalDivider` (1 file) → a 1.dp `Box` with `Theme.colors.border`, matching every other
   divider in the app.
5. Remove `material3` from `libs.versions.toml` and `shared/build.gradle.kts`. Fix
   `jvmTest/ComposeHarnessTest.kt` the same way. Then grep the whole tree for `material3` and expect
   zero hits.
6. Watch the line budget: several viewer files are near 150 lines and an explicit `TextStyle` is longer
   than a bare `Text`. A file pushed over gets split in this ticket, named for its role.

## Files

37 `.kt` files across `core/ui/`, `feature/viewer/**` and `jvmTest`; plus
`core/ui/components/WokaSpinner.kt` (new), `gradle/libs.versions.toml`, `shared/build.gradle.kts`

## Done when

- no `material3` string anywhere in `shared/`;
- `./gradlew.bat :shared:build` green;
- the app launched and the viewer walked by hand — text, icons, the loading spinner and the subtitle
  popover divider all unchanged to the eye.

## Result — 2026-08-18

`material3` is gone from `gradle/libs.versions.toml` (the `[versions]` pin **and** the library entry)
and from `shared/build.gradle.kts`. 40 files changed, +243 −213. `:shared:build` green, **563 tests, 0
failures, 0 errors**. Only remaining occurrence of the string in the repo is the sentence in
`WokaSpinner`'s KDoc that says what it replaced.

### The measurement held exactly

Re-counted before touching anything: **37 production files** (2 `commonMain`, 35 `jvmMain`) plus
`jvmTest/ComposeHarnessTest.kt`, for `Text` (28 files), `Icon` (12), `CircularProgressIndicator` (2),
`HorizontalDivider` (1). Identical to 2026-08-16 — no drift, nothing new crept in.

### What the pass actually exposed

- **`MaterialTheme` is never provided anywhere in the app.** The only mentions in the tree are three
  comments in `ViewerChrome`. So `material3.Text` has been resolving `LocalTextStyle` to
  `TextStyle.Default` and, where no colour was passed, `LocalContentColor` to **`Color.Black`**. That is
  the precise cause of "viewer typography and shell typography do not quite match": not a different
  scale, an *absent* one.
- **The loading spinner was Material's default purple `#6650A4`.** `CircularProgressIndicator` reads
  `MaterialTheme.colorScheme.primary`, and `LocalColorScheme` carries a default even with no
  `MaterialTheme` in the composition — so the viewer has been showing a colour that appears nowhere in
  Woka and was never chosen. **"Unchanged to the eye" is therefore the wrong bar for this one control**,
  and the Done-when should be read as "changed on purpose, and only here". `MediaViewerPlayer` now takes
  `Theme.viewer.onScrim` (it draws on the media backdrop); `VideoReloadOverlay` already specified white
  and keeps it.
- **Step 1's worry did not materialise.** Only **4** `Text` calls passed neither `style` nor `color`,
  and all four are in `ComposeHarnessTest.kt`. Every production call site was already explicit, so no
  default had to be invented anywhere.
- **Step 6's split was never needed.** The two files sitting at exactly 150 lines came out at 150
  (`MediaViewerTimeSlider`) and 149 (`PlayerSettingsSync`) — folding `fontSize`/`fontWeight` into an
  existing `TextStyle(...)` removes about as many lines as it adds. No file exceeded 150.
- **All 18 `Icon` call sites were identical in shape** — `painter` / `contentDescription` / `tint` /
  `modifier`, every one of them — so the mapping to `Image(..., colorFilter = ColorFilter.tint(…))` was
  a true 1:1. Worth noting for anyone auditing it: `material3.Icon` applies a default 24.dp only when
  the painter has no intrinsic size, and every site here passed an explicit `Modifier.size(...)`, so no
  geometry moved.

### Two ways the scripted pass went wrong, recorded because both look like success

The bulk was done by a transformer with a real paren-matching argument scanner, then reviewed as a
diff. It failed twice, and neither failure was caught by the script:

1. **A comment containing a `"` inside an argument list.** `MediaSliderTooltip` had
   `// The pill exists to answer "where does this land?", so the time` between two arguments. The
   string-skipper treated the first quote as opening a literal, so the top-level comma split landed
   mid-comment and produced `so the time` as a bare statement. The compiler caught it —
   `Unresolved reference 'so'` — but only because the wreckage happened to be invalid Kotlin.
2. **Running the folder after a hand-edit double-applied it.** `MediaViewerTimeSlider` ended up with
   two `style =` arguments. Also compiler-caught.

The lesson is not "do not script it" — 49 call sites by hand is how one gets missed. It is that **a
mechanical pass over Kotlin needs the compiler as its check, not the diff**, and that comments are part
of the syntax it has to survive.

### Done when

- No `material3` in `shared/` — verified by grep across `.kt`, `.kts` and `.toml`.
- `:shared:build` green — verified, with the test count unchanged at 563.
- **The hand-walk is done** — walked by the author on 2026-08-18: text, icons, the loading spinner and
  the subtitle popover divider all correct. So the four symbols were carrying no visual weight that
  `BasicText` and `Image` do not, and the one control that *was* expected to change — the spinner, off
  Material's unchosen `#6650A4` — reads correctly as a HIG spoke wheel.
