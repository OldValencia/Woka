# Home rethink — backlog

Three chunks, not a queue of micro-tickets: one person builds this, and each block below is a
sitting that ends with something that can be looked at. A block is promoted into
`tickets/<ID>-<slug>.md` when it becomes the next thing to build, written against the tree as it is
then. No file paths here on purpose.

### HOME-001 — Home becomes the library

The whole screen, in one go. Linked folders get an explicit order that a drag rewrites and settings
persist. A second zone appears beneath them for the pinned folders, drawn as smaller cards with the
folder name over a breadcrumb line, draggable the same way, and absent entirely when nothing is
pinned. A "Link a folder" tile closes the roots grid at every count, which is also what the first
run becomes — that tile alone with one line under it, instead of the words "No linked folders".
While the roots fit one row the content is centred rather than packed into the top-left corner;
beyond that the grid fills as today. Order stays cosmetic: nothing may read a card's position and
mean something by it.

**Depends on.** Nothing.

### HOME-002 — Launch, and a dock that tells the truth

One stored path decides what opens: empty means Home, and it is set or cleared from the card's
context menu and from a hollow badge in the card's corner on hover, filled on the card that holds
it. Setting it anywhere clears it everywhere else. The launch route honours it and keeps Home
beneath the folder in the stack; a path that has moved or died opens Home and drops the flag. In the
same pass the dock loses Library — the item, its badge, its hover popup and the tab state behind it
— leaving Link, Home, Search, Settings, with Home active only on Home.

**Depends on.** HOME-001 — pinned folders need their new home before the old one is removed.

### HOME-003 — Linking in, and undoing the way out

Accept an OS drag onto the window: a dropped directory links it, on Home and inside a folder view
alike, and a dropped file stays the quick-preview case that opens the viewer. Nothing in the
codebase does OS drag-and-drop yet, so this establishes the pattern as well as the feature. The
other end: unlinking sweeps every stored record under the folder's path prefix — watch history and
remembered tracks for a whole tree — immediately and silently. It gets an undo banner above the
dock for a few seconds that restores all of it, not only the link.

**Depends on.** Nothing.

## Not decided

### Number keys to jump to a root

`1`–`9` would open the nth linked folder from anywhere, in the order arranged on Home — the thing
that would make ordering worth doing beyond looks, and a fast way back to a root now that the dock
no longer lists them. Undecided: it gives grid position a meaning after HOME-001 deliberately took
one away, which is the same trap the launch slot fell into, only quieter. If it is built, the key
should be bound to a folder explicitly rather than to its place in the row.
