# Feature: Video Editor — Playback

Playing an **edited** timeline. Three clips out of order have to run as one continuous picture with
continuous sound, and the author refused the cheap answer: a 0.1–0.3 s stall at every join was
offered on 2026-09-03 and turned down.

**This is the riskiest folder in the feature** and the second of its two unmeasured gates
([`../video-editor.md`](../video-editor.md) §5). Ticket ids: **ME-230…ME-239**.

---

## 1. Status

| Ticket | Title | State |
|---|---|---|
| ME-231 | Timeline time and source time | **done 2026-11-01, without speed and freeze** (they do not exist yet). Built inside [ME-210](../timeline/tickets/ME-210-cut-the-film-on-the-strip.md): `ClipTimeline.kt` in `commonMain`, `ClipTimelineTest`. The interim seek-at-every-join walk (`ClipWalk`) is what ME-232 replaces |
| ME-230 | Probe: two decoders, one file | **done 2026-11-04** — two 4K decoders run at ~60 fps each against 24 needed; a join today stalls 0.2–1.9 s; the pre-roll lead is up to ~2.2 s, not 300 ms. Table at the end of this file; probe `jvmTest/probe/JoinDecodeProbe.kt` |
| [ME-232](tickets/ME-232-joins-without-a-stall.md) | Joins without a stall — lane pool, engine-owned list, decoder threads 4 | **built 2026-11-05**, tests green; **not yet looked at by hand** |
| [ME-233](tickets/ME-233-sound-across-a-join.md) | Sound across a join — spliced to the sample in the one audio loop | **built 2026-11-05**, tests green; **not yet heard by the author** |
| [ME-237](tickets/ME-237-side-data-stops-piling-up.md) | Side data stops piling up on the RGBA frame — a native leak of up to 1.7 GB per hour of 4K DV | **built 2026-11-05**, `FreshFrameGrabberTest` green — 5 entries at frame 300 against 1500 |

Every other block is in [the shared backlog](../backlog.md) §230.

Depends on [`timeline/`](../timeline/timeline.md) for a document with more than one clip in it, and
on [`foundation/`](../../../implemented/video-editor/foundation/foundation.md) for a canvas to present into.

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

---

## Measured — ME-230, 2026-11-04

`JoinDecodeProbe` (`shared/src/jvmTest/kotlin/com/woka/probe/`), this machine (16 threads), every grabber
opened as `openVideoGrabber` opens the player's: software decode, `threads=auto`, RGBA, no audio, output
native or scaled to 1920 wide. Two real films: **S02E01** — Netflix WEB-DL, HEVC 10-bit DV/HDR, 3840×2160,
24 fps, long GOPs; **Green Book** — 2160p HEVC, 23.976 fps, short GOPs. Five places each, 13 % … 86 %.

| | S02E01 native | S02E01 1920 | Green Book native | Green Book 1920 |
|---|---|---|---|---|
| **Join today** — one decoder, seek + first frame | 187 / 1543 / 624 / 526 / 1515 ms | 195 / 1854 / 699 / 508 / 1500 | 218 / 263 / 333 / 309 / 298 | 206 / 261 / 333 / 301 / 294 |
| Second grabber opened while A plays | 2 ms | 8 ms | 3 ms | 12 ms |
| **Pre-roll** — B seek + 3 frames while A plays | 265 – 1980 ms | 270 – 2187 | 291 – 397 | 327 – 487 |
| A's rate during B's pre-roll (flat out) | 60.4 – 62.5 fps | 137 – 170 | 57.9 – 60.5 | 98 – 119 |
| **Both run** — A and B each decode 120 frames at once | 60.3 + 59.3 fps | 147 + 128 | 59.9 + 60.1 | 93 + 91 |

**Verdict.**

- **Joins stall today, and by more than the 0.1–0.3 s the author refused.** 0.2–0.33 s on Green Book,
  **0.5–1.9 s** on the Netflix episode: a seek lands on the keyframe before the cut and decodes forward,
  and a long-GOP stream has seconds to decode.
- **Two decoders on one 4K file: yes, with room.** Both run at ~60 fps against the 24 the film needs,
  and B's seek does not pull A below ~58 fps. No hardware decoder is involved — the player decodes in
  software — so §4's "two hardware decoders" question does not arise. Scaling to 1920 does not shorten
  the seek, because the cost is the HEVC decode, not the scaler.
- **The pre-roll lead is the keyframe distance, not a constant.** §4's ~300 ms guess holds for Green
  Book and is off by 7× on S02E01. The margin has to come from the file — the distance from each join
  back to the keyframe before it (`keyframesMs`, which the render already scans) — times the measured
  decode rate.
- **Short clips are the open case.** With two decoders, A can only start pre-rolling clip N+2 after
  the join into N+1, so a clip N+1 shorter than N+2's lead (up to ~2 s here) still stalls. Whether
  that is accepted, or a third decoder is kept, was decided the same day: a pool of up to four
  (below) in ME-232, and ranges shorter than four can reach held in memory, ME-235.

### Pool size and decoder threads — `DecoderPoolProbe`, 2026-11-04

For a clip shorter than the next one's lead, two decoders are not enough. N decoders opened as the
player's at native size, each seeked to its own place; resident native memory over the JVM's
baseline, and every decoder's rate with the whole pool decoding flat out. One fresh process per
thread setting. S02E01 / Green Book:

| threads | 1 decoder, MB | 1 decoder, fps | 2, MB | 3, MB | 4, MB | slowest fps with 4 running |
|---|---|---|---|---|---|---|
| 2 | 257 / 178 | 53 / **30** | 455 / 352 | 688 / 530 | 924 / 705 | 36 / **25** |
| 4 | 332 / 266 | 87 / 48 | 642 / 550 | 934 / 812 | 1260 / 1084 | **45 / 41** |
| 8 | 497 / 422 | 92 / 68 | 960 / 906 | 1399 / 1347 | 1873 / 1801 | 52 / 42 |
| auto (16) | **832 / 719** | 91 / 67 | 1630 / 1578 | over 2 GB | — | — |

- **`threads=auto` is the expensive part, today, with one decoder.** HEVC frame threading holds a
  set of 4K frames per thread: the player's one decoder costs ~0.8 GB. The app runs `-Xmx1536m`, so
  JavaCPP's `maxPhysicalBytes` ceiling is ~3 GB for everything native; three `auto` decoders do not
  fit, and the probe's 2 GB test ceiling refused the third.
- **Four threads is the knee.** Solo it decodes S02E01 as fast as `auto` (87 vs 91 fps) and Green Book
  at 48 against 24 needed. **Four decoders at four threads cost 1.1–1.3 GB** — about what one `auto`
  and a half costs — and the slowest of them still runs at 41 fps with all four flat out.
- **Two threads is too thin**: 25 fps on Green Book with four running, on the edge of 24.
- Flat out is the worst case. In steady state one decoder plays at the film's rate and the others,
  having paid their seek, sit holding a frame.

### Reaching a frame more cheaply — `PrerollProbe`, 2026-11-05

The question behind ME-235's re-cut: rather than hold short ranges in memory, make a lane's
pre-roll cheaper. Five places, native size, landed offset after `@`. S02E01:

| | 4 threads | 2 threads |
|---|---|---|
| as `landAt` does it — every frame converted and cloned | 221 · 2454 · 948 · 673 · 2390 ms, @2–34 | 277 · 4186 · 1569 · 1108 · 3851 ms |
| decoded only, converted at the target | 243 · 2733 · 940 · 690 · 2437 ms, @2–34 | 281 · 4137 · 1593 · 1065 · 3835 ms |
| + non-reference frames skipped until 300 ms short | 241 · 2645 · 918 · 643 · 2269 ms, **@44–102** | 259 · 3173 · 1127 · 809 · 2789 ms |

Green Book reads the same at its own scale (298–486 ms at four threads).

- **The conversion costs nothing here**: JavaCV's `setTimestamp` already decodes forward to its
  target unconverted, so `landAt`'s per-frame convert-and-clone only ever touches the last frame
  or two. The whole price is decoding reference frames.
- **Skipping non-reference frames barely helps and lands on the wrong frame** — 44–102 ms past
  the target, where the join must be exact.
- **Two threads are 1.6× slower** to pre-roll; four stays.
- What would change the bound is **hardware decoding**: JavaCV does not set up a hardware device
  context for a grabber, and the player decodes in software because of the D3D11VA scaling bugs
  noted in `openVideoGrabber`. A project of its own, recorded in the backlog, not in 1.0.6.
