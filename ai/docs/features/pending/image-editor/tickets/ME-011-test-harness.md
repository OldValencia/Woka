# ME-011 — Test harness and the fixture tool

| | |
|---|---|
| **Order** | 12 |
| **Depends on** | ME-006 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** A tool that exercises every `ParamSpec` type, and a harness that drives a session — so the
shell can be built and tested before a single real tool exists.

**Why now.** Written after the shell, a fixture tool only confirms what the shell already does.
Written before it, it is the specification.

## Steps

1. `FixtureTool.kt` (`jvmTest/support/`): one `EditorTool` whose `params` contains one of every spec
   type, with a manifest in each `ToolDomain` and a `stage()` returning a recorded, inspectable value.
   Provide the `Single`/`Repeating` and `Instant`/`Continuous`/`Canvas` variants as parameters of a
   factory function, not as six classes.
2. `EditorHarness.kt` (`jvmTest/support/`): builds a session plus a registry over a given tool list
   under a temp storage root, and exposes helpers to apply an op, undo, and read the folded recipe.
   Follow `FolderHarness` / `ViewerHarness` — a harness a test *uses*, never a base class.
3. Port the ME-004 and ME-006 tests onto the harness where it shortens them; leave them alone where it
   does not.

## Files

`jvmTest/support/FixtureTool.kt`, `support/EditorHarness.kt` (new)

## Done when

A test asserts that the registry lists the fixture tool in every domain, that the session records its
ops, and that the fold reaches its stage — with no production tool in the build.

## Result — 2026-08-18

`support/FixtureParams.kt`, `support/FixtureTool.kt`, the harness added to the existing
`support/EditorHarness.kt`, and two tests: `unit/EditorFixtureTest.kt` (the fixture's own contract)
and `unit/EditorHarnessTest.kt` (registry, session and fold driven end to end).

### Four places the ticket did not match the tree

- **`support/EditorHarness.kt` was not new.** It has existed since ME-004, holding `onEditorSession`
  — written then to fix a test that built a session in a field initialiser, before
  `MainDispatcherRule` had installed the test dispatcher, so its `stateIn(…, Eagerly)` derivations
  never collected. `onEditorSession` is kept as it is; `EditorHarness` and `onEditor` are added beside
  it.
- **Step 1's "recorded, inspectable value" and the Done-when's "the fold reaches its stage" pull
  against each other.** `RasterFold` does `tool.stage(op.params) as? RasterStage`, so a fixture stage
  of any other type is dropped **silently** — a fold assertion written against one would pass while
  measuring nothing. Resolved by splitting the two claims: the tool records every `ParamSet` it was
  asked about (`staged`), and a fold test passes a real `RasterStage` through `produce`. "The fold
  consulted the tool" and "the fold applied a stage" are now separately observable, which is what lets
  `a null stage is a no-op rather than a skip` say anything at all.
- **`ObjectParam` needs a `KSerializer`**, which the step does not mention: it is the one spec that
  cannot be declared in a line. `FixturePayload` is the `@Serializable` type it points at.
  `ChoiceParam` and `PreviewChoiceParam` likewise need an enum — `FixtureChoice`, with three options
  so a choice can be moved off its default in either direction.
- **"A factory function, not six classes"** is satisfied by the constructor's default parameters. A
  separate `fixtureTool(...)` beside it would be a second spelling of the same manifest, and the
  second spelling is the one that drifts.

### Deliberately not done

- **Not a `CanvasTool`.** `ToolInteraction.Canvas` is a manifest value and needs no overlay, and
  nothing before the shell dispatches to one. An overlay fixture earns its place in ME-101, not here.
- **`FakeTool` (ME-005's `ToolContractTest`) and `StageTool` (ME-007's `RasterFoldHarness`) are left
  alone**, per step 3, which authorises porting ME-004 and ME-006 only. `StubTool` was ME-006's and is
  gone — `EditorToolRegistryTest` now builds `FixtureTool`, and lost 20 lines doing it.
  `EditorSessionTest` is left alone: it asserts undo granularity and never needs a registry, so the
  harness would only add a scope to cancel. **Three stand-in tools remain**, and folding `StageTool`
  into `FixtureTool` is a real simplification available to whoever next touches ME-007's tests.

### The guard worth keeping

`the fixture declares one spec of every ParamSpec type` must not be maintainable by eye, and the
first version used `ParamSpec::class.sealedSubclasses`. It failed: **`kotlin-reflect` is not on the
test classpath**, and adding a dependency to guard one assertion is the wrong trade.

The replacement is stronger. An exhaustive `when` over the sealed hierarchy means a ninth `ParamSpec`
subtype stops the test file **compiling** — a compile-time guarantee rather than a red test. Adding
the branch then forces a name into `EVERY_SPEC_KIND`, and *that* is what fails until the fixture
declares a spec of the new type, and so until every row renderer built against the fixture has one
too.
