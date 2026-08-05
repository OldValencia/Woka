# TC-SYS — OS integration

Everything that leaves the process: the CLI entry point, hardware keys, the system media session,
native dialogs, the clipboard.

---

### TC-SYS-01 — opening a file from the shell

**Area:** CLI routing (`Woka.exe [path]`)
**Preconditions:** a built desktop app. A folder of images that is **not** currently linked.
**Steps:**
1. Run `Woka.exe "D:\path\to\photo.jpg"` from a terminal.
2. Press `X` (close) in the viewer.
3. Accept the offer to pin the folder.
4. Repeat step 1 with a video path.
**Expected:** the app opens straight into the viewer on that file, not on Home. `X` leads to Folder
View for the *parent* directory, with the offer to pin it. Accepting adds it; the folder is there on
the next launch.
**Why this is not automated:** process launch with arguments, and the routing decision it drives.

---

### TC-SYS-02 — hardware media keys, on and off

**Area:** OS media session (§4), `systemMediaControls`
**Preconditions:** a keyboard with media keys, or a headset with them. A video playing.
**Steps:**
1. With `systemMediaControls` on, press play/pause, next and previous.
2. Start playback in another app (a browser tab). Press play/pause.
3. Turn `systemMediaControls` off in Settings and press the keys again.
**Expected:** with the setting on, the keys control Woka while Woka is the active session, and the OS
routes them to whichever app is playing — Woka never steals them from the other app. With it off,
Woka ignores them entirely and the other app keeps them.
**Why this is not automated:** the OS decides the routing; that decision is the thing under test.

---

### TC-SYS-03 — the Now Playing panel

**Area:** OS media session (§4)
**Preconditions:** a video with a recognisable name, playing. See
`ai/docs/features/implemented/system-media-controls.md`.
**Steps:**
1. Open the Windows volume/media flyout while playing.
2. Pause from the flyout, then resume from it.
3. Switch to another file and check the panel again.
4. Close the viewer.
**Expected:** title and artwork appear and match the file. State follows Woka both ways. Switching
files updates the panel. Closing the viewer removes the session rather than leaving a stale one.
**Why this is not automated:** the panel is an OS surface rendered outside the app.

---

### TC-SYS-04 — the native folder picker

**Area:** linked folders, AWT `FileDialog`
**Preconditions:** Home screen.
**Steps:**
1. Add a linked folder through the picker.
2. Cancel the picker without choosing.
3. Pick a path with non-ASCII characters and spaces.
4. Pick a network or removable path if one is available.
**Expected:** a native Windows dialog, not a Compose one. Cancel adds nothing. The non-ASCII path is
stored and re-read correctly and its folder card shows the right name.
**Why this is not automated:** a modal OS dialog; the suite must not open one.

---

### TC-SYS-05 — fullscreen

**Area:** viewer, window
**Preconditions:** a video playing, window not maximised, zoomed past Fit.
**Steps:**
1. Enter fullscreen.
2. Change the zoom while fullscreen.
3. Leave fullscreen with the same control, re-enter, then leave with `Esc`.
**Expected:** fullscreen covers the screen with no title bar and no taskbar. Zoom and pan survive
both transitions rather than resetting. `Esc` leaves fullscreen and does *not* also close the viewer
— one key, one effect.
**Why this is not automated:** an OS window-state change, and the `Esc` case is about an event not
reaching a second handler.

---

### TC-SYS-06 — copy to clipboard

**Area:** viewer, clipboard
**Preconditions:** an image open in the viewer. An Explorer window and a text editor open.
**Steps:**
1. Copy the image from the viewer.
2. Paste into Explorer in another folder.
3. Copy the File Info text from the info dialog.
4. Paste into the text editor.
**Expected:** the image paste produces a real file in the target folder. The info paste produces the
full plaintext block — name, path, size, date, streams — not a truncated or single-line version.
**Why this is not automated:** the system clipboard is shared machine state; a test writing to it
would clobber whatever the person running it had copied.
