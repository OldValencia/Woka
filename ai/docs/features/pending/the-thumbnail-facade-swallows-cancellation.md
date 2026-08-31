# The thumbnail facade swallows cancellation

| | |
|---|---|
| **Reported** | 2026-08-31, found while answering a question about lazy loading in `implemented/the-thumbnail-cache-grows-forever.md` |
| **Area** | `feature/viewer/shell/MediaThumbnails` |
| **Feature** | none — the facade predates the feature docs |

**Goal.** Let cancellation through `MediaThumbnails`, so a scroll that leaves a tile behind cancels
its decode instead of logging a stack trace and reporting a missing picture.

**Why now.** Four of the facade's five entry points catch `Exception`, and
`CancellationException` **is** an `Exception`. The grid and the carousel are lazy containers that
start a decode inside each item's own composition (`StillCoverImage.kt:76`,
`MediaViewerCarousel.kt:82`), so leaving an item cancels its `LaunchedEffect` — which is exactly the
behaviour that keeps Woka from building thumbnails for tiles that were never on screen. That
cancellation arrives here and is eaten.

Three things follow, in rising order of how much they matter:

- **Noise.** `e.printStackTrace()` on every cancelled tile. Scrolling a large folder prints a stack
  trace per item that went past.
- **A lie to the caller.** The cancelled load returns `null`, which every caller reads as "there is no
  picture for this file" rather than "you stopped asking". `StillCoverImage` draws its placeholder for
  exactly that value.
- **Structured concurrency stops holding.** A coroutine that catches its own cancellation and returns
  normally is no longer cancelled; the parent's `join` and any `withTimeout` around it behave as
  though the work completed.

**The idiom already exists in this repository.** `VideoPlayerDecoder.kt:125`,
`VideoPlayerAudio.kt:68` and `VideoPlayerAudioDecoder.kt:139` each do

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e.printStackTrace()
}
```

The thumbnail facade is the one place on that road that does not.

## The four sites, and the two shapes they take

`MediaThumbnails.kt`, all inside `withContext(ImageBitmapCache.ioDispatcher)`:

| line | function | shape |
|---|---|---|
| 34, 40 | `loadThumbnail` | `try` / `catch (e: Exception)` → `printStackTrace`, `null` |
| 59, 65 | `loadPreview` | the same |
| 77 | `sourceSize` | `runCatching { … }.getOrDefault(IntSize.Zero)` |
| 110 | `frameSet` | `runCatching { … }.getOrDefault(ImageFrameSet.Single)` |

**`runCatching` is the same defect and looks innocent.** `kotlin.runCatching` catches `Throwable`,
which is wider than `catch (e: Exception)`, not narrower — so both `getOrDefault` sites swallow
cancellation as well and answer with a plausible-looking default (`IntSize.Zero`, `Single`) that the
caller cannot tell from a real one.

**`stillColor` is already correct and must be left alone.** It catches nothing at all, so cancellation
and a decoder failure both propagate. It is listed here only so the next reader does not "fix" it into
matching its neighbours.

## What is not claimed here

**How much work a fast scroll actually wastes is not measured.** A decode already inside Skia is a
blocking call and cancellation in Kotlin is cooperative, so an in-flight build finishes whatever this
ticket does; what changes is what happens to the *result* and to the coroutine. Whether a fast scroll
through a large folder queues decodes that complete long after their tile is gone is a separate
question, and answering it needs a probe rather than a reading — the author was offered one on
2026-08-31 and declined it as not worth the time. **Do not let this ticket grow into that measurement.**

## Steps

1. `MediaThumbnails.kt`: add `catch (e: CancellationException) { throw e }` ahead of the existing
   `catch (e: Exception)` in `loadThumbnail` and `loadPreview`, importing
   `kotlinx.coroutines.CancellationException` — the same two lines the video engine already uses.
2. `MediaThumbnails.kt`: replace the two `runCatching { … }.getOrDefault(x)` calls in `sourceSize` and
   `frameSet` with a `try` / `catch (e: CancellationException) { throw e }` / `catch (e: Exception)`
   returning the same defaults, so the fallback still covers a decoder that throws and no longer
   covers the caller giving up. `stillColor` is not touched.
3. If step 2 pushes the file past 150 lines (`wc -l`), extract the three-way catch into one private
   inline helper in the same file rather than splitting the facade — it is one surface.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaThumbnails.kt` (edit),
`shared/src/jvmTest/kotlin/com/woka/integration/` (new test).

## Verification

- `jvmTest/integration/` (new) — a `loadPreview` whose scope is cancelled while it runs completes the
  enclosing `job` as **cancelled**, and the call does not return `null`. Asserted by cancelling the
  parent job and checking `job.isCancelled` plus that no value was published, rather than by timing;
- the same for `frameSet`, which is the `runCatching` shape and the one a reader is least likely to
  suspect;
- a decoder that genuinely throws still yields `null` / `IntSize.Zero` / `ImageFrameSet.Single` — the
  fallback this ticket must not remove. `ImageLoaderTest.kt` already covers the corrupt-file road;
- `jvmTest/ui/` — untouched and green: the tiles that draw a placeholder for a real failure must keep
  doing so.

## Done when

- cancelling a thumbnail load cancels the coroutine instead of returning a value;
- no stack trace is printed for a cancelled load;
- a decoder that throws still produces the documented fallback, and a test says so;
- `MediaThumbnails.kt` is inside 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
