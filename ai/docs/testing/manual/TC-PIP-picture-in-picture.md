# TC-PIP — picture-in-picture

A separate native window. Nothing in this area is reachable from the test suite: `PipManager` and
`GlobalPipWindow` create a real OS window outside the main composition.

---

### TC-PIP-01 — entering and leaving carries the state both ways

**Area:** PiP, transitions
**Preconditions:** a video playing in the viewer, zoomed past Fit and panned off centre.
**Steps:**
1. Note the position, zoom percentage and pan offset.
2. Enter PiP.
3. Let it play for a few seconds.
4. Return to the main viewer.
**Expected:** playback continues across both transitions without restarting or re-buffering. The play
head is where it should be. Zoom and pan are carried into PiP and restored on the way back — not
reset to Fit.
**Why this is not automated:** the state travels between two windows with independent compositions;
there is no in-process seam to assert across.

---

### TC-PIP-02 — dragging the window and its corners

**Area:** PiP, window management
**Preconditions:** PiP active, playing.
**Steps:**
1. Drag the window by its body to each corner of the screen.
2. Drag each of the four corners to resize, small and large.
3. Drag it to a second monitor if one is available; note in the result if not.
4. Zoom past Fit, then hold `Ctrl` and drag with the left button.
5. With the video **playing**, drag the window and let the button up. Then with it **paused**, drag
   and let the button up again. Repeat the drag twice in quick succession.
6. Click the picture once without moving the mouse.
**Expected:** the window follows the cursor without lag or detachment, and the picture inside it
stays put — a plain drag moves the window and nothing else. `Ctrl + LMB` is what pans the picture,
and it leaves the window where it is. Letting the button up after a drag changes nothing: a playing
video keeps playing, a paused one stays paused, and two drags in a row do not drop PiP back into the
app. A click that did not travel still toggles play/pause. `PipCornerFlash` fires when a corner is
engaged. The video keeps its aspect ratio through every resize and never letterboxes into the wrong
shape. Playback does not stall while dragging.
**Why this is not automated:** OS window dragging, and a visual cue tied to it. Steps 4–6 in
particular. The pan gate is asserted by `ViewerPanModeUiTest`, but only from the side that *can* be
injected: a held `Ctrl` does not attach itself to a mouse event the test framework sends, and the
drag-is-not-a-tap guard measures **screen** travel because the window moves with the cursor — with
no window to move, a test of it would pass whether the guard existed or not.

**Two defects found 2026-08-07 (dbad022), both fixed:**

- A plain drag moved the window *and* panned the video inside it. Both handlers fired —
  `pipWindowDraggable` moves the window whenever `Ctrl` is not held, and `mediaViewerGestures` panned
  on any drag at all. `MediaViewerContent` now takes `panRequiresCtrl`, which the fullscreen surface
  sets: the plain drag belongs to the window, `Ctrl + LMB` (or `Ctrl + RMB`) pans.
- Letting the button up after a drag toggled playback, and a second drag closed PiP. The window
  travels with the cursor, so the pointer barely moves in the composable's own coordinates and
  `detectTapGestures` read the release as a click. `pipWindowDraggable` now runs on the `Initial`
  pass and consumes once the cursor has travelled more than a few **screen** pixels — measured from
  `MouseInfo`, since local coordinates cannot see this at all.

**Changed since:** the right-click menu no longer shows Edit, Copy and Info. This window has no file
open in the sense those mean and all three were wired to no-op lambdas, so the menu rendered and did
nothing; it now carries the Always on Top switch instead — TC-PIP-06.

---

### TC-PIP-03 — keys inside PiP

**Area:** PiP, input
**Preconditions:** PiP active, focused.
**Steps:**
1. Press space; press it again.
2. Press left and right arrows.
3. Adjust volume with the keyboard.
4. Click the main window to give it focus, then press space.
**Expected:** transport keys act on the PiP video while PiP has focus. Once focus moves to the main
window, the same keys act there and do not reach PiP — no double handling, no key acting on both.
**Why this is not automated:** OS focus routing between two native windows.

---

### TC-PIP-04 — the already-opened conflict, both branches

**Area:** PiP, `VideoAlreadyOpenedDialog`
**Preconditions:** a video playing in PiP.
**Steps:**
1. With PiP playing file A, open file A again from Folder View. The dialog appears.
2. Take the branch that keeps PiP. Observe both windows.
3. Repeat from step 1 and take the other branch.
**Expected:** each branch does exactly what it offers. The file never ends up decoding twice with two
audio streams overlapping. Whichever window loses ownership stops cleanly rather than freezing on a
frame.
**Why this is not automated:** a dialog mediating between two windows, and the failure mode is
audible (two soundtracks) rather than assertable.

---

### TC-PIP-05 — closing the main window while PiP is active

**Area:** PiP, lifecycle
**Preconditions:** PiP active and playing; the main window open behind it.
**Steps:**
1. Close the main window.
2. Observe PiP.
3. Close PiP.
4. Check Task Manager for a surviving `Woka` process.
**Expected:** defined behaviour either way — PiP keeps playing as the last window, or it closes with
the app — but not a PiP window with no owner and no way to close it. After step 3 no process
survives, and `settings.json` holds the final state (the shutdown hook flushes it).
**Why this is not automated:** process lifetime and OS window ownership.

---

### TC-PIP-06 — Always on Top

**Area:** PiP, window stacking · **settings:** `pipAlwaysOnTop`, on by default
**Preconditions:** a video playing, the window shrunk to PiP. A second app to click on — Explorer or
a browser. `settings.json` reachable.
**Steps:**
1. With PiP playing, click the other app so it takes focus. Then click around in it.
2. Right-click the PiP picture. Turn **Always on Top** off from the menu.
3. Click the other app again.
4. Turn it back on from Settings → PiP instead, and check the right-click menu agrees.
5. Fill the screen again with the toolbar button, and click the other app.
6. Turn it off, quit Woka, look at `settings.json`, and start Woka again.
**Expected:** with the flag on, PiP stays above the other app in every position and keeps playing.
The right-click entry carries a check mark exactly when the flag is on, and the Settings switch and
the menu are always the same value in both directions — neither needs the window reopened to take
effect. While the window **fills the screen** the flag does nothing: the other app comes to the
front normally. After step 6 `settings.json` holds `"pipAlwaysOnTop": false` and the next launch
starts with it off. On by default, so a fresh profile has the file mention nothing at all.
**Why this is not automated:** window stacking order is an OS decision about two native windows,
judged by looking at them.

---

### TC-PIP-07 — the toolbar's shrink / fill button

**Area:** PiP, window mode
**Preconditions:** a video playing, fullscreen.
**Steps:**
1. Look at the top-right toolbar.
2. Click the picture-in-picture button.
3. Click it again.
4. From fullscreen, drag the window's edge inwards by hand instead of using the button.
5. Press the button to go back to fullscreen, then shrink and grow it a few more times, mixing the
   button and the edge drag.
6. In the state step 4 leaves, right-click and check Always on Top.
**Expected:** the toolbar holds the zoom control, the PiP button and the carousel icon — **no Info
and no Edit**, both of which used to sit there doing nothing. The button shrinks the window to a
box in the bottom-right corner at the video's aspect ratio, and fills the screen again on the second
click; its tooltip says which it will do. The edge drag reaches the same state as the button, and
Always on Top starts applying at that point without anything else being touched. **The two routes
mix in any order** — a window shrunk by dragging its edge grows back from the button just as one
shrunk by the button does.
**Why this is not automated:** window placement against a real screen, and a judgement about the
resulting box.

**Defect found and fixed while building this:** after an edge drag the button would not restore
fullscreen — only a window shrunk *by the button* grew back. Two layers of the same cause, and the
first fix for it was wrong:

- `WindowState.placement` does not follow the frame when the window is un-maximised by a drag, so
  Compose still held `Maximized`; assigning `Maximized` to it was not a change and its updater never
  fired. The mode now follows the frame through a `ComponentListener` instead.
- That was not enough, because `Frame.MAXIMIZED_BOTH` has the same shape of problem: an edge drag
  can leave the flag set on a window that fills nothing, and setting a flag that is already set is
  also not a change. The flag is gone from this code entirely — "PiP" is now measured as
  *"smaller than the working area"*, and both directions of the toggle set an explicit rectangle.
