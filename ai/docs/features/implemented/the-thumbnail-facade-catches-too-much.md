# The thumbnail facade catches too much

| | |
|---|---|
| **Reported** | 2026-08-31, while answering a question about lazy loading in `implemented/the-thumbnail-cache-grows-forever.md` |
| **Closed** | 2026-08-31 |
| **Area** | `feature/viewer/shell/MediaThumbnails` |
| **Feature** | none — the facade predates the feature docs |

**Goal.** Stop `MediaThumbnails` turning things it should not catch into plausible answers.

## This ticket was filed on a claim that turned out to be false

It was called *"the thumbnail facade swallows cancellation"*, and it said that scrolling past a
tile printed a stack trace and reported the picture as missing, because `CancellationException`
is an `Exception` and the facade catches `Exception`.

**That cannot happen, and checking the signatures is all it took to know.** Nothing inside any of
those `try` blocks suspends:

| called inside the guard | |
|---|---|
| `ImageLoader.load` | `fun`, not `suspend` |
| `StillPreview.load` | `fun` |
| `VideoThumbnailExtractor.load` | `fun` |
| `StillDecoder.nativeSize` / `.frameSet` | `fun` — the whole interface is blocking |

A `CancellationException` can only arise where something observes cancellation, and nothing here
does. `withContext` throws it at **its own** boundary — before the block starts, or when
resuming the parent afterwards — and both are outside the `try`. So no stack trace per scrolled
tile, no `null` mistaken for "no picture", and structured concurrency was never broken.

The ticket was written from reasoning about the shape of the code instead of from its types.
Recorded rather than quietly deleted, because the failure mode is worth remembering: it was
plausible, specific, well-argued and wrong.

## What was actually there, and is now fixed

**`runCatching` catches `Throwable`.** `sourceSize` and `frameSet` were written as
`runCatching { … }.getOrDefault(x)`, and `Throwable` is *wider* than `Exception`, not narrower.
An `OutOfMemoryError` out of a decoder — the plausible one, on a picture large enough to be
worth worrying about — was turned into `IntSize.Zero` or `ImageFrameSet.Single`, an answer the
caller cannot tell from a real one. Both now catch `Exception`, so an `Error` travels.

That is the whole defect. It is smaller than the one this file was opened for.

## What shipped

All four guarded sites go through one private `decoded(fallback, report) { … }` in the same
file, which is where the rule now lives:

- catches `Exception`, never `Throwable`;
- rethrows `CancellationException` first — **hardening, not a fix**. It cannot fire today, and
  the KDoc says so in as many words. It is there because the day one of those decoders gains a
  `suspend`, a swallowed cancellation would appear silently and a caller would read "you gave
  up" as "this file has no picture". The video engine already writes it this way
  (`VideoPlayerDecoder.kt:125`);
- `report` is false for `sourceSize` and `frameSet`, keeping them as quiet as `runCatching` was:
  a file they fail on is one the loader is about to fail on too and print about.

`stillColor` is untouched. It catches nothing at all and was already correct — listed here so
the next reader does not "fix" it into matching its neighbours.

## Verification

- `jvmTest/integration/MediaThumbnailsFallbackTest.kt` (new) — a corrupt still still answers
  `IntSize.Zero`, one frame and no preview; a healthy one still reports its real size, and a
  burst still counts its five frames. That last pair is the half a too-eager `catch` would hide;
- **the narrowing itself is not asserted, and the test says why**: there is no way to make a real
  decoder throw an `Error` on demand from here, so what is pinned is the behaviour that had to
  survive the change rather than the change itself;
- `jvmTest/integration/ImageLoaderTest.kt` — untouched and green; it already covered
  `loadThumbnail` absorbing a corrupt file;
- `MediaThumbnails.kt` is 145 lines, the anti-FQN scan is clean, and
  `./gradlew.bat :shared:build` is green.

## Done when

- `sourceSize` and `frameSet` no longer answer a default for an `Error` — **done**;
- the cancellation rethrow is in place and honestly labelled as hardening — **done**;
- a decoder that throws still produces the documented fallback, and a test says so — **done**;
- the false premise this ticket was opened on is written down rather than erased — **done**,
  above;
- `./gradlew.bat :shared:build` green — **done**.

**Not verified by hand**, and it does not need to be: there is nothing on screen to look at. No
behaviour a user can see was changed.
