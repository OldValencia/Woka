# TC-VID — video playback

What is heard and noticed. Seeking arithmetic, transport clamping and track enumeration are covered
by `VideoSeekTest`, `VideoTransportTest` and `MediaProbeTest`; nothing here re-checks them.

---

### TC-VID-01 — the first frame arrives clean and in sync

**Area:** viewer, playback start
**Preconditions:** a real film or episode, at least 1080p, with audio. Not a corpus fixture.
**Steps:**
1. Open the file from Folder View.
2. Watch the first three seconds without touching anything.
**Expected:** no black flash between the poster frame and the first decoded frame. Audio starts with
the picture, not before or after it. No visible stutter while the decoder settles.
**Why this is not automated:** `VideoPlayerEngineTest` asserts a frame arrives. Whether the
*transition* into it is seamless is a property of what the eye catches in the first few hundred
milliseconds.

---

### TC-VID-02 — pitch does not drift with speed

**Area:** viewer, playback speed (§6)
**Preconditions:** a video with clear speech.
**Steps:**
1. Play, and set the speed to 0.5×.
2. Step up through 0.75×, 1×, 1.25×, 1.5×, 2×, listening at each.
3. Return to 1× using the snap gesture.
**Expected:** speech stays at its own pitch at every step — faster or slower, never higher or lower.
No warbling or metallic artefacts from the `atempo` filter. 1× sounds identical to untouched
playback.
**Why this is not automated:** `PlaybackSpeed.snap` and its labels are unit-tested. Pitch is a
property of the audio a person hears; asserting it would need spectral analysis of a live sound line.

---

### TC-VID-03 — dragging the slider feels continuous

**Area:** viewer, transport
**Preconditions:** a long video (≥ 20 minutes), 1080p or better.
**Steps:**
1. Play, then press and hold on the slider.
2. Drag slowly across a third of the timeline, then quickly back.
3. Release.
**Expected:** the picture tracks the drag rather than jumping in large steps. Audio parks during the
drag and resumes cleanly at the release point, playing if it was playing. No frame from the old
position appears after the release.
**Why this is not automated:** `pauseForSeek`/`resumeAfterSeek` are asserted. Smoothness is the
aggregate of dozens of seeks per second against a real decoder — the thing under test is the
timing, which a test harness would replace.

---

### TC-VID-04 — the poster frame is worth looking at

**Area:** Folder View, thumbnails
**Preconditions:** a folder of at least ten real videos, thumbnail cache cleared
(`Documents/OldValencia/Woka/.thumbnails`).
**Steps:**
1. Open the folder and let every thumbnail generate.
2. Scan the grid.
**Expected:** no cell shows a black frame, a fade, or a plain logo card where the file plainly has
content. Regenerating (delete the cache, reopen) may pick different frames — that is by design.
**Why this is not automated:** `isMonochrome` is unit-tested and the pipeline is asserted end to end,
but every fixture in the committed corpus is a few seconds of low-motion footage that
`isMonochrome` rejects, so the extractor rightly falls back to its first candidate. The claim
"a good frame was chosen" has no material in the suite to be tested against.

---

### TC-VID-05 — chapter ticks and their names

**Area:** viewer, chapters (§4)
**Preconditions:** a real file with named chapters (a Blu-ray rip or an MKV with a chapter list).
`chapters.mkv` in the corpus is 3 seconds long and only proves the parse.
**Steps:**
1. Open the file and reveal the controls.
2. Look at the tick marks on the slider.
3. Hover each tick.
4. Seek to just before a boundary and let playback cross it.
**Expected:** one tick per boundary, positioned proportionally. The tooltip carries the chapter's own
name, not "Chapter 3". Ticks stay put as the window is resized.
**Why this is not automated:** `MediaChapterProbeTest` covers the boundaries and titles. Their
placement along a rendered slider, and the tooltip, are UI geometry against real data.

---

### TC-VID-06 — switching the audio track does not disturb the picture

**Area:** viewer, audio tracks (§2.1)
**Preconditions:** a file with several audio tracks — the corpus `S01E01` fixture has eight, seven
of them sharing `rus` and differing only by title.
**Steps:**
1. Play, and open the audio track menu.
2. Switch to a different track mid-playback.
3. Switch again, twice more, without pausing.
4. Check the menu still reports the track that is actually playing.
**Expected:** the picture never freezes, tears, or steps back. Audio changes within a beat and stays
in sync with the video clock afterwards. The seven same-language tracks are distinguishable in the
menu by their titles.
**Why this is not automated:** the guarantee is "video is untouched while audio re-opens" — a
property of two live decode loops, observable only as an absence of a visible glitch.

---

### TC-VID-07 — the reload overlay appears when it should and goes when it should

**Area:** viewer, video tracks (§2.3)
**Preconditions:** a multi-video-stream container, ideally 4K so the re-open is slow enough to see.
The corpus `DualVideo` fixture is 320×180 and swaps too fast.
**Steps:**
1. Play, and switch to the second video stream.
2. Watch the overlay through the swap.
3. Switch back.
**Expected:** the spinner appears over the last frame — not over black — and clears the moment the
new stream's first frame lands. Audio parks while it is up rather than running ahead. It never
survives a failed swap.
**Why this is not automated:** `isReloadingVideo` could be asserted, but the case is about *when* the
user sees it relative to the picture, which is exactly the timing a test would flatten.

---

### TC-VID-08 — all four end-of-file behaviours on a mixed folder

**Area:** viewer, repeat and shuffle (§6)
**Preconditions:** a folder with at least three videos and several images interleaved.
**Steps:**
1. Play the last video with Repeat Off, shuffle off. Let it end.
2. Set Repeat File. Let it end.
3. Set Repeat Folder. Let it end, and keep watching past the wrap.
4. Set shuffle on with Repeat Off. Play through every file.
**Expected:** Off stops at the end. File restarts the same video. Folder advances and wraps to the
first video, skipping the images. Shuffle plays each video once and then stops rather than repeating
one early.
**Why this is not automated:** `PlaylistWalkTest` covers the walk exhaustively. What it cannot cover
is the handover — that EOF in the decoder actually reaches the navigation layer and loads the next
file in a live viewer.
