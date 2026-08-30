# ME-123 — A huge source never enters the shared cache

| | |
|---|---|
| **Order** | 67 — before ME-125 and ME-128; it is the one that can lose other work |
| **Depends on** | ME-133 (every read is a decode plus a fold), ME-135 (the save signal) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Refuse an `ImageBitmapCache` entry that is large enough to evict most of the cache to make
room for itself, so one 100 MP photograph cannot turn every thumbnail in the app cold.

**Why now.** The guard rail §10 measured and did not build. A 100 MP source is **384 MB** of native
pixels against a **256 MB** budget (ME-010): admitting one evicts everything else *and still does not
fit*, so the app pays the eviction storm and gets no cache entry out of it. ME-133 made this worse
rather than better — every read of an edited file is now a decode **and** a fold, so the raw decode and
the folded result exist together and the folded one is what `ImageLoader` stores.

## Where the leak actually is, measured against the tree

Two of the three roads named by the backlog block are **already closed**, and saying so is half of
this ticket:

- **`EditorProxy`** caps at 2560 px and its doc says it is "deliberately not cached anywhere". Correct
  as written; nothing to do.
- **`ImageSaveJob`** decodes native at save, uses it and drops it, with the ME-010 numbers in its KDoc.
  Also correct; nothing to do.
- **`ImageLoader.load`** is the one that is open. It decodes at native resolution, folds the recipe
  over it and writes the result to `ImageBitmapCache[key(path, frameIndex)]` unconditionally. That is
  the viewer's own road, it runs *before* the editor is opened, and it is what puts 384 MB in a 256 MB
  cache.

**The gate goes in `set`, not in `ImageLoader`.** A caller that has to remember not to cache is a rule
somebody forgets, and `StillPreview`, `MediaThumbnails` and the frame strip all write entries too. One
place, and no caller learns.

**Refusing is not the same as evicting.** `evictDownToBudget` already keeps the total under budget —
what it cannot do is decline. Admitting a 384 MB bitmap makes it evict every other entry, keep the
huge one, and remain over budget; the picture is returned to the caller either way, so refusing costs
exactly one re-decode on a later re-open and saves every other entry.

**The half this ticket does not take.** The block also carries *"the decode off the composition
thread"* — `EditorAnimatedProxy.kt:77` is `remember(file, frameIndex) { EditorProxy.load(...) }`, which
runs synchronously while composing, 667 ms for a 100 MP source (ME-010). That needs a placeholder, an
ordering against the frozen viewer underneath and an answer for `Cancel` pressed mid-decode, none of
which is a cache question. It stays in `backlog.md` as its own block.

## Steps

1. **`core/ui/image/ImageBitmapCache.kt` (edit)** — a private
   `MAX_ENTRY_BYTES: Long get() = budgetBytes / 2` and an early return in `set`:

   ```kotlin
   operator fun set(key: String, bitmap: ImageBitmap) = synchronized(lock) {
       if (bitmap.byteSize > MAX_ENTRY_BYTES) return@synchronized
       …
   }
   ```

   **Half the budget, and the comment says why**: an entry that alone can evict more than half the
   cache is the pathology, and at the 256 MB default that admits a 24 MP photograph (96 MB) and
   refuses a 36 MP one (138 MB) and a 100 MP one (384 MB). A fraction rather than a constant, so a
   user who raises `woka.imageCacheBytes` raises the ceiling with it. The file is at **149 lines**, so
   if `wc -l` goes past 150 the split is `byteSize` and its `config` table into a
   `core/ui/image/BitmapBytes.kt` sibling, as its own step.

2. **`core/ui/image/ImageBitmapCache.kt` (edit)** — `remove` and `removeAllFor` are unchanged and must
   stay so: a key that was never admitted is already absent, and both already tolerate that.

3. **Nothing else changes.** `ImageLoader`, `StillPreview`, `MediaThumbnails` and `FrameThumbnail` all
   keep writing to the cache exactly as they do; the entry that is too large is simply not there when
   they look again. Listed as a step because "no call site moves" is the claim being made.

## Files

`shared/src/jvmMain/kotlin/com/woka/core/ui/image/ImageBitmapCache.kt` (edit);
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and the status table);
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit — the block is replaced by this
ticket and by the off-thread-decode block it leaves behind).

## Verification

- `jvmTest/integration/ImageCacheEvictionTest.kt` (edit), under `withImageCacheBudget` so the ratio is
  tested rather than the megabytes:
  - with a 4 MB budget, an entry over 2 MB is **not** stored — `get` answers null straight after `set`;
  - and the entries that were already there are still there, with `retainedBytes` unmoved. That second
    assertion is the ticket: today the huge entry is admitted, everything else is evicted, and
    `retainedBytes` still exceeds the budget;
  - an entry just under half the budget is stored as it always was;
- `jvmTest/integration/ImageLoaderTest.kt`, `ThumbnailCacheTest.kt`, `GridCoverTest.kt`,
  `FrameCacheKeyTest.kt`, `SaveEvictionTest.kt` — unchanged and still green: an ordinary photograph is
  far below the gate and nothing about it moves.

## Done when

- an entry larger than half the budget is refused, and refusing it evicts nothing;
- an ordinary photograph, a preview and a poster frame all still cache exactly as they did;
- no call site outside `ImageBitmapCache` learned about the gate (`grep -rn "MAX_ENTRY" shared/src`
  hits one file);
- the off-thread decode is written into `backlog.md` as its own block, naming ME-010's 667 ms;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
