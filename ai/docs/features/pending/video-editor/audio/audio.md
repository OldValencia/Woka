# Feature: Video Editor — Audio

Four behaviours, asked for by name on 2026-09-03 and all four wanted: the sound follows the cuts; the
sound can be **detached** from the picture and cut on its own; an **external** audio file can be laid
over the video; and the sound can be **extracted** to a file.

Umbrella: [`../video-editor.md`](../video-editor.md) §4.4. Ticket ids: **ME-240…ME-259**.

---

## 1. Status

No tickets written. Blocks in [the shared backlog](../tickets/backlog.md) §240.

The first two blocks depend only on [`timeline/`](../timeline/timeline.md). The second row depends on
[`playback/`](../playback/playback.md), because two sound sources at once is a change to the same
audio path a seamless join is changing.

---

## 2. Scope

**In.** Mute, volume and fade in/out per clip and for the whole video. Detach audio onto its own row.
Add an external audio file. Extract the audio to a file. A waveform on the second row.

**Out.** Equalisation, compression, noise reduction, ducking, more than one external track, and
anything that needs a mixer with more than a gain per source. `SoundBoost` already exists in the
player for the one case that came up before (a quiet film), and it is a viewer control, not an edit.

---

## 3. Architecture

### 3.1 Audio following the cuts is free

The clip list is a list of source ranges, and the audio decoder reads the same ranges. Cutting the
picture cuts the sound because there is one list and both streams obey it. There is nothing to build
here beyond what [`playback/`](../playback/playback.md) builds anyway, which is why this folder starts
at "per-clip volume" rather than at "make audio follow the cut".

### 3.2 Per-clip sound is an op in the clip's layer

Mute, volume and the two fades are an ordinary tool: a manifest and four `ParamSpec`s, with **no UI
code at all**. Under **All Clips** the op lands in the file-wide layer and mutes the whole video;
under **This Clip** it lands in the selected clip's layer and mutes one clip. That is `EditLayers`
doing exactly what ME-175 built it for, with a clip id where a frame index used to be
([`../video-editor.md`](../video-editor.md) §3.3).

**Mute is still the acceptance test of the tool architecture.** The old doc said so and it stays
true: if a manifest plus one `BoolParam` is not enough to put a mute switch in the inspector, §3.4 of
the image editor's design is wrong and the fix belongs in the shell, not in a branch here.

Fades are seconds, not a curve, and there is one shape (linear-in-amplitude). A fade editor is the
kind of control that turns a simple editor into a professional one for the sake of a difference
nobody hears at 300 ms.

### 3.3 The second row exists only when asked

Author's decision, 2026-09-03: the default is one row with the sound inside it. Two things summon a
second row, and it disappears again when emptied:

- **Detach audio.** The clip's own sound stops following the picture and becomes an entry on the
  audio row, trimmable and draggable on its own. This is the operation that makes a reply carry
  across a cut.
- **Add audio…** An external file, laid on the same row.

**The row is one row, and it holds either kind of entry.** Two rows for "detached" and "external"
would be a track list, which is the line §2 of the umbrella refuses to cross. One row means one
selection concept, one waveform renderer and one set of gestures — and it is enough for every case
the brief names.

**Detaching does not duplicate the audio anywhere.** A detached entry is still a range of the same
source file; what changes is that its position on the timeline is no longer tied to the picture
clip's.

### 3.3a An entry on the second row has its own settings, and they are markup's settings

Author's point, 2026-09-03: the second row was drawn with no controls, and an entry needs the same
kind of panel a markup shape gets. It does — and **it is the same panel**, which is the useful part.

A markup shape asks three questions: *when does it start, how long does it last, how does it fade.*
A sound entry asks exactly those three and adds two of its own:

| Row | Type | |
|---|---|---|
| Volume | `FloatParam`, detent at 100 % | the entry's gain |
| Starts at | `FloatParam` in timeline seconds | where it sits; the same number a drag on the row writes |
| Fade in | `FloatParam`, detent 0 | seconds |
| Fade out | `FloatParam`, detent 0 | seconds |
| Repeat | `BoolParam` | loop until the video ends, for a track shorter than the film |

**So `TimeWindow` is one shared param group and one shared envelope function** — `(atMs, holdMs,
fadeInMs, fadeOutMs)` in, a `0f..1f` envelope for a timeline position out, in `commonMain`, called by
the canvas and by the renderer. Markup multiplies it into an alpha; audio multiplies it into a gain.
Two implementations of the same curve would drift, and the drift would show as an exported file that
does not match the preview — which is the one thing [`../render/render.md`](../render/render.md) §3.3
declares a requirement rather than an aspiration.

`holdMs` is redundant for a sound entry — its length is the entry's own range on the row, which the
user sets by dragging its edge — so the group is used with that field taken from the entry rather
than from a slider. That is a *reading* of the same four numbers, not a second type.

**The strip and the panel are two views of those numbers, and the drag vocabulary is the picture
row's own** — body moves, edge trims, left button, described once in
[`../video-editor.md`](../video-editor.md) §4.3a. A left-drag on the entry's right edge is therefore
the same gesture as a drag on a join's grip, and `Starts at` in the panel is the same number a
body-drag writes. **Not the right button**, which §4.1b spent on resetting a control.

**The envelope is drawn on the entry**, as a shaded ramp at each end with an accent line over it, so
a fade is visible where the sound is rather than only in a panel. It is the same argument as drawing
a markup shape's span on the strip.

**`Repeat` needs a stop, and the stop is the video.** A looping track ends when the timeline does;
there is no second number, because "loop three times" is a thing a user counts and a thing the
editor would then have to explain when the video gets longer.

### 3.3b Three volumes, and they are three different things

Author's note, 2026-09-03, raised against the seeker the band will grow. Naming them apart now is
cheap; discovering later that one control was doing two jobs is not.

| | Whose | Where it lives | Who hears it |
|---|---|---|---|
| **A clip's gain** | the edit | an op in that clip's layer (§3.2) | everyone who ever plays the result |
| **The whole video's gain** | the edit | the same op under **All Clips** | everyone who ever plays the result |
| **Monitoring volume** | *not* the edit | the app's own `mediaVolume`, which the viewer already persists | only the person editing, on this machine |

**The third is the one to keep out of the document.** It is the number the editor's canvas already
opens the player at (ME-207), it belongs beside the seeker in the band, and it must never reach a
recipe or a render: turning the monitor down to spare the neighbours cannot be allowed to write a
quiet file. Whoever builds the band's volume control (ME-206 or its follow-on) has to point it at
`mediaVolume` and at nothing else, and the audio tool has to leave that number alone.

The first two are one control and one op, separated by the scope switch — which is the whole point
of §3.2's rule and the reason there is no third op here.

### 3.4 An external file makes the recipe stop being self-contained, and that has to be said out loud

Every other edit in this app is a description of the file it lives in. An external track is a
**reference to a second file**, so:

- the recipe stores that path, and a moved or renamed file means the edit can no longer be rendered;
- the editor must have an honest **missing audio** state — the entry drawn as absent, with the path
  it wanted, and everything else still working;
- a path relative to the video's own folder is stored when the two are in the same folder, so moving
  a folder does not break it, and an absolute path otherwise;
- `Render video…` **bakes it in**, which is the escape: once rendered, the result needs nothing but
  itself.

This is a genuine cost, not a detail, and it is the first thing in the whole editor that can break
because of something outside the file. It is accepted because the alternative — copying the audio
into the video's metadata — would put megabytes into a slot designed for a few hundred bytes.

### 3.5 Two sources at once needs a mixer, and today there is not one

The player decodes one audio stream into one `SourceDataLine`. An external track over an unmuted
original is two streams, which means summing samples with a gain each before the line — a small,
well-understood piece of work, and one that lands in the same code
[`playback/`](../playback/playback.md) is changing for the join. The two must be sequenced, not
written in parallel by two tickets.

### 3.6 The waveform is a peak file, computed once

A row with no waveform cannot be cut usefully — the whole reason to detach audio is to see where the
speech is. Audio decodes far faster than video, so the peaks for a whole file are one pass, cached
by path plus length plus mtime, exactly as the poster is (`SaveEviction`'s key). Drawing is a
min/max column per pixel.

### 3.7 Extract is an `Output` tool

"Give me the sound as a file" is not an edit to the video, so it belongs in the `Output` group beside
Resize, and it writes with a stream copy when the codec fits the container — AAC into `m4a` — and
encodes only when it does not. It respects the cuts, because everything does.

---

## 4. Open questions

- **What Detach does to the clip's own volume op.** The picture clip and the audio entry are two
  objects after a detach, and the op was on the clip. It must travel with the sound, and "travel" is
  a rule somebody has to write.
- **Whether an external track can be longer than the video.** Trimming it to the video is the simple
  answer; extending the video with black is the other, and it is the beginning of a title card.
- **A per-clip speed change and a detached audio entry.** The picture clip gets shorter and the audio
  entry does not, because it is no longer attached. Correct, and surprising.
- **Does adding audio while the original is unmuted need a duck?** Nobody asked for one. Two gains
  and a note in the inspector is the cheap answer.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | Where do mute, volume and fades live? | **An ordinary tool's op, in the clip's layer** | It is `EditLayers` doing what it was built for, and it makes "mute everything" and "mute this clip" one control with the scope switch already beside it (§3.2) |
| 2026-09-03 | How many extra rows? | **One, holding both detached and external entries** | Two rows is a track list, which is the line the feature refuses to cross. One row is one selection, one waveform, one set of gestures (§3.3) |
| 2026-09-03 | Is a second row shown by default? | **No — only after Detach or Add** | Author's call: someone who never asks never sees it |
| 2026-09-03 | May a recipe reference another file? | **Yes, for the external track only, with a missing-audio state and a relative path where possible** | The alternative puts megabytes of audio into a metadata slot designed for hundreds of bytes. The cost is stated in §3.4 rather than discovered later |
| 2026-09-03 | Fade shape? | **Linear, in seconds, one shape** | A curve editor is a professional control for a difference nobody hears at 300 ms |
| 2026-09-03 | What settings does a second-row entry get? | **`TimeWindow` — the same group markup takes — plus volume and repeat** | The two ask the same three questions, and one envelope function called by both the canvas and the renderer is what keeps the export matching the preview (§3.3a) |
| 2026-09-03 | Does `Repeat` take a count? | **No — a loop ends when the video does** | A count is a thing the editor would then have to explain every time the video's length changed |
| 2026-09-03 | How many volumes are there? | **Three, and only two are the edit** | A clip's gain and the file's are one op under the scope switch; monitoring volume is the app's `mediaVolume` and must never reach a recipe or a render, or turning the monitor down would write a quiet file (§3.3b) |
