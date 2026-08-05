# TC-SUB — subtitles

Rendering and positioning. Parsing, encodings, the sidecar scan and the bitmap index are covered by
`SrtParserTest`, `AssParserTest`, `SubtitleFileTest`, `SubtitleCharsetTest`, `SubtitleStreamTest` and
`BitmapSubtitleTest` — none of that is repeated here.

---

### TC-SUB-01 — bitmap subtitles sit where they were authored

**Area:** viewer, bitmap subtitles (Step 10)
**Preconditions:** a real release with a PGS track, ideally one that places signs and dialogue
differently.
**Steps:**
1. Play until a dialogue subtitle is up.
2. Zoom in past Fit, then out below it.
3. Pan while a subtitle is showing.
4. Enter fullscreen and let several more appear.
**Expected:** the subtitle keeps its authored place relative to the *picture*, scaling with it — not
pinned to the window and not drifting under pan. Nothing is clipped at the frame edge. A sign
positioned over part of the image stays over that part.
**Why this is not automated:** `BitmapSubtitleTest` asserts the rectangles and their coordinates.
Whether they land correctly once the frame has been scaled and panned is a composition of the
subtitle canvas with the render transform, judged on screen.

---

### TC-SUB-02 — ASS tags render as authored

**Area:** viewer, ASS rendering
**Preconditions:** a real `.ass` sidecar using italics, colour overrides and `\pos`. See
`ai/docs/features/implemented/subtitle-ass-full-rendering.md` for what is in scope.
**Steps:**
1. Load the sidecar against its video.
2. Play through a passage using italics and a colour change.
3. Play through a positioned sign.
**Expected:** italics render as italics, colours as authored, and a `\pos` line appears where it was
placed rather than at the default position. No raw `{\i1}` or `\N` visible anywhere.
**Why this is not automated:** the parser tests assert the tags are stripped from the text. That the
*styling they encoded* is then applied is a rendering outcome.

---

### TC-SUB-03 — appearance settings do what they say

**Area:** settings, subtitle appearance (§3)
**Preconditions:** any video with text subtitles showing.
**Steps:**
1. Step the size through Auto, Small, Medium, Large, Extra Large.
2. Change the colour, then the background opacity.
3. Toggle the outline.
4. Resize the window from small to maximised at each of two sizes.
5. Reset the appearance.
**Expected:** each control changes what it names and nothing else. Auto scales with the video panel
height; the fixed steps stay proportional across a resize rather than snapping back. Reset returns
every field to the shipped defaults at once.
**Why this is not automated:** the values round-trip through settings and that is asserted. Legibility
at each step, against real video, is the whole point of the feature.

---

### TC-SUB-04 — the vertical shift and the sync offset

**Area:** viewer, subtitle placement and sync (§3)
**Preconditions:** a video whose subtitles are deliberately out of sync, or use the offset to push
them out.
**Steps:**
1. Raise and lower the vertical shift while subtitles are showing.
2. Apply a positive subtitle offset, then a negative one, of a second or more each.
3. Seek backwards and forwards and check the offset still holds.
**Expected:** the shift moves subtitles without clipping them off the frame or under the controls.
The offset re-times the *lookup* — nothing re-decodes, no stutter — and survives seeking in both
directions.
**Why this is not automated:** the offset is applied at lookup time; proving it "still holds after a
seek" needs a real decode timeline, and the visible criterion is lip sync.

---

### TC-SUB-05 — subtitles are never covered by the chrome

**Area:** viewer, layering
**Preconditions:** a video with subtitles near the bottom of the frame.
**Steps:**
1. Play until a two-line subtitle is up.
2. Move the mouse so the controls appear over it.
3. Open the carousel while a subtitle is showing.
4. Let the chrome auto-hide and watch the subtitle again.
**Expected:** the subtitle is readable throughout — either the chrome sits above it without
overlapping, or the subtitle lifts while the chrome is up and settles back when it hides. It is never
half-hidden behind the controls or the carousel.
**Why this is not automated:** a layering and layout interaction between three independently
animated pieces of chrome; the failure is "I cannot read it", not a measurable overlap.

---

### TC-SUB-06 — a CP1251 sidecar reads correctly on screen

**Area:** viewer, sidecar encodings
**Preconditions:** a real Russian `.srt` in CP1251, next to its video.
**Steps:**
1. Open the video; the sidecar is found by the scan.
2. Select it and play a passage.
**Expected:** Cyrillic renders as Cyrillic, with no mojibake and no replacement glyphs, in the
viewer's own font.
**Why this is not automated:** `SubtitleCharsetTest` asserts the decode. This case exists one level
further on: that the decoded string also *renders*, in whatever font the appearance settings pick —
a missing glyph is invisible to a string assertion.
