# Home rethink

**Status: designed, nothing built.** The problem statement is `idea--rethink-home.md` beside this
file. The visual spec is `ai/mockups/design/rethink-home.html` — once a UI ticket is written,
disagreements are settled by changing the mockup first.

## The problem

Home is a grid built for a library of many linked folders. In practice a person links one or two,
once, and never opens the list again. The screen then costs a click at every launch and shows
nothing that changed since yesterday, and the dock carries a destination — Library — that lights up
in places you cannot be and does nothing when clicked.

## What Home becomes

**Home is the library, not a list of shortcuts.** Two zones on one screen:

- **Linked folders** — the roots. Few, stable, ordered by hand.
- **Pinned** — folders *inside* those roots, where the user is currently living. Several, changing
  weekly, ordered by hand.

Half the screen therefore moves week to week, which is what makes it worth opening. Both concepts
already exist: `PinnedSession` already stores a folder inside a root together with the scroll
position it was left at, and it is already created from inside a folder as well as from Home. It has
simply never been shown on the screen that is supposed to be the library.

The card itself does not change. What changes is the screen around it: sections, composition for
small counts, an add tile, and a dock with one less lie in it.

## Opening a folder at launch

A **flag on a folder**, not a position in the grid: one stored path, empty by default, empty meaning
Home. It is set from the card's context menu and from a hollow badge in the card's corner that
appears on hover, and the card wears the badge while it holds the flag. Setting it on one card
clears it from every other.

An earlier draft made the *first grid position* mean "opens on launch". Rejected: it does not
survive a library of one — the only card is always first — and it would let dragging a card for
looks silently change what opens tomorrow. Ordering is therefore cosmetic everywhere, in both zones,
and carries no second meaning anywhere.

At launch the stored path is checked. A folder that has moved or been deleted opens Home and the
flag is dropped, rather than raising anything. Home stays beneath the launched folder in the stack,
exactly as a command-line launch already does, so Back and the dock's Home item behave identically
either way.

## The dock shrinks to four

**Link, Home, Search, Settings.** The Library item is removed, not repaired: it highlighted while
the user was in a folder — a state that does not exist, since you are in a folder and never "in the
library" — its hover popup listed a different set of folders from Home's, and its click was wired to
nothing. Once pinned folders are on Home, the popup holds nothing Home does not say better, and Home
is one click away in the same dock.

What is left is the action that builds the library, the library, and the two items that are not
navigation at all. If the dock earns a fifth item later it will be because something needed a home,
not because a row looked empty.

## Linking, and unlinking

Linking gets three ways in, one action behind them: the tile in the grid (last, always visible), the
dock's `+`, and **a folder dragged in from Explorer**. The third does not exist anywhere in the app
today — there is no OS drag-and-drop in the codebase at all — though the vision document names
drag-and-drop a desktop pillar. It is the only route that works for someone who never opens a
context menu. Dropping a *file* is the quick-preview case and stays as it is.

Unlinking is destructive in a way the UI does not admit: it fires immediately from the card menu and
from the hover `×`, and the mutation behind it sweeps every record under that path prefix — watch
history and the chosen audio and subtitle tracks for the whole tree. It gets an undo banner for a
few seconds rather than a confirm dialog: a dialog punishes the common case to guard the rare one.

## What this deliberately does not add

- **No recents.** Linked folders and pinned places are the two lists that already exist.
- **No launch dropdown in Settings.** The flag lives on the object it affects.
- **No inference.** Nothing opens because of what happened yesterday; every launch was placed there
  on purpose.

## Status

Three chunks rather than a queue of small ones — one person builds this, and each is a sitting that
ends with something that can be looked at.

| ID | Ticket | Status |
|---|---|---|
| HOME-001 | Home becomes the library — both zones, drag, the Link tile, the first run, the composition | not started |
| HOME-002 | Launch, and a dock that tells the truth — the flag, and Library removed | not started |
| HOME-003 | Linking in, and undoing the way out — drop from Explorer, undo an unlink | not started |

## Decisions

| Date | Question | Choice |
|---|---|---|
| 2026-09-20 | What is Home? | The library: linked roots plus the places pinned inside them. A screen whose content changes is worth landing on; a list of two shortcuts is not. |
| 2026-09-20 | How is the launch folder chosen? | A flag on a card, stored as one path. Rejected: the first grid position, which breaks at one folder and gives dragging a hidden second meaning. |
| 2026-09-20 | Does grid order mean anything? | No — cosmetic in both zones. That is what keeps the drag gesture free of consequence. |
| 2026-09-20 | Fix the Library dock item or remove it? | Remove. It named a place that cannot be occupied, listed folders Home does not, and did nothing when clicked. |
| 2026-09-20 | Confirm dialog on unlink? | No — an undo banner. The action is destructive but ordinary; a dialog taxes every use to guard the rare mistake. |
| 2026-09-20 | Recents, or a launch setting in Settings? | Neither. A third list of folders, and an answer stored away from the thing it changes. |
