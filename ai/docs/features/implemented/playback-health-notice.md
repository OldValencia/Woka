# Plan: Telling the user when the machine cannot keep up

**Status: implemented, awaiting the one check that matters — that it stays silent on a machine that
copes.** All four phases are in; `PlaybackHealthTest` passes seven cases, including "a machine
keeping up is never reported".

Landed differently in two places. `hasSubtitle` was not moved to an extension after all — it was
deleted, because `tracks.hasAnySubtitle` already says the same thing without a forwarding hop, and
keeping it would have pushed two call-site files past the size limit for nothing. And the speed gate
plus the frame-rate lookup live in `recordPresentedFrame` beside the detector rather than inline in
the presenter, which keeps that loop at 143 lines.

Measure the frame rate actually being displayed. When it stays well under what the file declares,
say so — one line at the bottom of the picture — and leave it at that.

## Why this instead of fixing it

`hdr-hardware-decode-plan.md` tried to fix it, silently, by moving to a hardware decoder. It was
written on a machine with eight times the headroom it needed, so the path that mattered never once
executed there, and what shipped alongside it broke playback outright. Abandoned.

This is the opposite trade, and deliberately so:

- **It can be verified where it is written.** A false positive is visible on any machine — including
  a fast one, where the notice must never appear. That is the whole test.
- **The worst failure is a wrong sentence**, not a stuttering picture or desynchronised sound.
- It tells the truth the player already lives by. Dropping frames to hold the media clock is
  *correct* behaviour, not a malfunction — `PlayerSpeedNotice` already explains exactly this for
  playback above 1x. This extends the same honesty to a machine that is simply too slow.

It does not make anything faster. It replaces an unexplained stutter with a stated reason, which is
worth more than a fix nobody can confirm.

---

## What is measured

**Displayed frames per second**, not the decoder's skip counter.

The counter is easier to reach — `FramePacer` already keeps `skipsInARow` — but it answers "was a
conversion skipped", which happens transiently for reasons that have nothing to do with a weak
machine. Displayed frame rate is the thing the user actually perceives and the thing the message
would claim, so it is the thing to measure. A notice must not be able to be right for the wrong
reason.

Expected rate comes from the selected track's `frameRate`, which `MediaTrackProbe` already reads.

## When it must not measure

False positives are the entire risk, so the gates are wide:

| condition | why |
|---|---|
| `playbackSpeed != 1.0` | above 1x the player drops frames **by design**; `PlayerSpeedNotice` owns that case |
| paused, seeking, `isReloadingVideo` | not playback |
| first 3 s after start or any seek | the decoder is priming; every file looks slow there |
| `frameRate` missing, ≤ 0, or > 120 | a declared rate that cannot be trusted cannot be compared against |
| window not visible / minimised | Compose stops drawing; nothing is late, it is just not shown |

## The threshold

Displayed below **70%** of declared, held for **5 seconds**, and cleared as soon as it recovers.

Both numbers are chosen to under-report. 24 fps declared means the notice needs a sustained 16 fps or
worse. A machine that stutters briefly — a background job, a disk hiccup — says nothing.

## What it costs

Per presented frame: compare two `Long`s against a bucket boundary and increment an `Int`. Single
nanoseconds, 24 times a second, against the ~6.3 ms of decode and colour conversion each of those
frames already cost. That is roughly **one part in 250,000** of the frame budget — below anything a
profiler or Task Manager can separate from noise.

There is exactly one way to make it expensive, and it is worth naming so it is not done by accident:

> **The frame counter must not be Compose state.** A `mutableStateOf` written every frame recomposes
> everything that reads it at frame rate, which is how a free measurement turns into a real cost.

So: the counter and the two rates are plain fields. Only `isStruggling` — a `Boolean` that flips at
most twice per file — is Compose state, and only the notice reads it. During normal playback the
detector causes **zero** recompositions.

And once it has reported, `onFramePresented` returns on its first line for the rest of the file —
the verdict is given once, so there is nothing left to count.

---

## Phase 1: The detector ✅

- Add `video/engine/PlaybackHealth.kt`: a `@Stable` class with `fun onFramePresented(nowMs)`,
  `fun suspendMeasuring()` for the gate conditions, and an observable `isStruggling` plus the two
  numbers behind it (`displayedFps`, `expectedFps`) so the notice can quote them.
- A rolling one-second bucket count, not an average since start: a file that struggles only in a
  heavy scene must be caught, and one that struggled at open must be forgiven.
- **Clock injected**, exactly as `HardwareFallback` did. That is the one thing the abandoned plan got
  right — the injected clock let a unit test catch a real defect (`0` serving as both "not behind"
  and a valid reading) that would otherwise have shipped.

**Files:** `video/engine/PlaybackHealth.kt`

## Phase 2: Feeding it ✅

- The presenter calls `onFramePresented` where it already sets `currentFrame`, and
  `suspendMeasuring()` on the paths it already has for parked, seeking and stale-generation frames.
- That is the whole hot-path cost: one method call per presented frame, incrementing an `Int`.
- Nothing is added to the decode loop. The abandoned work put a check there and it is not needed —
  the presenter is where frames become visible, which is what is being measured.

**Files:** `video/engine/decode/VideoPlayerPresenter.kt`

## Phase 3: The notice ✅

- Add `video/PlaybackHealthNotice.kt`, styled on `PlayerSpeedNotice` — the same muted advisory
  register, not an error. Bottom of the picture, above the control bar, `AnimatedVisibility` with
  fade.
- Quote the real numbers, because they are honest and they are checkable:
  *"2160p HDR — showing about 11 of 24 frames a second. Sound stays in step; the picture pays."*
- **Shown once per opened file**, auto-hiding after ~8 s. A message that keeps returning is worse
  than no message.
- Placed by `MediaViewerContent` beside `SubtitleOverlay` and `VideoReloadOverlay`, so both the main
  window and the fullscreen one get it from one call site.

**Files:** `video/PlaybackHealthNotice.kt`, `shell/MediaViewerContent.kt`

## No setting

Considered and dropped. A notice that appears once per file, for eight seconds, only on a machine
that genuinely cannot cope, does not earn an entry in Settings — and it would save nothing measurable
either, since there is nothing to save.

If the detector ever drives a real change in behaviour (see below), *that* deserves a switch, and the
switch should be about the behaviour rather than about a line of text.

## Phase 4: Tests, and the split it forces ✅

- `PlaybackHealthTest`, machine-independent, driving the injected clock: a steady 24 fps never
  reports; a sustained 10 fps reports once after 5 s; a 2-second dip reports nothing; 10 fps at 2×
  speed reports nothing; recovery clears it.
- `VideoPlayerState` needs one property for the UI to read, and it sits at 148 lines. The delegating
  getters `hasSubtitle` and `activeCue` move out to extension properties beside
  `VideoPlayerTracks` — they only forward to `tracks` and have no business on the state object.

**Files:** `jvmTest/.../PlaybackHealthTest.kt`, `video/engine/VideoPlayerState.kt`,
`video/engine/track/VideoPlayerTracks.kt`

---

## How it gets accepted

Two checks, both doable here:

1. **On this machine the notice must never appear** — not on the 2160p HDR file, not while seeking,
   not while switching tracks, not at 2× speed. This is the important one; it is the false-positive
   test and this machine is the right place to run it.
2. Forced true once by hand (temporarily lowering the threshold) to confirm the wording, placement
   and the auto-hide look right.

What cannot be checked here is that it fires on a genuinely slow machine. That is acceptable in a way
it was not for the abandoned plan: if the detector never fires when it should, nothing breaks — the
picture behaves exactly as it does today.

---

## What this could drive later — and what it could not

Asked directly: could automatic quality reduction hang off this signal? The answer decides whether
the detector is a foundation or a dead end, so it is worth writing down rather than rediscovering.

**Switching to a lower-quality version: impossible.** That is a streaming technique and it works
because the server holds five encodes. A local file holds one. There is no lower resolution to switch
to.

**Decoding smaller: not available.** HEVC decodes at the resolution the bitstream describes. Output
size only affects swscale afterwards, and `RenderScale` already asks for the surface size.

**Dropping frames: already happening, and aimed at the wrong cost.** `FramePacer` skips the
*conversion* of a late frame but still decodes it — its own comment says conversion "is where the
time actually goes", which is true on a machine where decode is 5 ms and conversion 7. On a machine
eight times slower the proportions invert and decode dominates, so the existing mechanism barely
touches the real bottleneck.

Working the arithmetic through for a machine 8× slower than the one measured, against a 41.6 ms
budget at 24 fps:

| | ms |
|---|---|
| decode | 40 — fixed, nothing reduces it |
| swscale at 2560px | 10 |
| **total** | **50** — over budget |
| swscale at 1280px | 2.6 |
| **total** | **42.6** — still over |

Shrinking the window as far as it goes does not save the frame. Decode alone spends the budget.

**The one real lever: `skip_frame`.** Telling the decoder not to reconstruct non-reference frames at
all cuts decode itself rather than what follows it. With a typical HEVC B-pyramid that is roughly a
third to a half of frames, so 40 ms becomes ~26 ms, and 26 + 10 fits inside 41.6 — at half the frame
rate. That turns unwatchable into watchable, which is the only outcome that matters on such a
machine.

Two conditions before anyone builds it, and they are the lessons from the abandoned plan:

1. It needs a grabber re-open, which is exactly the mechanism that broke playback and was reverted.
2. Every number above is an estimate from one machine's measurements scaled by a guess. Build it from
   readings taken on a machine that actually struggles — which is precisely what this detector is for.

So the notice is not the end of the road. It is the instrument that would tell anyone whether the
road is worth walking, and what it costs.
