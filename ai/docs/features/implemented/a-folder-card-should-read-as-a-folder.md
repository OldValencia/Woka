# A folder card should read as a folder

| | |
|---|---|
| **Requested** | 2026-08-30, by hand, from a screenshot |
| **Resolved** | 2026-08-31 — candidate 1, painted properly, plus a fan on hover |
| **Area** | `core/ui/components/FolderCard*` — Home, the folder grid, Search |
| **Depends on** | the borrowed cover (`folder-of-folders-has-no-cover.md`), which is what made this visible |
| **Mockup** | `ai/mockups/design/folder-card-stack.html` — both themes, four variants, two screens |

## Resolved 2026-08-31 — the answer is the stack edge, drawn as it was specified

The mark is **variant C**: the two sheets keep their own colours instead of the tile's, and each is
outlined on three sides. On top of it, the sheets **fan out under the cursor** and are otherwise
motionless, so a Home screen made entirely of folders pays nothing at rest.

Chosen by eye from the mockup, in both themes. What follows the original text below is the record.

**Goal.** Make a card that stands for a **folder** distinguishable at a glance from a card that stands
for a **picture**, now that both of them draw a photograph.

**Why now.** Until 2026-08-30 a folder of folders had no cover, so it drew the folder glyph and said
what it was by accident. Giving it a borrowed cover from one level down fixed the blank tile and
removed the only thing on the card that said "folder". A grid of photographs where some of the
photographs are folders is worse than the bug that was fixed.

**The author decides the look.** This ticket states the constraints and lists the candidates with what
each costs; it deliberately does **not** choose. Per `code-style/composable.md` the geometry is settled
in a mockup **first**, and the Kotlin follows it.

> **This ticket named the wrong mockup**, and the correction is part of the record.
> `FINAL_design_mockup.html` contains no card at all — no `.card`, no `.stack`, no `.cover`. The card
> was specified in `home-proposal.html:125-167`, and that file is dark-theme only, so it could not
> settle a contrast question. `folder-card-stack.html` was written for this ticket instead, per
> `feature-docs.md:94`: both themes in one file, toggled, so the dark theme is designed and not
> derived.

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

## What was done

1. **`ai/mockups/design/folder-card-stack.html` (new)** — the spec. Both themes on the real tokens,
   four ways to paint the sheets side by side, and the same four on two screens: a Linked Folder that
   mixes folders with pictures, and Home, where every tile is a folder. Three additions to C were
   offered as independent switches — an inset cover, a fan on hover, a warm `accentFolders` outline —
   and **one** was taken. Piling on all three would have been the mosaic's mistake in new clothes:
   four simultaneous ways of saying "folder" leave nothing as background.

2. **`core/ui/theme/Theme.kt`** — three fields on `Colors`, both palettes:

   | | Dark | Light |
   |---|---|---|
   | `stackEdge` | `#1A1E27` | `#E9E9EF` |
   | `stackEdgeFar` | `#141821` | `#DEDEE6` |
   | `stackEdgeLine` | `0x12FFFFFF` (7 %) | `0x17000000` (9 %) |

   Deliberately card-adjacent and not canvas-adjacent: a sheet that reaches the background colour
   stops reading as a sheet and becomes a hole. The far outline is `stackEdgeLine` at 70 % of its own
   alpha, which is why it is not a fourth token.

3. **`core/ui/components/FolderCardStack.kt` (new, 124 lines)** — the sheets, split out of
   `FolderCardBody` under the §2 file-size mandate, which the path drawing pushed past 150.
   `FolderCardBody.kt` went 102 → 86 lines and now calls `FolderCardStack(isHovered)`.

## Two things that cost a build each, kept because both were invisible until drawn

**The sheets were filled with the tile's own colour, and the original mockup could not have shown it.**
`FolderCardBody` painted both in `surfaceElevated` with no outline — white on `#F2F2F7` in the light
theme, the tile colour repeated in the dark. `home-proposal.html:131-133` had always specified
distinct fills *and* a `border-top:0` hairline. It was never seen, because those pseudo-elements sit
at `z-index:-1` and `.card` creates no stacking context, so inside the mockup's own `.app` frame —
which paints an opaque background — the sheets were drawn underneath it and disappeared. The same
trap is still in `home-proposal.html`; `folder-card-stack.html` fixes it with `isolation:isolate` on
the card, and says so in a comment.

**The fan is free in CSS and is not free in Compose.** Two sheets as real boxes, each sized to its own
protrusion, grow the card by 4.dp on hover — and height is layout, so every row below shifts and the
grid wobbles when you point at a folder. The mockup never showed this either: absolutely positioned
pseudo-elements take no space in the flow. `FolderCardStack` therefore reserves the fanned height
(12.dp) at all times in **one** box and animates only what `drawBehind` paints. The cost is 4.dp of
transparent space under a folder card at rest; nothing measures the card's height, so nothing else
moved.

**The outline is a `Path`, not a `border`.** `Modifier.border` strokes all four sides and the top one
is not optional here: on a sheet 4.dp tall a hairline top *and* bottom leaves 2.dp of fill, and the
sheet becomes a contour. One open three-sided path is filled (a fill closes across the top
implicitly) and stroked (a stroke does not). The corner radius is clamped the way
`RoundedCornerShape` clamps its own — 9.dp cannot exceed a 4.dp sheet.

## Verification

- `jvmTest/ui/FolderStackUiTest.kt` (**new**, not an edit of `FolderCoverUiTest` as planned — that
  file reached 161 lines and the mandate won). The stack is present on a folder card **carrying a
  borrowed cover** and absent on a picture card. The fixture has a picture in the folder on purpose:
  unlike the glyph it replaced, this mark does not depend on the cover, and that independence is the
  whole point.
  **`useUnmergedTree = true` is load-bearing in both cases.** The card's outer `Box` is clickable and
  merges its descendants' semantics into itself; without the flag the folder case fails outright and
  the picture case passes for the wrong reason — reading the merge, not the absence. The first build
  found this;
- `jvmTest/ui/FolderCoverUiTest.kt` — untouched behaviour, still green, still 114 lines;
- `jvmTest/ui/FolderGridUiTest.kt` and the `SearchResultsList` cards — unchanged and green;
- `./gradlew.bat :shared:build` — green;
- **by hand, in both themes**, which is what settles a contrast question. Worth another look at 125 %
  and 150 % display scaling specifically: a 1.dp stroke on a 4.dp sheet is the "artefact at small
  sizes" that this ticket used as its argument against the corner tab.

## Done when

- a folder and a picture are told apart at a glance in the Home grid, in both themes — **done**;
- the answer is in the mockup before it is in Kotlin — **done**, and the ticket's own pointer to
  `FINAL_design_mockup.html` was wrong; corrected above;
- an ordinary picture card is unchanged — **done**, and asserted;
- `./gradlew.bat :shared:build` green — **done**.

**Not built, and left on the table** in `folder-card-stack.html` should the stack ever prove not to be
enough: the inset cover (candidate 5) and the warm `accentFolders` outline. Rejected by hand: the
corner tab (2) and the badge over the cover (3).
