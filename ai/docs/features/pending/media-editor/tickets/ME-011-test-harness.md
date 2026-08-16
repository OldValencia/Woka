# ME-011 — Test harness and the fixture tool

| | |
|---|---|
| **Order** | 12 |
| **Depends on** | ME-006 |
| **Feature** | [Media Editor](../media-editor.md) |

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
