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
**Expected:** the window follows the cursor without lag or detachment. `PipCornerFlash` fires when a
corner is engaged. The video keeps its aspect ratio through every resize and never letterboxes into
the wrong shape. Playback does not stall while dragging.
**Why this is not automated:** OS window dragging, and a visual cue tied to it.

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
