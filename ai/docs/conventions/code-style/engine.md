# Engines and the platform split

Decoders, the player, Skia surfaces, JavaCV/FFmpeg, AWT, threads, native memory. Everything under
`feature/*/engine/`, `feature/viewer/image/`, `core/system/`, and every `.jvm.kt`.

## commonMain or jvmMain

- `commonMain` holds anything whose **signature** has no JVM type: models, math, state machines,
  schemas, tool contracts. `androidx.compose.ui.graphics.Matrix` and `IntSize` are available there —
  use them rather than inventing a parallel geometry type.
- `jvmMain` holds Skia surfaces, `java.io.File`, JavaCV, AWT, `javax.sound`, ImageIO.
- A capability needed by common code crosses through `expect`/`actual`, and **both halves live in the
  same package** (`core/utils/VideoThumbnail.kt` / `VideoThumbnail.jvm.kt`). Never a runtime cast,
  never a reflection lookup.
- Push the platform leaf down: keep the layout, the state and the decision in `commonMain`, and let
  only the call that needs AWT be `actual`.

## Native resources

- Everything opened is closed on every path, including cancellation: grabbers, `SourceDataLine`,
  Skia `Surface`, `Image`. `use {}` where the type allows it, `try/finally` where it does not.
- A decoded video frame is ~15 MB of native memory behind a few dozen bytes of Java wrapper. The
  collector cannot see the pressure, so the staging buffer is what paces it — read the note in
  `desktopApp/build.gradle.kts` before changing anything that allocates per frame.
- Reuse buffers across frames instead of allocating per frame; if a buffer must be per frame, say in
  the KDoc what bounds the number of them in flight.
- Anything that can hold a file open on Windows must release it before the file is replaced.

## Threading

- One owner per resource. A grabber is touched from exactly one coroutine; everything else asks that
  coroutine.
- Cross-thread handshakes are a named type with a monotonic generation, not a pair of volatile
  booleans (`PlaybackGeneration`). A seek writes its target **before** it signals, never after.
- Blocking native calls run on `Dispatchers.IO` or a dedicated single-thread dispatcher, never on
  `Dispatchers.Main`. **Compose-facing state is written back on Main** — from the decode loops that
  is `writeUiState { }`, which hops to Main and takes assignments only. Assigning `mutableStateOf`
  straight from a background coroutine loses the write when the recomposer advances the global
  snapshot; wrapping it in a mutable snapshot instead keeps the write and loses the *repaint*. Both
  halves are the bug behind "the file opened with no audio track and no seek bar, reopening fixed
  it" — `state.md` has the full account.
- Bound the parallelism of anything that decodes (`Dispatchers.IO.limitedParallelism(2)` is the
  precedent for image decode) — unbounded decode turns into unbounded native memory.

## Capability, not assumption

- What a decoder does is **measured**, not assumed. A question about a format, a tag or a colour
  transfer becomes a probe under `jvmTest/probe/` that prints a table, and its answer is recorded with
  a date in the ticket or plan that asked for it, and cited in the code (`comments.md`).
- Feature detection at runtime returns a capability object; the UI reads it and degrades. No
  `try { … } catch (Throwable) { }` standing in for a capability check.
- FFmpeg and Skia disagree about colour: `StillColorInfo` is filled from the FFmpeg path and never
  from Skia, because Skia describes a valid PQ `cICP` PNG as "gamma close to sRGB". Assume nothing
  about which library is authoritative — check what the existing code decided and why.

## Errors

- An engine reports failure as a value (a sealed result, a state field), not as an exception crossing
  into the UI.
- The user-facing half of a failure is a notice with a cause and a next step
  (`PlaybackHealthNotice`, `FrameExportBanner`), never a silent no-op.
- Logging is deliberate and greppable. Diagnostics added during a hunt are marked `TEMPORARY (§ref)`
  and removed once the cause is known.
