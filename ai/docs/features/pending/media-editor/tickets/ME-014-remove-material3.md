# ME-014 — Remove the `material3` dependency

| | |
|---|---|
| **Order** | 14 — approved; **must land before ME-101** |
| **Depends on** | ME-001 |
| **Feature** | [Media Editor](../media-editor.md) |

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
