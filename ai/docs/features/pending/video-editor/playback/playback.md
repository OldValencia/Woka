# Feature: Video Editor — Playback

Playing an **edited** timeline. Three clips out of order have to run as one continuous picture with
continuous sound, and the author refused the cheap answer: a 0.1–0.3 s stall at every join was
offered on 2026-09-03 and turned down.

**This is the riskiest folder in the feature** and the second of its two unmeasured gates
([`../video-editor.md`](../video-editor.md) §5). Ticket ids: **ME-230…ME-239**.

---

## 1. Status

No tickets written. Blocks in [the shared backlog](../tickets/backlog.md) §230.

Depends on [`timeline/`](../timeline/timeline.md) for a document with more than one clip in it, and
on [`foundation/`](../foundation/foundation.md) for a canvas to present into.

---

## 2. Scope

**In.** Continuous picture and continuous sound across a join, in the editor's canvas. The mapping
between timeline time and source time. Per-clip speed applied to both streams. Freeze-frame clips.

**Out.** The strip and the document ([`timeline/`](../timeline/timeline.md)). Playing an edited video
in the **viewer** — that is [`save/`](../save/save.md)'s problem, because a viewer only sees an edit
once one has been written into the file, and the answer there may be different: the viewer may
legitimately choose to stall at a join where the editor may not.

---

## 3. Architecture

### 3.1 Why this is tractable at all: it is one file

Every clip comes from the same source (§3.2 of the umbrella), so across a join the resolution, pixel
format, frame rate, sample rate and channel count **cannot change**. There is no format negotiation,
no resampling and no scaler switch at the boundary — which is exactly the work that makes a
multi-file timeline a different kind of program. The hand-off is "which decoder is the presenter
draining", and nothing else.

### 3.2 Two decoders, one presenter, one sound line

```
      clip N playing                      approaching the join
   ┌──────────────┐                    ┌──────────────┐   ┌──────────────┐
   │ grabber A    │ ──frames──▶        │ grabber A    │   │ grabber B    │  seeked to clip N+1,
   └──────────────┘                    └──────────────┘   └──────────────┘  decoding into a queue
          │                                    │                 │             of 2–3 frames
          ▼                                    └────────┬────────┘
      presenter ──▶ canvas                          presenter ──▶ canvas
```

At the join the presenter switches source, A is seeked to the clip after next, and it becomes the
pre-roller. **The sound line is never closed** — one `SourceDataLine` for the whole session, fed from
whichever audio decoder is current, because closing and reopening one is an audible click even when
it is fast.

**The queue is two or three frames, not half a second.** A 4K frame is ~33 MB; a 15-frame pre-roll is
half a gigabyte, and the player has already been through one memory incident of exactly this shape
(`FrameRetention` exists because an eight-deep queue turned into gigabytes). What buys the seamless
join is that decoder B has already **paid the seek** and is running, not that it has buffered a
second of video.

**The second grabber must not open before the first has probed.** `VideoPlayerState.tracksProbed` is
a latch that exists because two concurrent first-opens of the same file intermittently returned
incomplete stream info — no duration, no audio channels. The pre-roller opens after it, always.

### 3.3 Timeline time and source time are two clocks

The strip, the readout and the play head speak **timeline** milliseconds; the decoder speaks
**source** milliseconds. One pure function each way, over the clip list:

```
timelineToSource(ms) -> (clipIndex, sourceMs)
sourceToTimeline(clipIndex, sourceMs) -> ms
```

Pure, in `commonMain`, unit-tested — and every off-by-one in this feature will live here. A clip with
a speed of ½× is twice as long on the timeline as in the source, so speed belongs in these two
functions and nowhere else; a freeze clip is a range of zero source length and a positive timeline
length, which is the degenerate case the tests must carry.

`VideoPlayerState.currentPositionMs` stays a **source** position, because that is what it means today
and what the decode path uses. The timeline position is derived. Redefining it would silently change
what every existing reader of it — the slider, the resume record, the system media controls — is
saying.

### 3.4 What was rejected

- **Render the timeline to a temp file and play that.** Every edit becomes a re-encode before it can
  be seen, which is the opposite of the instant preview the whole design is for, and it burns disk in
  proportion to the file.
- **One decoder that seeks at the join.** Refused by the author. It is also the answer that gets
  worse as the edit gets more interesting: a cut every two seconds means stalling every two seconds.
- **A `trim` + `concat` filtergraph fed one linear decode.** Elegant, and it decodes every region the
  user cut out — the cost scales with the *source*, not with the result — and it gives up cheap
  seeking inside the editor, which is the thing the strip is for.

---

## 4. Open questions — the measurements this folder is

- **How long before a join must the pre-roller start?** It is one seek plus decode-to-position, which
  `SeekPreviewSource` measured at 117–156 ms keyframes-only and 155–271 ms exact on 2160p HEVC. A
  join needs the exact path, so ~300 ms is the starting guess and it is a guess. What happens when
  two joins are 200 ms apart is the case that decides whether a pre-roll window is enough or whether
  short clips need a different route entirely.
- **Does a per-clip speed change click the audio?** The tempo path runs an `atempo` filter, and
  changing it mid-stream means rebuilding the filter graph. A rebuild between clips may be inaudible
  or may pop; nothing here has tried it.
- **Two hardware decoders on one file at once.** Whether the machine and the codec allow it at 4K, or
  whether the pre-roller has to be a software decode at reduced scale for the first frames. This is
  measurable before any of the design above is built, and it should be.
- **What the sound does across a join with a gap in the audio** — a clip whose audio is muted next to
  one that is not. Fading at the boundary is [`audio/`](../audio/audio.md)'s answer, but the *timing*
  of the fade is this folder's.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | Stall at a join, or pre-roll? | **Pre-roll a second decoder** | Author's call: the join must be seamless. It is tractable only because every clip comes from one file, so nothing about the format changes across the boundary (§3.1) |
| 2026-09-03 | How deep is the pre-roll queue? | **Two or three frames** | A 4K frame is ~33 MB and this player has already had one memory incident from a deep frame queue. What buys the join is the seek being already paid, not the buffer being deep (§3.2) |
| 2026-09-03 | Which clock is `currentPositionMs`? | **Source, unchanged; the timeline position is derived** | The slider, the resume record and the system media controls all read it today; redefining it changes what they say (§3.3) |
| 2026-09-03 | Play a rendered temp file instead? | **No** | It makes every edit wait on a re-encode, which is the opposite of the design (§3.4) |
