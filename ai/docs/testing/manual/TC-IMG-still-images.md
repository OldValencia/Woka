# TC-IMG — still images

What the eye has to judge. The zoom arithmetic behind all of this is covered by `ZoomScaleTest`,
`ScaleMathTest` and `PanBoundsTest`; nothing here re-checks a formula.

---

### TC-IMG-01 — transparency reads correctly on both themes

**Area:** viewer, still images
**Preconditions:** a PNG and a WebP with real alpha (a logo on transparency, not a checkerboard
image). Settings → Appearance available.
**Steps:**
1. Open the PNG in the viewer on the light theme.
2. Switch the theme to dark without leaving the viewer.
3. Repeat with the WebP.
**Expected:** transparent regions take the viewer backdrop, not black, not white, and not a
chequerboard. The image edge stays clean against the backdrop on both themes. No flash of the wrong
colour while the theme changes.
**Why this is not automated:** the failure is "it looks wrong against this backdrop" — a judgement
about two colours next to each other, not a pixel value any single assertion can name.

---

### TC-IMG-02 — a large image is not mushy at Actual

**Area:** viewer, zoom
**Preconditions:** a photo of at least 4000 px on its long edge. Window not maximised.
**Steps:**
1. Open the photo. It arrives fitted.
2. Set the size to Actual (100%).
3. Pan to a region with fine detail — text, foliage, fabric weave.
**Expected:** at 100% the detail is as sharp as the same file in any other viewer. No softening, no
resampling halo, no visible downscale-then-upscale.
**Why this is not automated:** this is the single surviving zoom case. `ScaleMathTest` proves Actual
resolves to native 1:1; whether the *pixels drawn* are the native ones is a question about the
render path and can only be seen.

---

### TC-IMG-03 — zoom follows the cursor

**Area:** viewer, zoom
**Preconditions:** any image larger than the panel when zoomed.
**Steps:**
1. Open the image, fitted.
2. Put the cursor over a small distinctive feature away from centre.
3. Wheel up several notches without moving the mouse.
4. Wheel back down to fitted.
**Expected:** the feature under the cursor stays under the cursor throughout, in and out. No drift
that accumulates over the notches; the frame returns to centred when it no longer overflows.
**Why this is not automated:** `zoomAround` is arithmetic and could be unit-tested, but "it feels
anchored" is about the accumulated result across many real wheel events at real timings.

---

### TC-IMG-04 — a photo shot in portrait opens in portrait

**Area:** viewer, still images · **tracks F3**
**Preconditions:** a photo straight off a phone or camera, taken in portrait, carrying EXIF
`Orientation` (not one already re-encoded by an editor).
**Steps:**
1. Open the containing folder in Woka.
2. Look at the grid thumbnail.
3. Open the photo in the viewer.
**Expected:** upright in both the grid and the viewer, matching what Explorer's preview shows.
**Why this is not automated:** the assertion *is* automated — `ImageLoaderTest` has it, `@Ignore`d
against F3 with a synthetic fixture. This case exists because the fixture has one hand-spliced APP1
segment, and real cameras write orientation in ways that vary; it is the check that the fix works on
actual files.

---

### TC-IMG-05 — an HDR photo: what it looks like today

**Area:** viewer, still images · **input for the F4 decision**
**Preconditions:** an HDR still — HEIF, AVIF, or a PNG with a PQ transfer. An HDR-capable display if
one is available; note in the result if not.
**Steps:**
1. Open the photo in the viewer.
2. Open the same file in Windows Photos side by side.
**Expected:** *record*, do not judge. Note whether Woka's rendering is washed out, over-bright, or
close to the reference, and whether the file opens at all (HEIF has no decoder yet — see
`MediaKind.UNDECODABLE_EXTENSIONS`).
**Why this is not automated:** F4 is an open scope decision — whether tone mapping extends to stills
or HDR stays video-only. This case produces the evidence for that decision; there is no expected
value to assert against until it is made.

**Recorded 2026-08-07 (dbad022):** HEIF, HEIC and AVIF do not open at all; a PQ PNG does open and
renders. That is the whole of the evidence F4 was waiting for, and it matches
`pending/rare-image-formats-and-frame-sets.md` § 1 line for line — HEIC has no decoder, AVIF is not
even listed, and an HDR still is drawn as SDR. The case stays open as the re-check for when that
plan ships; nothing in this pass changes it.

---

### TC-IMG-06 — a broken file shows a placeholder, not a crash

**Area:** viewer, error paths
**Preconditions:** a folder holding a truncated JPEG, a zero-byte `.png`, and a `.heic`.
**Steps:**
1. Open the folder in Woka.
2. Open each of the three files in the viewer in turn.
3. Page past them with the arrow keys and come back.
**Expected:** each shows a placeholder. The viewer stays usable, navigation still works, and the
grid does not lose its other thumbnails.
**Why this is not automated:** `ImageLoaderTest` covers that the loader throws and the facade absorbs
it. What cannot be asserted is that the *user* is left with a working screen rather than a blank one.

---

### TC-IMG-07 — a file deleted underneath the open viewer

**Area:** viewer, error paths
**Preconditions:** a folder of several images, open in the viewer on one of them. An Explorer window
on the same folder.
**Steps:**
1. With the viewer showing image 3 of 6, delete image 3 from Explorer.
2. Press the right arrow, then the left arrow.
3. Close the viewer back to Folder View.
**Expected:** no crash and no frozen frame of a file that no longer exists. Navigation continues
across the remaining files; the folder listing reflects the deletion after a refresh.
**Why this is not automated:** the race is between an external process and a live UI; reproducing
the timing in-process would be testing something else.

---

### TC-IMG-08 — a file with no read permission

**Area:** viewer, error paths
**Preconditions:** an image whose ACL denies read to the current user (`icacls <file> /deny
%USERNAME%:R`). Remember to restore it afterwards.
**Steps:**
1. Open the containing folder.
2. Attempt to open the file in the viewer.
**Expected:** a placeholder and a usable viewer, the same as any other unreadable file. No dialog
loop, no silent hang.
**Why this is not automated:** the suite must not manipulate ACLs on the machine running it.
