# The thumbnail cache grows forever, and may not need to exist

| | |
|---|---|
| **Reported** | 2026-08-30, by hand — ~100 MB in `.thumbnails` before it was cleared |
| **Area** | `core/data/WokaStorage`, `feature/viewer/image/StillPreview`, `feature/viewer/video/engine/VideoThumbnailExtractor` |
| **Feature** | none — the poster cache predates the feature docs |

**Goal.** Decide what bounds `Documents/OldValencia/Woka/.thumbnails`, and first decide whether it
should exist at all.

**Why now.** It has no upper bound and **nothing ever removes an entry**. Every still that takes
Woka's own road and every video that gets a poster writes a 400 px PNG there, and the only code that
deletes anything is `WokaStorage.prepareThumbnailsDir`, which wipes the whole directory when
`KEY_SCHEMA_MARKER` changes — a one-time migration, not housekeeping. A hundred megabytes accumulated
without the user asking for anything unusual.

## Why it only ever grows — measured against the tree

`WokaStorage.thumbnailFile` names an entry by

```
SHA-256("${absolutePath}|${length}|${lastModified}${frame}").take(8) + ".png"
```

Three consequences follow from that key, and all three are leaks:

- **Every edit orphans an entry.** Writing a recipe changes both the length and the mtime of the
  photograph, so the next read misses and writes a *second* PNG. The first can never be named again.
  `ImageBitmapCache`'s own KDoc already records this — the poster "orphans rather than going stale" —
  as a correctness property. It is, and it is also the leak.
- **Every rename, move or delete orphans one.** The key holds the absolute path; nothing watches for
  the source going away.
- **A frame set multiplies it.** `frameIndex` is part of the key, so a five-shot burst can leave five.

**The path cannot be recovered from the key**, which is what rules out the obvious repair: given an
orphan there is no way to ask whether its source still exists. Any policy has to work from the file
system's own metadata — size, age, access time — or the cache needs an index it does not have.

**This has just got worse, and by my hand.** `chooseFolderCover` (2026-08-30) decodes a preview per
folder card, and up to four for a folder whose pictures are blank, so the Home grid now writes entries
it never used to. Stated here because a ticket that hides the cost of the change that caused it is
worth nothing.

## The question before the fix: is the disk cache earning its place?

The author asked it directly, and it is the right first question. What the cache buys today:

- **Stills on Woka's road** — `heic`, `heif`, `avif`, `tif`. `StillPreview.build` is the expensive
  pass: decode at full size, scale to 400 px, encode a PNG. Its own doc says "paid once per file
  ever", and the disk copy is what makes that true **across restarts**.
- **Video posters** — an ffmpeg grab, which is far more expensive than a still decode.
- Nothing else. Ordinary JPEG and PNG tiles go through Coil, which never touches this directory.

What already exists without it: `ImageBitmapCache`, 256 MB, LRU by bytes, in memory for the session.
So dropping the disk layer costs **one rebuild per file per launch**, for those formats only. Whether
that is acceptable is a measurement nobody has taken: how long a cold Home grid takes over a folder of
HEICs and a folder of videos, with the directory present and absent.

**Take that measurement first.** A probe under `jvmTest/probe/` printing the two timings, and its
answer recorded here with a date, is what decides between the two branches below.

## The two branches

**If the cache stays — bound it.** The precedent is in this repository already: `ImageBitmapCache` is
LRU by *bytes* against a budget (F1), and the reasoning there transfers, including why a cap on the
number of entries bounds nothing. What is different is that this one is on disk and survives the
process, so the sweep needs somewhere to run — startup is the obvious place, and it must not delay the
first frame. Candidates, in rising order of what they need:

- **Age.** Drop entries not modified in N days. Needs nothing, and the mtime of the *entry* is already
  a rough proxy for when it was last rebuilt — but not for when it was last *read*.
- **Total size.** Walk, sum, delete oldest until under a budget. Needs one directory walk; the honest
  companion to the in-memory budget, and the one that answers "a hundred megabytes" directly.
- **Liveness.** Delete entries whose source is gone. **Needs an index**, because the key is a hash —
  which is a schema change and a `KEY_SCHEMA_MARKER` bump.

**If the cache goes — say what replaces it.** Video posters are the case that would hurt most, and
they may deserve to keep a disk cache even if stills lose theirs. A split answer is allowed and may be
the right one.

## Verification

- a probe recording cold-grid timings with and without the directory, dated, before either branch;
- whichever branch is taken: a test that the directory does not exceed its bound after N writes, over
  a temp storage root (`withTempStorageRoot`) — this must never touch a real `Documents` folder;
- `jvmTest/integration/ThumbnailCacheTest.kt`, `ThumbnailPipelineTest.kt`, `GridCoverTest.kt`,
  `FrameCacheKeyTest.kt` — unchanged and still green whatever is decided.

## Done when

- the measurement exists and is written down here with its date;
- `.thumbnails` has a stated bound, or a stated reason for not existing;
- editing the same photograph twenty times does not leave twenty PNGs behind;
- `./gradlew.bat :shared:build` green.
