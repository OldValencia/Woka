# Woka: Development Roadmap

Woka is a minimalist, aesthetic (Apple HIG style) desktop media viewer and editor for JVM (Compose Multiplatform), focusing on a curated gallery experience through "Linked Folders".

## 📍 Architecture & CLI Routing

The app entry point (`main(args)`) routes based on CLI arguments — implemented in
`app/navigation/StartupRoute.kt`, applied by `initKoin(launchArgs)` before the first composition,
and asserted row by row by `StartupRouteTest`:
- `./gradlew.bat :desktopApp:run` ➡️ Opens **Home Screen**.
- `./gradlew.bat :desktopApp:run --args="D:\Path\To\Folder"` ➡️ Opens **Folder View** (Grid of media in that folder).
- `./gradlew.bat :desktopApp:run --args="D:\Path\To\File.jpg"` ➡️ Opens **Viewer** directly.
- A path that is not there, or a file the app cannot show, falls back to Home and to the file's
  parent folder respectively. A launched folder offers itself for pinning to Home.

---

## 📝 Execution Phases

### Phase 1: Home Screen & Linked Folders (Foundation)
- **UI:** Clean empty state with a prominent `+ Link Folder` button.
- **Data Storage:** Settings and linked folder paths are stored locally in `user.home/Documents/OldValencia/Woka/` (or similar standard documents directory).
- **Logic:** OS Folder Picker dialog to select folders to pin.
- **Result:** Home Screen displays a grid/list of "Linked Folder" cards (showing name and a cover image or item count).

### Phase 2: Folder View & Subfolder Navigation (Grid)
- **UX:** Clicking a linked folder opens the Grid view of its media.
- **Subfolders:** Support for subfolders *within* the linked folder. Users can navigate into subfolders to view and organize files hierarchically.
- **UI:** Breadcrumb navigation (e.g., `Home > My Photos > Summer 2026`). Adaptive Grid of square thumbnails.
- **Logic:** Async scanning for `.jpg`, `.png`, `.mp4`. Generation and caching of thumbnails to ensure smooth scrolling.

### Phase 3: UI/UX Redesign & Polish
- **Goal:** Transform the initial "draft" UI into a premium, macOS/Apple HIG-inspired desktop experience before building complex features.
- **Top Bar:** Unify the title bar, breadcrumbs, search, and action buttons into a sleek, blur-backed native-feeling toolbar.
- **Grid & Cards:** Refine folder and media cards with proper corner radii, smooth hover animations, selection states, and glassmorphism elements.
- **Typography & Spacing:** Adjust fonts (weight, size, color hierarchy) and padding to ensure a clean, uncluttered, professional aesthetic.
- **Empty States & Settings:** Polish the Settings overlay and empty folder views with engaging graphics or subtle instructions.
- **Package restructure (done):** moved from layer-first to feature-first (`app/`, `core/`,
  `feature/<name>/`) per `features/implemented/package-restructure-feature-first.md`.
- **Code-quality refactor (done):** `features/implemented/code-quality-refactor.md` — a media domain
  type, one storage root, viewer chrome tokens, overlay/slider deduplication, real events instead of
  timestamp triggers, collapsed parameter lists, `ViewerMedia`, the player engine split into
  sub-packages with its cross-thread handshake named as `PlaybackGeneration`, a runtime-cost pass,
  and the 130–150 line mandate closed across the whole tree. Fixed on the way: disagreeing extension
  lists, a thumbnail cache keyed on file *name*, events colliding inside one clock tick, and a seek
  that signalled before it wrote its target.
- **Viewer verification pass (done):** `features/implemented/viewer-verification-pass.md` — test
  pyramid built (172 unit / 167 integration / 42 UI automation, `:shared:build` at 383 checks) plus a
  manual pass, run twice. That pass lived in `ai/docs/testing/manual/` and has since been read,
  acted on and **retired** — a by-hand result is now dated in the ticket that asked for it
  (`conventions/feature-docs.md` §7). Found and fixed on the way: a PiP drag that
  panned the video and then registered as a click, no CLI routing at all, and a grid "Copy" that
  meant copy-into-a-folder.

### Phase 4: The Viewer (Preview & Carousel) — **verified**
Verified end to end by `features/implemented/viewer-verification-pass.md`: the four-layer test pyramid
plus a manual pass, since retired. The "Direct OS Launch Behavior" below is built as
of that pass; the rare formats are the one part still outstanding.

- **UX:** Clicking a media file in the grid dims the background and opens the Viewer overlay.
- **UI:** Image centered, bottom carousel (context of the current folder), top toolbar (Title, Zoom, `X` button).
- **Direct OS Launch Behavior (Double Click):** 
  - If a file is opened directly from the OS, the Viewer opens.
  - Clicking `X` does **not** close the app. Instead, it transitions to the **Folder View** of that file's parent folder.
  - If the parent folder is not currently linked, the app presents a prompt/button: "Pin this folder to Home Screen?".
- **Logic:** High-quality rendering via Skia, zoom (scroll), pan (drag), and video playback via JavaCV.
- **Picture in Picture:** the fullscreen window shrinks to a corner box from its toolbar or by an edge
  drag, and stays above other apps while it is that size — `pipAlwaysOnTop`, on by default, switchable
  from the window's right-click menu and from Settings → PiP.
- **Rare image formats:** `features/pending/rare-image-formats-and-frame-sets.md` — HEIC/HEIF and
  AVIF decoding, one "frames inside a file" mechanic for bursts, Live Photos and animated
  GIF/WebP alike, and HDR tone mapping for stills.
- **Animated stills:** `features/implemented/animated-stills.md` — that mechanic *plays* an animation
  rather than only counting its frames. **Built 2026-08-20** in the viewer: a GIF or animated WebP
  runs at the speed it declares, loops as it says to, and a click pauses it. The grid still draws
  frame 0 and that is its own decision (phase 4 of the plan). It unblocked the image editor's GIF
  carrier, ME-138, which shipped the next day. Moved to `implemented/` 2026-08-24.

### Phase 5: Media Editor (Editing tools)
Two folders since 2026-08-24, one per shipping half. **5a — the image editor — is complete and moved
to `features/implemented/image-editor/` on 2026-09-03.** `features/pending/video-editor/` is **5b**.

**5b was re-scoped and restructured on 2026-09-03**, against a brief several times the size of the
four blocks it held: as well as the trim, the author asked for cutting a piece out of the middle and
moving it elsewhere, seamless playback across the joins, four audio behaviours, the image editor's
five tools reused on a moving picture, and two ways out to a file. It is now **seven feature folders
under one umbrella with one shared backlog** — `foundation/`, `timeline/`, `playback/`, `audio/`,
`picture/`, `save/`, `render/` — and `video-editor.md` is the umbrella that holds the architecture and
the decision log.

**`foundation/` shipped on 2026-09-03** — ME-201, ME-206, ME-207 and ME-205, confirmed on screen —
and moved to `features/implemented/video-editor/foundation/`. **A finished folder leaves the pending
tree on its own**, which is this feature's own rule: it ships in seven parts, and a part that is done
belongs with the shipped work. A video now opens in the editor, draws through the folded document,
stands still until asked to move, carries a band and tone maps correctly; ME-208 was dropped because
the author chose `save/` as the folder after it. The ticket IDs stay in the `ME-`
namespace, banded one range per folder inside the 200s, because the prefix means *Media Editor* and
spans the whole phase; ME-202…ME-204 are retired, their ids kept and never reused.

The claim below that "the video editor adds a tool set, an engine and a saver and changes none of the
shell" **survived being read against the code** and is still true of the shell: what it understated is
the size of the tool set. The one seam the shell grows is a defaulted `Timeline` composable, following
`Thumbnail`'s precedent.

**5a is done: no open tickets, and its backlog holds only the things deliberately not built.** A
shell, a rail, twelve tools, a recipe carrier per container family, a per-frame edit, an HDR path
that tone maps at the decode, and an HDR tool that can bake to SDR. The feature doc's status table
is where the ticket-by-ticket record lives; this paragraph deliberately does not restate it.

Two things are named there as gaps rather than tickets — what a save in flight does when the app
closes, and localisation — and the 600 track (four further adjustments) was **declined by the author
on 2026-09-03**, not deferred.

**The foundation is built** — ME-001…ME-014 all done as of 2026-08-18: the document, history and
session; the parameter schema and tool contract; the registry with settings-backed enablement; the
raster fold; the engine seam; the capability probes; the test harness and fixture tool; the visual
spec and its tokens; and `material3` removed. Every one is shared by both editors and provable
without a window. ME-009's save targets were part of it and were **deleted by ME-132**, which
replaced the whole export path.

- **UX:** `Edit` in the viewer's control pill and in the grid's context menu.
- **Features:** Crop, resize, rotate/flip, adjustments, filters, HDR on/off, vector markup (arrows,
  shapes, text) — all shipped in 5a and all reused on video. On video, additionally: trim, split,
  delete and reorder within one file; per-clip speed and freeze-frame; per-clip and whole-file audio
  with detach, an external track and extraction; markup that appears, holds and fades.
- **Logic:** an immutable `EditDocument` of tool ops, folded into one draw recipe; the same recipe
  replayed at native resolution on save. **The recipe is written into the file itself** (ME-131…ME-135)
  and every reader replays it, so `Done` edits the picture in place — there is no copy, no export
  step and no hidden backup store. "Save a Copy is the default" was the 2026-08-16 decision and was
  reversed on 2026-08-19. **On video the same recipe carries the cuts as well**, and `Done` grows a
  second entry — `Render video…`, which writes a real file anybody can play, copies every segment it
  can and never overwrites the source. The copy comes back for that one path, because a lossy
  overwrite is irreversible.

### Phase 6: OS Integration
- Register Woka in Windows Registry to handle `.jpg`, `.png`, `.mp4` file extensions.
- Add context menu entry for directories: `Open with Woka`.
- (Future) IPC/Integration with the Echo clipboard manager.
