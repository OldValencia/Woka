# Tests, harnesses, fixtures, probes

`shared/src/jvmTest/kotlin/com/woka/` — `unit/`, `integration/`, `ui/`, plus `support/`, `fixtures/`
and `probe/`.

## The four tiers

| Directory | Touches | Use it for |
|---|---|---|
| `unit/` | nothing — no disk, no window, no Koin | math, folds, state machines, mappers, schemas |
| `integration/` | real files under a temp storage root | repositories, save jobs, caches, settings round-trips |
| `ui/` | Compose automation through `support/UiHarness` | what renders, what a click does, enablement |
| `probe/` | the machine's real decoders | answering a capability question — asserts nothing |

Put a test in the highest tier that can still fail for the right reason. A fold tested through the UI
is a slow test that will be deleted the first time it flakes.

## Naming

Test names are sentences about behaviour, not about methods:

```
a failure mid-write leaves the original byte-identical
an op added last but ranked first applies first
a disabled tool disappears from the rail and its ops are reported as skipped
```

Class name says what is under test: `RasterFoldTest`, `AtomicWriteTest`, `EditorToolRailUiTest`.

## Harnesses, not base classes

Shared setup is an object in `support/` that a test *uses*: `FolderHarness`, `ViewerHarness`,
`PlayerHarness`, `UiHarness`, `TempStorageRoot`, `SettingsWriter`, `KoinOnce`, `MainDispatcherRule`,
`Await`, `HotFlow`. Inheritance hides setup; a harness makes it a visible line in the test.

A new harness is justified only when two tests already need it. Until then, keep it local.

## Fixtures

- Generated, not committed, where generation is cheap (`fixtures/MediaFixtures.kt`,
  `ImageFixtureSpecs`, `VideoFixtureSpecs`, `ExifFixture`). A generated fixture states what it
  contains, so a failure blames the right thing.
- **Check your fixture before blaming the code.** A wrong fixture has cost more debugging time here
  than any real bug.
- A fixture requiring a capability the machine may lack (an encoder, a codec) checks
  `FfmpegCapabilities` first and skips with a message naming what was missing.

## Async

- `runTest` plus `MainDispatcherRule`; never `Thread.sleep`. Waiting is `Await`/`HotFlow`, with a
  timeout and a message saying what never happened.
- A test that needs real time (a pacer, a debounce) says so in a comment and bounds it.

## Storage isolation

Every test that writes goes through `TempStorageRoot`. The Gradle test task also sets
`woka.storageRoot` for the whole JVM, because settings shutdown hooks fire long after a per-test
override is restored — do not rely on one without the other.

## Probes

A probe prints a table and asserts nothing about the machine: its output is evidence, not a gate. Its
output is recorded **with a date in the ticket or plan that asked for it**, the decision it settles
goes into the feature's decision log, and both are cited in the code. There is no separate
`ai/docs/testing/` tree — evidence lives next to the question it answers, because a dated file in a
directory nobody opens is how a probe result rots. `DecoderCapabilityProbe` + `CapabilityReport` is
the shape to copy.

## What must have a test

Anything that can lose or corrupt a user's file, any pure transform, any schema round-trip, any
ordering rule, and every bug fixed — the regression test is part of the fix, named for the behaviour
that was wrong.
