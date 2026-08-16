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

## Testing contract

A state holder must be constructible in a test with fakes and no window. If it needs a `Composable` to
exist, the boundary is wrong. `jvmTest/support/` holds the harnesses (`test.md`).
