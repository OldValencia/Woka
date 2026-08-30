# Bug — a folder containing only folders draws no cover

| | |
|---|---|
| **Reported** | 2026-08-30, by hand |
| **Area** | `core/ui/components/FolderCard*`, Home and the folder grid |
| **Feature** | none — the folder card shipped with `implemented/media-viewer/phase2-folder-view.md` |

**What happens.** Link a folder whose contents are *only* subfolders of pictures. The subfolders'
cards draw their covers normally. The outer folder's card draws **nothing at all** — a black tile,
with not even a folder glyph on it.

**What it should do.** At minimum the folder glyph, which is what `FolderCardPreview` already draws
for a folder it has no preview for. Better, and the reason this is worth a ticket rather than a
one-line fallback: a folder of folders is a real shape in a Linked-Folders app, and its card should
say what is inside it.

## Resolved 2026-08-30 — see the findings below

Both halves are implemented and `./gradlew.bat :shared:build` is green. What follows is the record of
how the cause was found, kept because the first reading of it was wrong.

## What is measured, and what is not

**Measured — the cover list is empty by construction.** `FolderCardUtils.fetchFolderStateData` builds
`previews` with

```kotlin
val previews = allFiles.mapNotNull { f ->
    if (!f.isFile) return@mapNotNull null
    …
}.take(1)
```

so a directory holding no *files* yields an empty list. Nothing walks one level down. That alone
explains "no cover"; it does not explain "black".

**Measured second — the glyph is in the tree, and always was.** `FolderCoverUiTest` was written as the
instrument rather than as a regression guard, and it answers headlessly with no application run:
`FolderCardPreview(isDir = true, previews = emptyList())` puts `FOLDER_GLYPH_TAG` on screen and
`assertIsDisplayed` passes. So the fallback branch is innocent, and three of the four hypotheses above
— `isDir` false, a different composable, an unread `previews` — are dead.

**Measured third — the glyph was also drawn *under* the rest-state dimming.** Every
other piece of card chrome is drawn after it, and `FolderCardPreview` says why in a comment of its
own — *"the glyph and the progress bar are chrome, and a card the cursor is not on still has to be
readable"* — but that comment sat above `PlayBadge` and `FolderCardProgress` only. The folder glyph
was inside the cover branch, so an unhovered card drew it at **0.5 alpha beneath a further 18 % of
black** (`REST_DIM`) on `surfaceElevated`. In the dark theme that is a barely-there outline on a dark
tile, which is exactly "a black tile with nothing on it". Not a missing glyph — an unreadable one.

**Measured fourth: a card that *has* a cover and shows nothing.**
`StillCoverImage` has three branches that draw **nothing at all** — a Woka-road file (`heic`, `heif`,
`avif`, `tif`) whose `loadStillCover` answered null, one whose road is still being decided, and a
decode still running — and `FolderCardPreview` only reached the glyph when `cover == null`. So a card
that could not paint its cover had neither picture nor glyph. Fixed with a `placeholder` slot on
`StillCoverImage`, defaulted to nothing so every existing caller is unchanged, and the folder card
passes its glyph.

**Measured fifth, and this is what the screenshot was actually of** (the author named the contents,
2026-08-30). `Pictures` holds **five subfolders, one fully transparent PNG and one hidden file**. The
transparent PNG decodes perfectly and paints nothing, so:

- `coverHere()` accepted it, being the first *file* — the subfolder scan never ran;
- the tile drew the card's own surface and read as a broken thumbnail;
- and the glyph stayed away, because the card had a cover.

None of the findings above touch that: the cover was neither missing nor undecodable. **The
picture had nothing in it, and nothing asked.**

## What was done

1. **The glyph moved above the dimming**, beside `PlayBadge`, and out of the cover branch into its own
   `FolderGlyph` composable. The comment that explains why chrome sits there now covers it too.
1b. **`StillCoverImage` gained a `placeholder` slot**, drawn on each of its three no-picture branches
   and laid out centred and filling by `CoverPlaceholder` — the picture it stands in for filled the
   tile, and a glyph left at the parent `Box`'s default alignment lands in a corner. Defaulted to
   nothing, so a photograph tile is byte-for-byte what it was. The folder card passes its glyph, which
   is what closes the blank tile for a cover that will not decode.
2. **A folder of folders borrows a cover**, and **the cover has to have something in it.** The choice
   moved to `core/ui/components/FolderCoverChoice.kt`, where it stopped being "the first file that is
   an image" and became a small policy:

   - candidates are collected from the files lying directly inside, and only if there are none, from
     one level down — subfolders sorted by name, at most `COVER_SCAN_LIMIT = 8` opened. Sorted because
     `listFiles` answers in filesystem order and a cover that changes between launches is worse than
     no cover; capped because a card is a cover, not a search;
   - **hidden files are skipped.** On Windows a hidden file is a normal inhabitant of a user's
     Pictures folder and is never what the folder is about;
   - up to `COVER_CANDIDATES = 4` are kept, and the first that is **not** a flat expanse of one colour
     wins. `looksBlank` compares an 8×8 grid of samples — 64 reads against 160 000 for a 400 px
     preview, and any photograph disagrees with itself within the first few. A decode that fails
     counts as blank: nothing to draw and nothing drawable are the same answer to the only question
     the card is asking;
   - if every candidate is blank, there is no cover and the glyph is the honest answer.

   **The cost this adds, stated plainly.** `fetchFolderStateData` did no decoding at all; it now
   decodes one preview per folder card, and up to four for a folder whose pictures are blank. It runs
   on `Dispatchers.IO` behind `folderStateCache`, and `StillPreview` writes a 400 px copy to disk, so
   it is paid once ever per file rather than once per launch.

**One level, deliberately.** Two would make an arbitrary directory tree a thing the grid walks, and
the depth at which a picture stops representing a folder is not a number anyone can defend.

**A mosaic was rejected**, and not by this ticket: `FolderCardPreview`'s own KDoc records that the
four-tile mosaic was removed because it "multiplied six folders into twenty-four competing
photographs". `FolderCoverMosaic` is still named in `CLAUDE.md`'s layout map and **does not exist in
the tree** — checked 2026-08-30. The map is what is wrong there.

**Known limitation, accepted.** `folderStateCache` is keyed on this directory's mtime plus the
thumbnails directory's, and a picture added *inside a subfolder* moves neither — so a folder that
would gain its first borrowed cover keeps the glyph until something else changes. It is a cover, not
data, and the alternative is stat-ing every subfolder on every card.

## Verification

- `jvmTest/integration/FolderCoverTest.kt` (new), over **real encoded images** from
  `support/ImageFixtures.kt` rather than junk bytes, because the question is now what a picture
  *contains* — a folder of pictures still covers itself with the first of them; a folder of folders
  borrows from the alphabetically first subfolder that has one; a fully transparent picture and a flat
  one are both passed over; **blank files inside do not stop the subfolder scan**; a folder of nothing
  but blank pictures offers no cover; a picture **two** levels down is not borrowed; an empty folder
  still offers nothing; and the caption's count is unmoved by the borrowing;
- `jvmTest/ui/FolderCoverUiTest.kt` (new) — the glyph is displayed for a folder with no preview,
  including on a card the cursor is not on; **it is displayed for a folder whose cover cannot be
  decoded** (a `.heic` of junk bytes, which takes the Woka road from its extension and fails on it);
  and absent the moment there is a picture instead. **Its layer order is not asserted**: the semantics
  tree does not expose one, and a test named for it would claim more than it checks.

## Done when

- a folder whose only picture has nothing in it shows one from a subfolder instead — **done**;
- a linked folder holding only subfolders shows a picture from inside it — **done**;
- a folder with nothing to show anywhere draws a readable glyph — **done**;
- the reason it was black is written down, not worked around — **done**, above;
- an ordinary folder of pictures draws exactly the card it drew before — **done**, first test;
- `./gradlew.bat :shared:build` green — **done**.

**Confirmed by hand 2026-08-30**, which is what settles the one claim the headless tests cannot make:
that the tile reads correctly in the dark theme at rest.
