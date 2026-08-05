# TC-PERF — long runs and large files

Everything here needs either real time or real size. The suite runs in seconds against a 3.3 MB
corpus, which is the wrong instrument for all of it.

---

### TC-PERF-01 — ten minutes without drift

**Area:** playback, A/V sync
**Preconditions:** a film or episode of at least 15 minutes with speech, 1080p or better. Task
Manager open on the Woka process.
**Steps:**
1. Note private working set at the start.
2. Play for ten minutes without touching anything.
3. Check lip sync at 1, 5 and 10 minutes.
4. Note the working set again.
**Expected:** lip sync holds at every check — no accumulating lead or lag. Memory settles rather than
climbing steadily; a slow monotonic rise over ten minutes is a defect worth recording with numbers.
**Why this is not automated:** the failure is a drift measured in real minutes. `VideoSeekTest`
covers a seek landing correctly; nothing in the suite runs long enough for drift to exist.

---

### TC-PERF-02 — a very large image does not freeze the UI

**Area:** viewer, still images
**Preconditions:** an image of at least 8000 px on its long edge.
**Steps:**
1. Open the containing folder.
2. Open the image in the viewer.
3. While it loads, move the mouse and try to press `Esc`.
4. Once loaded, zoom and pan.
**Expected:** the window keeps responding while the decode runs — chrome reacts, `Esc` is not
swallowed. No frozen paint. Once open, zoom and pan are smooth.
**Why this is not automated:** "the UI stayed responsive during a slow decode" is a statement about
the main thread over a real interval.

---

### TC-PERF-03 — holding the arrow key does not pile up loads

**Area:** viewer, navigation
**Preconditions:** a folder of at least 40 mixed images and videos, thumbnail cache cleared.
**Steps:**
1. Open the first item in the viewer.
2. Hold the right arrow for five seconds and release.
3. Wait, and watch what settles.
4. Hold the left arrow back to the start.
**Expected:** paging keeps up, or falls behind gracefully and catches up. On release the viewer
settles on the file it stopped at and shows *that* file — not one from part way through the run
arriving late. No queue of stale decodes overwriting the current frame afterwards.
**Why this is not automated:** the defect is a race between key repeat and an async loader, and it
only appears at real key-repeat rates.

---

### TC-PERF-04 — the retained-memory measurement for F1

**Area:** `ImageBitmapCache` · **produces the input for the F1 decision**
**Preconditions:** a folder of at least 30 images of 4000 px or more. Task Manager, or JVM flight
recording if available.
**Steps:**
1. Start Woka and note the working set.
2. Open the folder and page through every image once in the viewer.
3. Note the working set.
4. Page through a second time and note it again.
5. Return to Home, wait a minute, note it once more.
**Expected:** *record the four numbers.* The expectation today is that memory rises roughly with
total decoded pixels on the first pass, does **not** rise on the second (everything is cached), and
does **not** fall on return to Home — `ImageBitmapCache` is a `ConcurrentHashMap` with no eviction.
**Why this is not automated:** F1 is an open decision — an LRU by entry count, an LRU by total
pixels, or separate caps for grid posters and full-size viewer frames. The choice needs a number for
what "unbounded" actually costs on this machine with real photos, and there is no expected value to
assert against until Valik picks a policy. Once picked, `ThumbnailCacheTest` gets the automated
half and this case keeps only the measurement.

---

### TC-PERF-05 — a folder of a thousand files

**Area:** Folder View, listing and thumbnails
**Preconditions:** a directory with ≥ 1000 media files.
**Steps:**
1. Open it from Home.
2. Scroll to the bottom immediately, then back to the top.
3. Change the sort while thumbnails are still generating.
4. Enter select mode and use select-all.
**Expected:** the listing appears without a long freeze; thumbnails fill in progressively rather than
blocking the grid. Scrolling stays smooth with generation in flight. Changing the sort re-orders
without discarding thumbnails already made. Select-all does not stall the UI.
**Why this is not automated:** `FolderViewModelTest` covers listing and sorting on a handful of
files. Behaviour at a thousand is about scheduling and scroll performance.
