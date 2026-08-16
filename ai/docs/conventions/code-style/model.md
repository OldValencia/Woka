# Models — data classes, enums, sealed hierarchies

`MediaItem`, `MediaKind`, `TrackPrefs`, `WatchRecord`, `StillColorInfo`, `ToolOp`, `ParamValue`.
Everything that is stored, serialised, published or passed between layers.

## Shape

```kotlin
@Immutable
@Serializable
data class PinnedSession(
    val path: String,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)
```

- `data class`, all `val`, defaults on everything that has a sensible one.
- `@Immutable` when it crosses into Compose — it is what lets the compiler skip recomposition.
- `@Serializable` only if it actually reaches disk or a document; do not decorate speculatively.
- No behaviour beyond pure derivation. A computed property is fine (`StillColorInfo.needsColorManagement`);
  anything touching a file, a clock or a cache is not — that goes to a repository or an engine.

## Types

- **No platform type in a serialised model.** Paths are `String`, colours are `Long` ARGB, sizes are
  two `Int`s. `java.io.File` and `androidx.compose.ui.graphics.Color` do not appear in anything that
  can be written to JSON. Convert at the boundary.
- Prefer a small enum over a `String`/`Boolean` pair. `ResumeMode { Off, LongOnly, Always }` exists
  because "off" and "long videos only" are not two states of one switch — folding the third into a
  sentinel threshold would be unreadable in `settings.json`.
- Prefer a `sealed interface` over a nullable discriminator when the branches carry different data
  (`EditSaveResult`, `RasterStage`).
- Units live in the name: `resumeMinDurationMinutes`, `subtitleColorArgb`, `retainedBytes`,
  `defaultZoomPercent`. Never a bare `duration` or `size`.

## Evolution

- A new field **always** has a default, and that default reproduces today's behaviour for a file
  written before the field existed. Comment says why that default (`comments.md`).
- Decoding is tolerant: unknown keys are ignored, an undecodable value falls back to the default
  rather than throwing. A model that crashes on an old file has turned a user's settings into a
  landmine.
- A schema whose *meaning* changed needs a version marker and an explicit migration —
  `SIZE_SCHEMA_VERSION` in `WokaSettings` is the precedent, including the note about why the default
  stays 0.
- Never reuse a key for a different meaning. Add a new one and sweep the old.

## Placement

- `core/data/` for domain models shared by more than one feature.
- `feature/<name>/…/model/` for models only that feature speaks (`viewer/video/model/Cue.kt`).
- `commonMain` unless a signature genuinely needs a JVM type (`engine.md`).
- One concept per file. A file of small siblings (`MediaTrack`, `MediaTracks`) is named for the concept
  they share.
