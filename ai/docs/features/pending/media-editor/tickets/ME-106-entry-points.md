# ME-106 — Entry points

| | |
|---|---|
| **Order** | 20 |
| **Depends on** | ME-104, ME-105 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Make `Edit` open the editor, from both doors, and make `Done` write a file — so the editor is
usable end to end on PNG, JPEG and WebP.

**Why now.** Everything it needs exists and nothing above it can be judged without it: this is the first
ticket after which the feature can be *used* rather than only tested.

## Scope, and what `Done` does here

`Done` **saves a copy** and closes. That is all: no menu, no overwrite, no confirmation, no banner —
those are **ME-130**, and each of them is a decision about what a dialog says rather than about whether
the editor works. Splitting them keeps this ticket to one sitting and leaves ME-130 with a coherent
subject instead of leftovers.

The folder listing is refreshed after a successful save, because a copy is a **new** path and nothing
watches the directory for it — without that the file exists and is invisible, which reads as a failed
save.

## Two doors, one decision, and the one that already half-exists

The grid's `Edit` entry has been sitting beside Rename/Copy/Delete since ME-101 with `enabled = false`,
deliberately, because `ImageEditorEngine.Surface` was still a `TODO`. It is not any more. Replacing that
literal with `engineFactory.create(target) != null` is the entire editability decision on that side —
the factory is the single answer (§4.1), it routes on `File.mediaKind()`, and it returns null for video
and for anything unknown.

The viewer's `Edit` is `editAction = { onClose(state.currentId) }`, a stub that closes the viewer. It
becomes a call to the same entry function.

**Constructing an engine to grey a menu item is cheap and stays cheap.** `sourceSize` is lazy, so no
pixel is decoded; the only disk touch is the `SourceStamp`'s `length()`/`lastModified()`, which is a
stat. If that ever stops being true, the factory is where the cheap check belongs, not the call site.

## Where it mounts, and the keyboard

**Its own overlay in `FolderScreen`, beside `viewerMediaId`** (§9, 2026-08-18). The viewer is an overlay
too, not a `Screen`, so this is the same shape: `Cancel` from the grid returns to the grid, `Cancel` from
the viewer returns to the viewer with its exact zoom and pan, because the viewer stays mounted and
frozen underneath.

**`GlobalShortcutState.onPreviewKey` is a single mutable slot.** Whoever writes it last wins, and the
viewer's `MediaViewerShortcutEffect` installs its handler in a `DisposableEffect(globalShortcuts)` that
will not re-run when the editor overwrites it. So the editor must **save the previous handler and put it
back on dispose** — otherwise Esc, Space and the arrows stop working in the viewer after the first edit
and stay broken until the app restarts. This is the same slot ME-124 has to arbitrate between three
claimants; here there are two.

The dock is hidden while the editor is open, the way it already hides for the viewer.

## Steps

1. `shell/EditorEntry.kt` (jvmMain): `data class EditorTarget(val path: String, val frameIndex: Int = 0)`
   and `internal fun isEditable(factory: EditorEngineFactory, path: String): Boolean`. The *state* — a
   nullable `EditorTarget` — belongs to `FolderScreen`, because that is what mounts the overlay; this
   file holds only the type both doors speak in and the one place editability is decided.

   `frameIndex` is carried from the start and both callers pass 0 today. A frame set is edited through
   ME-108's conversion, and what the carousel shows afterwards is ME-126 — but the parameter exists now
   so neither of those has to change this signature.
2. `shell/EditorHost.kt`: `@Composable internal fun EditorHost(target: EditorTarget, onClose: () -> Unit,
   onSaved: (String) -> Unit)` — resolves `EditorEngineFactory` and the `EditorSession` factory from
   Koin, **owns the session's lifetime and calls `close()` on dispose** (ME-004's rule; ME-128 is what
   moves that ownership to a store), composes `EditorScreen`, runs the save on `Done`, and hosts the
   discard dialog. A target the factory cannot open closes immediately rather than showing an empty
   shell.
3. `shell/EditorKeys.kt`: install a preview-key handler for `Esc` (cancel, through the same dirty check
   as the button), `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` (undo, redo) and `Ctrl+S` (done). A
   `DisposableEffect` that captures `globalShortcuts.onPreviewKey` on entry and **restores it on
   dispose**, with a comment saying why. `Ctrl+Shift+S` waits for ME-130, which is what adds the
   overwrite target at all.
4. `shell/DiscardChangesDialog.kt`: shown when `Cancel` or `Esc` arrives with `session.isDirty` true.
   Follows the app's existing dialog language and is **not** over media, so it takes `Theme.colors`
   rather than the on-scrim set.
5. `feature/folder/FolderScreen.kt`: an `editorTarget: EditorTarget?` state beside `viewerMediaId`,
   `EditorHost` mounted when it is non-null, `dockAccessories.isVisible` false while it is, and
   `viewModel.refresh()` plus `scrollToMediaId` on `onSaved`.
6. `feature/folder/FolderItemContextMenu.kt`: `enabled = isEditable(...)` instead of `false`, and the
   click sets the target.
7. `feature/viewer/NativeMediaViewer.kt` (expect **and** actual) and `MediaViewerScreen.kt`: thread an
   `onEdit: (String) -> Unit`, and make `editAction` call it instead of `onClose`.

## Files

`jvmMain feature/editor/shell/EditorEntry.kt`, `shell/EditorHost.kt`, `shell/EditorKeys.kt`,
`shell/DiscardChangesDialog.kt` (new), `commonMain feature/folder/FolderScreen.kt`,
`feature/folder/FolderItemContextMenu.kt`, `feature/viewer/NativeMediaViewer.kt`,
`jvmMain feature/viewer/NativeMediaViewer.kt`, `feature/viewer/shell/MediaViewerScreen.kt` (edit),
`jvmTest ui/EditorEntryUiTest.kt`, `jvmTest unit/EditorEntryTest.kt` (new)

## Verification

`unit/EditorEntryTest.kt` — `isEditable` is true for the three encodable formats, **false for video**,
false for an unknown extension, and false for a path that does not exist. No decode happens: assert
`ImageBitmapCache.retainedBytes` is unchanged across the calls.

`ui/EditorEntryUiTest.kt`:

- the grid's `Edit` entry is **enabled** for a PNG and **disabled** for an `.mp4`;
- choosing it mounts the editor and hides the dock;
- `Cancel` on a clean document closes with no dialog; on a dirty one it asks, and `Keep editing` leaves
  the editor open with the document intact;
- **the previous key handler is restored on dispose** — install a sentinel handler, open and close the
  editor, and assert the sentinel is back. This is the regression that would otherwise be found by a
  user whose arrow keys stopped working.

## Done when

- `Edit` opens the editor from the grid and from the viewer, and `Done` leaves a `-edited` file in the
  directory that the grid then shows;
- `editAction` no longer calls `onClose`;
- the viewer's shortcuts work again after the editor has been opened and closed;
- `./gradlew.bat :shared:build` green, with the tests above passing.

## Result — 2026-08-18

`:shared:build` green, **628 tests, 0 failures** (620 before). The editor opens from both doors and
`Done` leaves a `-edited` file the grid then shows.

### `FolderScreen` is `commonMain`, so the mount needed a seam

`EditorHost` and everything it draws with are `jvmMain` internals, and `FolderScreen` cannot see them.
Rather than widening a dozen declarations, the editor gained the shape the viewer already has:
`expect fun NativeMediaEditor` in `commonMain`, `actual` in `jvmMain`, both in
`com.woka.feature.editor`. `EditorTarget` and `isEditable` moved to `commonMain` with them — neither
has a JVM dependency, and `EditorEngineFactory` was already a `commonMain` interface precisely so the
decision could live above the engine that answers it.

### The tests found two behaviours, one of which contradicted an earlier decision

**A file that is not there was editable.** `mediaKind()` reads the extension alone and `SourceStamp`
answers 0/0 for a missing file rather than failing, so `create` handed back an engine for a path that
had been deleted — leaving the grid's `Edit` entry enabled and turning the problem into a failed save
instead of a greyed menu item. Fixed in the factory, which is the single answer.

That **reverses an intentional assertion from ME-008**: `editability is a question about the format,
not about the bytes` existed to keep a menu cheap. The cost argument was right and is now obsolete —
`isFile` is a stat rather than a decode, and since ME-104 the engine's `SourceStamp` stats the file in
its constructor anyway. The test is rewritten rather than deleted, and says why it flipped; the half of
its claim that survives — no pixel is read to answer — is now its own test.

**Twenty editability questions cost zero bytes of cache**, asserted, because this runs while a context
menu is being drawn and a folder of 40 MP photographs would hitch on every right-click if it ever
started decoding.

### The keyboard hand-back has its own test, and needs one

`GlobalShortcutState.onPreviewKey` is a single mutable slot, and the viewer installs its handler in a
`DisposableEffect(globalShortcuts)` that does **not** re-run when the editor overwrites it. An editor
that forgets to restore leaves the viewer without Esc, Space or arrows — and that arrives as "the arrow
keys stopped working", days later, with nothing connecting it to an edit.

`EditorKeysUiTest` installs a sentinel, mounts the effect, asserts the slot changed, and asserts the
sentinel is back once the composition is gone. It also covers the empty case, so an editor opened
outside the viewer restores *null* rather than leaving its own handler behind.

`Ctrl+Z` and `Ctrl+Y` are consumed **even when there is nothing to undo**: the editor owns the keyboard
while it is open, including for keys it currently has no work for, or the press falls through to
whatever the viewer underneath does with it.

### Still to come

`Ctrl+Shift+S` is absent because the overwrite target is ME-130's. The session is owned by
`EditorHost`'s `DisposableEffect`, which is exactly the ownership ME-128 moves to a store when more
than one editor can be open at once.
