# A folder card should read as a folder

| | |
|---|---|
| **Requested** | 2026-08-30, by hand, from a screenshot |
| **Area** | `core/ui/components/FolderCard*` — Home, the folder grid, Search |
| **Depends on** | the borrowed cover (`pending/folder-of-folders-has-no-cover.md`), which is what made this visible |

**Goal.** Make a card that stands for a **folder** distinguishable at a glance from a card that stands
for a **picture**, now that both of them draw a photograph.

**Why now.** Until 2026-08-30 a folder of folders had no cover, so it drew the folder glyph and said
what it was by accident. Giving it a borrowed cover from one level down fixed the blank tile and
removed the only thing on the card that said "folder". A grid of photographs where some of the
photographs are folders is worse than the bug that was fixed.

**The author decides the look.** This ticket states the constraints and lists the candidates with what
each costs; it deliberately does **not** choose. Per `code-style/composable.md` the geometry is settled
in `ai/mockups/design/FINAL_design_mockup.html` **first**, and the Kotlin follows it.

## What the card already does, and what it is allowed to do

- **The cover is one picture, calm.** `FolderCardPreview`'s KDoc records that a four-tile mosaic was
  tried and removed: it "multiplied six folders into twenty-four competing photographs — nothing was
  background, so nothing was foreground either". A mosaic is **not** available as an answer here.
- **The stack edge already exists.** `FolderCardBody` draws two strips under the tile for folders only
  — `StackEdge(inset = 8.dp)` and `StackEdge(inset = 15.dp, alpha = 0.55f)`, 4 dp tall, in
  `Theme.colors.surfaceElevated`. This is the current answer to "there is more inside", and on a light
  surface it is nearly invisible. **It may simply be under-drawn rather than wrong.**
- **The word "Folder" is gone on purpose.** `FolderCardBody` removed the title row because it made the
  word repeat down a screen made of folders. Re-adding a text label argues with a settled decision and
  must be justified, not assumed.
- **A badge is an established pattern.** A video already gets `PlayBadge`, centred, drawn **above** the
  rest-state dimming so an unhovered card stays readable.
- **A folder accent already exists**: `Theme.colors.accentFolders`, used by the dock's Library item and
  the Home context menu. Nothing on the card uses it.
- **`FolderCardStyle` already knows** — `rememberFolderCardStyle(..., isDir = data.isDir)` takes it, so
  a shape or colour that differs by kind needs no new plumbing.

## Candidates

1. **Draw the stack edges properly.** The idea is right and the execution is faint: raise the contrast,
   or tint them `accentFolders`, or give them the card's border rather than a fill. Cheapest by far,
   changes no layout, and keeps the metaphor the card already chose. **The one to try first.**
2. **A corner tab.** A folder has a tab; a photograph does not. Drawn into the cover's clip so the tile
   silhouette itself differs. Strongest signal, and the only candidate that survives a card scaled down
   to a thumbnail — but it costs a custom `Shape`, and a tab that reads as a rendering artefact at
   small sizes is worse than no tab.
3. **A folder badge on the cover**, the `PlayBadge` treatment. Consistent with something the app
   already does, trivially readable, and above the dimming for free. Cost: a second glyph competing
   with the caption on a tile that is already carrying a name and a count.
4. **A different corner radius or border for folders.** Quietest of the four and almost free. Probably
   too quiet to answer the complaint on its own; a reasonable *addition* to 1.
5. **Inset the cover.** Draw the borrowed picture slightly smaller than the tile so the folder's own
   body is visible behind it — the card *contains* a picture rather than *being* one. Elegant, and it
   is the one that says "borrowed from inside" rather than "this is the folder's photograph".

**Not a candidate: the glyph instead of the cover.** That is the state this ticket exists to move on
from, and the blank-looking tile it produced is what was reported.

## Files it would touch

`core/ui/components/FolderCardBody.kt` (the stack edges, the tile's shape),
`core/ui/components/FolderCardPreview.kt` (a badge or an inset cover),
`core/ui/components/FolderCardStyle.kt` (anything that differs by `isDir`),
`ai/mockups/design/FINAL_design_mockup.html` (**first**).

## Verification

- `jvmTest/ui/FolderCoverUiTest.kt` (edit) — whatever mark is chosen, it is present on a folder card
  carrying a borrowed cover and absent on a picture card; a `testTag` on it, because "reads as a
  folder" is not something a UI test can see;
- `jvmTest/ui/FolderGridUiTest.kt`, `SearchResultsList`'s cards — unchanged and still green;
- **by hand, in both themes.** This is a contrast question and every previous one in this project —
  the `onScrim` tokens, the rest-state dimming, the glyph under it — was settled by looking, not by a
  green build.

## Done when

- a folder and a picture are told apart at a glance in the Home grid, in both themes;
- the answer is in the mockup before it is in Kotlin;
- an ordinary picture card is unchanged;
- `./gradlew.bat :shared:build` green.
