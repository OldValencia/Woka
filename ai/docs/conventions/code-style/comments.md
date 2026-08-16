# Comments and KDoc

Woka's comments are the design record. They explain **why this shape**, never what the line does — the
line already says that. This is what lets the codebase be re-read a year later without archaeology.

## The voice

```kotlin
/**
 * Decode cache shared by the viewer's still loader and the video poster-frame
 * extractor. Split out of `ThumbnailLoader` when that object was divided between
 * `viewer/image` and `viewer/video/engine`.
 *
 * **Least-recently-used, bounded by bytes** (F1). It used to be a
 * `ConcurrentHashMap` with no eviction at all, and the two populations in here
 * differ by three orders of magnitude — a video poster is ≤ 400 px (~0.36 MB)
 * while a still is cached at *native* resolution (96 MB for a 24 MP photo). A cap
 * on the number of entries would therefore have bounded nothing.
 */
object ImageBitmapCache
```

Three moves, in this order: **what it is**, **what shape it takes and why**, **what was rejected and
what that cost**.

## Rules

- **Name the rejected alternative and its failure.** "The previous key was `name.hashCode()`, which
  gave two `clip.mp4` files in different folders one shared thumbnail — the second folder showed a
  frame from the first." A comment that only praises the current design is decoration.
- **Cite the section that justifies a value:** `(§4)`, `(F1)`, `(TC-PERF-04)`, `(ME-009)`. A magic
  number with no citation is a defect.
- **Comment the default, not just the field.** Especially in settings: *why* off, *why* 20 minutes.
- **No restating the signature.** `/** Returns the width. */ fun width(): Int` is noise.
- **No `// TODO` without a ticket id.** `// TODO(ME-104): …` or nothing.
- **Diagnostics are marked** `TEMPORARY (§ref)` and grepped out once the cause is known.
- **Dates belong in measured claims:** "measured 2026-08-07, skia describes a PNG carrying a valid PQ
  `cICP` chunk as gamma close to sRGB" (`StillColorInfo`). A measurement without a date cannot be
  re-checked.

## Language

English, present tense, full sentences. Technical and dry — no exclamation marks, no "we", no
apologising for the code. Backticks around every identifier and path.

## Where the comment goes

- **File-level / class-level KDoc** carries the design argument. This is the default place.
- **Inline comments** are for a single surprising line: a retry count, an ordering constraint, a
  workaround for a platform bug. Two consecutive inline comments usually want to be one KDoc block.
- A `data class` field gets its own comment when its default or its unit is not obvious
  (`WokaSettings` is the reference for this).
