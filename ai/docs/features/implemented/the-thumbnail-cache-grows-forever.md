# The thumbnail cache grows forever, and may not need to exist

| | |
|---|---|
| **Reported** | 2026-08-30, by hand — ~100 MB in `.thumbnails` before it was cleared |
| **Resolved** | 2026-08-31 — the cache stays, bounded at 100 MB, least recently used first |
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

### Measured 2026-08-31 — `jvmTest/probe/ThumbnailCacheProbe.kt`, verbatim

```
Thumbnail cache probe — 8 tiles per population

| population        | cache absent | cache present | per tile absent | per tile present | rebuilt on the warm pass | poster reads flat |
|-------------------|--------------|---------------|-----------------|------------------|--------------------------|-------------------|
| HEIC stills       | **103 ms**   | **8 ms**      | 12 ms           | 1 ms             | none                     | **no**            |
| video, real frame | **136 ms**   | **109 ms**    | 17 ms           | 13 ms            | **8 of 8**               | **yes**           |
| video, flat frame | **109 ms**   | **81 ms**     | 13 ms           | 10 ms            | **8 of 8**               | **yes**           |
| stills, 4000x3000 | **238 ms**   | **12 ms**     | 29 ms           | 1 ms             | none                     | **no**            |

A rebuilt poster means the warm pass **never hit the cache**, so that row's
`cache present` figure is a second extraction and not a cache read.

Both clips produce a flat poster, so the rows above cannot separate "the video
cache never works" from "it never works for a flat frame". Planting a poster
that `isMonochrome` accepts, at the exact name the extractor looks under:
planted poster reads flat: **no** · rebuilt anyway: **0 of 8** · **28 ms** for 8 tiles

Mean poster: **2892 bytes** over 8 entries — a fixture fact only.
The reported ~100 MB cannot be turned into an entry count from here.

Walking 4000 entries, summing length and mtime: **130 ms**.
That is the whole cost of the `total size` policy's directory pass.

One poster of real photographic content, at each format Skia can write:

| format      | on disk     | against PNG  | encode  |
|-------------|-------------|--------------|---------|
| PNG (today) | 20624 bytes | 100 % of PNG | 1856 µs |
| JPEG q85    | 13328 bytes | 64 % of PNG  | 1132 µs |
| WEBP q80    | 6680 bytes  | 32 % of PNG  | 4662 µs |
```

**The disk cache earns its place, for both populations, and the answer to "can we simply not write
it" is measured rather than argued.** At the size a photograph off a phone actually is —
4000 × 3000 — a tile costs **29 ms to build and 1 ms to read**. A screenful of twelve is a third of
a second of decoding on every launch, on a dispatcher limited to two at a time, and that figure is
*generous* to the no-cache branch: the population is a smooth PNG gradient, which is the cheapest
thing to decode, where a real HEIC is slower still. Videos: 16 ms → 3.4 ms a tile, from the planted
poster that shows what a working hit costs.

`ImageBitmapCache` does cover a session, so the cost of dropping the directory is once per file per
launch rather than once per scroll. It is still a third of a second of grid, every launch, forever.
**The cache stays; the question is only what bounds it.**

**The system thumbnail cache cannot stand in for it.** Explorer's `thumbcache_*.db` has no supported
file-level API — reaching it means COM (`IShellItemImageFactory`) and therefore a native bridge this
project does not have. That is the smaller objection. The fatal one is that `StillPreview.build`
folds Woka's own edit recipe in through `AppliedStillEdits.apply`: a system thumbnail is the picture
as the OS sees it on disk, which for an edited file is the **pre-edit** picture. Woka's grid would
show the wrong image.

**A sweep is affordable.** 130 ms to walk 4 000 entries and read length and mtime for each — off the
critical path after first frame, not before it.

## What size an entry is, and what else can be saved

**Entries are already resized, and 400 px is not generous — it is tight.** `PREVIEW_MAX_PX = 400` in
`StillPreview`, and the poster extractor scales to the same 400 on its longest edge. Nothing is stored
at original size. The reason it cannot go lower is the grid's own ladder,
`GridDensity.kt:19-21` — `120 / 240 / **400** dp` — read by `GridCells.Adaptive(columnSize)`, which
makes a cell *at least* that wide and usually wider. So at the largest density a tile is ≥ 400 dp,
which at 125 % display scaling is ≥ 500 physical pixels: **the poster is already being upscaled
there**, and shrinking it would show. The carousel, at 56–72 dp, is over-served five times over by
the same entry — but giving it a second smaller one adds files rather than removing them.

**The format is where the disk actually goes.** A 400 px poster of real photographic content is
20 624 bytes as PNG and **6 680 as WebP — 32 %**. Encoding costs 4.7 ms against 1.9 ms, paid once,
on a build path that already costs 29 ms; JPEG is cheaper still to encode but only reaches 64 %, and
it cannot carry alpha — which `looksBlank` exists precisely because a fully transparent picture is a
real thing to find. Changing the format changes the entry's name and so needs a `KEY_SCHEMA_MARKER`
bump, which is a one-time wipe the storage doc already calls cheaper than a migration.

**The Home grid writes entries for formats the Home grid never reads.** `looksBlank`
(`FolderCoverChoice.kt:118`) calls `loadStillCover` on **every** candidate cover, and that reaches
`StillPreview.load` for any still whatever its extension. But `StillCoverImage.kt:78` only takes
Woka's road when `needsWokaCover || AppliedStillEdits.carries(file)` — an ordinary JPEG cover is
drawn by Coil, which never opens this directory. So choosing a cover for a folder of JPEGs writes a
400 px PNG that the grid will not read. It is not pure waste — the viewer's carousel goes through
`loadPreview` for every still and would read it — but it is a write per folder card, on Home, for a
picture that may never be opened. A decode that does not persist would remove it.

**Two of the three growth causes are Woka's own actions and can be invalidated exactly.** The key
cannot be reversed into a path, which is what rules out finding orphans — but at the moment the
source changes, Woka is holding the old path:

- an editor save overwrites in place, and the pre-write key is computable before the write;
- `FolderActions.renameFile` (`FolderActions.kt:80`) knows both names.

Deleting the old entry at those two points kills the edit and rename leaks at the source. Only moves
and deletions made outside the application would still need the sweep, which is the honest division
of labour between an exact fix and a bound.

### Found while measuring, and not what this ticket was about

**A video whose poster reads flat is re-extracted on every launch, forever.**
`VideoThumbnailExtractor.load` re-reads its cached PNG through `ImageIO` and re-runs `isMonochrome`
on it; a poster that fails goes straight back to ffmpeg. All 8 tiles rebuilt on the warm pass for
both clips, and **0 of 8** once a poster the heuristic accepts was planted at the same name — so the
cached path itself is sound and the re-check is the whole of it. A dark opening frame is a real
thing to have in a folder of videos.

This is **not** a growth leak: the entry is keyed on the source's own path, length and mtime, so the
rebuild overwrites the same file rather than adding one. It is a permanent cost per launch, and it
also means the warm figures in the video rows above are two extractions rather than a cache read.

**The cached path decodes the same PNG twice**, once via `ImageIO.read` for the heuristic and again
via `readBytes` + `Image.makeFromEncoded` for the bitmap. Cheap to fix, and worth stating next to
the above because both live in the same eight lines.

## Decided 2026-08-31, by hand, on the measurement above

1. **The cache stays.** 29 ms a tile against 1 ms, at the size a photograph actually is.
2. **The system thumbnail cache is not an option**, and the reason is correctness rather than
   plumbing: it would show the pre-edit picture for an edited file.
3. **Bound it by total size, 100 MB**, oldest first, swept off the critical path after first frame.
4. **`setLastModified` on a cache hit**, so mtime means *last looked at* rather than *built*.
   Without it, eviction by mtime is FIFO — a thumbnail opened daily and one never opened again are
   indistinguishable — and "delete what has not been looked at in a long time" is not expressible.
   This is what the `liveness` branch below wanted an index for, obtained without one.
5. **The flat-poster re-extraction is fixed in this ticket**, not deferred to its own.

Open, and not settled by the measurement: whether to move the entries to **WebP** (32 % of PNG, one
`KEY_SCHEMA_MARKER` bump) and whether `looksBlank` should decode **without persisting**. Both are
savings rather than corrections; neither is required by the bound above.

## Implemented 2026-08-31

All four in one pass, in the order the dependencies dictated: the flat-poster bug first
because it hurt now, the format next because it renames every entry, the bound last
because it had to be written against the final naming.

**1. A flat poster is no longer re-extracted.** `VideoThumbnailExtractor.readCached`
decodes what it reads and asks it nothing. The `isMonochrome` re-check belonged to
`extract`, where a frame is *chosen*; re-asking after one had been chosen and written
only re-ran the same failing search, forever, on every launch. The double decode went
with it — the cached PNG used to be read once through `ImageIO` for the check and again
through Skia for the bitmap.

**2. Entries are WebP.** `KEY_SCHEMA_MARKER` is `.keys-v3` and the suffix is `.webp`, so
the existing directory is dropped once on first launch, which `WokaStorage` already
calls cheaper than a migration. `core/ui/image/PreviewEncoding.kt` carries the encoder
and the measurement; `encodePng` is untouched and stays what it is — the encoder for a
frame the **user** asked to export, where lossless is the only defensible answer.
`saveAndDecode` also stopped round-tripping through PNG bytes purely to hand Skia
something to decode.

**3. The bound.** `core/data/ThumbnailSweep.kt`: 100 MB, oldest first, by bytes and not
by count for the same reason `ImageBitmapCache` is. Run from `app/ThumbnailHousekeeping.kt`,
a composable that draws nothing, five seconds after the first composition — a
`LaunchedEffect` is already past the first frame, but not past the burst of cover decodes
behind it, and those run two at a time on one dispatcher.

**4. `markUsed` on every disk hit**, in both readers. Without it the ordering is FIFO —
an entry is written once and never touched, so a poster opened daily and one never
opened again are indistinguishable — and "delete what has not been looked at in a long
time" cannot be expressed at all. This is what the `liveness` branch wanted an index for,
obtained without one.

### `looksBlank` was examined and deliberately left alone

The waste is real: `FolderCoverChoice.looksBlank` decodes up to four candidates per
folder card and persists each, though only the chosen one is ever displayed. But every
way of *not* persisting is worse than the bound that now cleans it up:

- persisting nothing loses the disk entry for the **chosen** cover too, because the
  memory cache then answers the card's own draw and no write ever happens. A folder of
  HEICs would pay a full decode per card per launch — 29 ms a tile, the exact cost this
  ticket exists to avoid;
- persisting nothing **and** skipping the memory cache decodes the chosen cover twice in
  the common case, where the first candidate is not blank — adding a decode to save
  nothing;
- persisting only the chosen one cannot be expressed: which one is chosen is not known
  until after every candidate has been decoded.

The 100 MB bound removes rejected candidates by age like anything else, which is what a
bound is for. Recorded rather than done, so the next reader does not re-derive it.

## Two things this changed in the tests, both of which were passing for the wrong reason

**`ThumbnailCacheTest` asserted the video cache worked, while it never hit.** It compared
the poster's bytes before and after a second load, reasoning that a re-extraction would
land on a different frame — but every fixture is a few seconds of low-motion footage, so
a re-extraction wrote the same pixels and the comparison held either way. The probe
caught it by comparing mtimes.

**And then mtime stopped working too**, the moment `markUsed` began moving it on a hit:
a read and a rebuild became indistinguishable by it. Both the test and the probe now
plant a poster of a length nothing here produces and check whether it survived. A
diagnostic that cannot fail is worth less than none.

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

## Asked and answered without changing anything: when does a preview get built?

Raised by hand 2026-08-31, from how Explorer does it — start building a thumbnail only once the item
is scrolled far enough to show a pixel. **Woka already behaves that way, and by construction rather
than by design.** Every list is a lazy container — `LazyVerticalGrid` on Home (`HomeScreen.kt:65`)
and in a folder (`FolderGrid.kt:97`), `LazyColumn` in search and in the frame strip, `LazyRow` in the
viewer's carousel — and the decode is started *inside* the item's own composition
(`StillCoverImage.kt:76`, `MediaViewerCarousel.kt:82`), never beside it. A tile that has not been on
screen never composes, so its `LaunchedEffect` never runs and nothing is decoded or written.

Two honest qualifications. Compose prefetches one item ahead in the scroll direction, so the build
starts marginally before the pixel — which is the better behaviour, not a leak. And a decode already
running is not interrupted when its item leaves composition: the work is a blocking Skia call, and
cancellation in Kotlin is cooperative.

**Found while answering, not fixed, and worth its own ticket.** `MediaThumbnails.loadPreview` and
`loadThumbnail` both wrap their body in `catch (e: Exception)`. `CancellationException` is an
`Exception`, so **cancellation is swallowed, printed as a stack trace, and turned into `null`** —
structured concurrency does not work through that, and a fast scroll makes it noisy. Left alone
deliberately: it is a coroutine defect in the thumbnail facade, not a question about the cache's size.

## Verification

- `jvmTest/probe/ThumbnailCacheProbe.kt` (+ `Report`, `Fixtures`) — cold-grid timings with and without
  the directory, recorded above with their date, taken **before** the branch was chosen;
- `jvmTest/integration/ThumbnailSweepTest.kt` (new) — under budget is left alone; over budget comes
  back under it; what survives is what was used most recently; **a marked entry outlives a newer one
  that was never read**, which is the whole point of `markUsed`; and the schema marker is never swept,
  because deleting it would turn the next launch's sweep into a purge. All over `withTempStorageRoot`,
  never a real `Documents` folder;
- `jvmTest/integration/ThumbnailCacheTest.kt` — the disk hit is asserted by a planted poster rather
  than by bytes or mtime, neither of which can tell a read from a rebuild any more; a poster
  `isMonochrome` rejects is served rather than re-extracted; a hit leaves a mark;
- `ThumbnailPipelineTest.kt` reads the entry through Skia, since WebP has no `ImageIO` reader;
  `StorageKeyTest.kt` follows the v3 suffix. `GridCoverTest.kt`, `FrameCacheKeyTest.kt` untouched;
- `./gradlew.bat :shared:build` — green.

## Done when

- the measurement exists and is written down here with its date — **done**, above;
- `.thumbnails` has a stated bound, or a stated reason for not existing — **done**: 100 MB, least
  recently used first, swept five seconds after the first composition;
- editing the same photograph twenty times does not leave twenty PNGs behind — **done**, though by
  bounding rather than by repair: the key cannot be reversed into a path, so nineteen orphans are
  unidentifiable and are removed as the oldest entries instead of as known dead ones. Stated plainly
  because it is a weaker property than the line asks for;
- `./gradlew.bat :shared:build` green — **done**.

**Confirmed by hand 2026-08-31.**
