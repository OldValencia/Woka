# State holders — ViewModels, sessions, engines-with-state

`FolderViewModel`, `HomeViewModel`, `VideoPlayerState`, `EditorSession`. Anything that owns mutable
truth and publishes it.

## Shape

```kotlin
class FolderViewModel(
    val path: String,
    private val repository: LocalMediaRepository,
    internal val settingsRepository: SettingsRepository
) {
    internal val scope = CoroutineScope(Dispatchers.Main)

    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = combine(...)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- **No `androidx.compose.*` import.** Not `mutableStateOf`, not `Color`, not `IntSize` — if a shape or
  a size must be published, it is a plain type or a `core/data` model. This is checked mechanically.
- Dependencies arrive through the constructor and are built by Koin (`di.md`). Nothing is
  `object`-singletoned into existence and nothing is constructed inline.
- `private val _x = MutableStateFlow(...)` + `val x: StateFlow<…> = _x.asStateFlow()`. The mutable half
  never leaves the class.
- Public API is **verbs**: `toggleSelectMode()`, `preview(op)`, `commit()`. A public `setState` is a
  state holder that has given up.

## Derivation

Derived state is derived, not stored:

```kotlin
val visible = combine(items, settings) { i, s -> map(i, s) }
    .distinctUntilChanged()
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

A field that can be computed from two others is a third writer waiting to disagree. If the derivation
is more than a few lines, it moves to a pure file next door (`FolderStateMapper`) that takes inputs and
returns the snapshot — that file is where the unit tests point.

## Immutability

State is an `@Immutable data class`; every mutator returns `.copy()`. Lists and maps are **replaced**:
`copy(ops = ops + op)`, never `ops.add(op)`. A `MutableList` inside a published state is a bug even if
nothing mutates it today.

## Events

An event is a real type with identity — not a `Boolean` flipped back, not a timestamp. Two events
inside one clock tick must both survive; that is the exact failure a timestamp trigger produced here
before. Use a `Channel`/`SharedFlow` of a sealed event type, or a state field holding a value class
with a monotonic id.

## Scope and lifetime

- The holder owns its `CoroutineScope` and something owns the holder. Write in the KDoc **who
  constructs it and what cancels it** — a session with no stated owner leaks a decode loop.
- Long work goes to `Dispatchers.IO` (or the bounded dispatcher a cache exposes, like
  `ImageBitmapCache.ioDispatcher`), never to `Dispatchers.Main`.
- Cross-thread handshakes get a named type, not a `@Volatile Boolean` pair — `PlaybackGeneration` is
  the precedent, and it is named that because "generation" is the concept, not "flag".

## Writing Compose state off the main thread

**Assign it on the main thread. `writeUiState { }` (`feature/viewer/video/engine`) is the precedent —
a `withContext(Dispatchers.Main)` around assignments only,** with every disk and decoder call left on
the caller's dispatcher. Fields that are read together go in one block: a list and the flag derived
from it, split across two hops, can be shown split.

`mutableStateOf` is **snapshot** state, not a plain field, and a background coroutine that assigns it
directly fails in two different ways. Both were measured on one incident (2026-08-29,
`VideoPlayerDecodeStart.probeTracksAndChapters`), and knowing both is the point — fixing only the
first leaves a bug that looks unrelated.

**1 — the write is lost.** A direct assignment goes into the global snapshot, the recomposer advances
that snapshot underneath, and the write is gone. Nothing logs it and nothing throws. The symptom was
a video opening with no audio track at all: the picture played, the track menu was empty, reopening
the file fixed it, and it only bit on the first launch after a build — a cold start widens the
window. What identified it was logging `Snapshot.current.id` beside the values: three tracks and
`selectedAudio=2` at snapshot 1239, every field back at its constructor value at 1248, with no other
writer in the module.

**2 — the write is unseen.** Wrapping it in `Snapshot.withMutableSnapshot { }` makes it survive, and
that is *not enough*. Compose Desktop schedules recomposition off an observer of **global** snapshot
writes, which a nested snapshot's apply goes past; worse, when the write lands **before** the
composition first reads that state there is no read to invalidate, and the composition then takes a
snapshot older than the apply. The model is correct and the screen is stale, with nothing to trigger
a second pass until an unrelated click does one. The symptom was a crossed-out speaker over a
popover listing three audio tracks, and a video with no seek bar — both corrected the instant the
volume was nudged. Instrumented as one log line reading `composed=false now=true`: what the
composition had read, against the live value a moment later.

Two rules fall out of it:

- if state you wrote is back at its initial value, log `Snapshot.current.id` before suspecting the
  code that "must" have overwritten it;
- if the model is right and the screen is wrong, log the composed value *and* the live value in the
  same line. Equal values mean the read is fine and the problem is elsewhere; a disagreement names
  this bug outright.

## Testing contract

A state holder must be constructible in a test with fakes and no window. If it needs a `Composable` to
exist, the boundary is wrong. `jvmTest/support/` holds the harnesses (`test.md`).
