# AP-001 — Probe: every candidate format through the player's grabber

| | |
|---|---|
| **Order** | 1 — before any code that depends on the answers |
| **Depends on** | nothing |
| **Feature** | [Audio player](../audio-player.md) — read §1, §2, §4 first |

**Goal.** Record, per candidate extension in the feature doc §4, whether the player's own grabber
setup opens it, how long it says it is, whether samples decode, where a seek lands, which tags and
cover it exposes — and from that, the final `AUDIO_EXTENSIONS`.
**Why now.** AP-002's engine shape depends on how the grabber reports an embedded cover
(`attached_pic`) and a file with no video stream; AP-003's extension list depends on which formats
decode. **Nothing is visible on screen when this lands** — it is a measurement; the status row says
so.

## Steps
1. **Fixtures, generated with the bundled `ffmpeg.exe`** (unpack per `video-formats.md` §2) into
   `shared/src/jvmTest/resources/media/audio/`, each ≤ 100 KB, 2 s of `sine=440`, and the exact
   command per file in `media/README.md`:
   `tone.mp3` (libmp3lame), `tone-cover.mp3` (same + a 64×64 PNG as `attached_pic`, tags `title`,
   `artist`), `tone.flac` (+ cover via `-disposition:v attached_pic`), `tone.m4a` (aac, + cover),
   `tone-alac.m4a`, `tone.wav`, `tone.opus`, `tone.ogg` (vorbis), `tone.wma` (wmav2), `tone.ac3`,
   `tone.amr` (libopencore_amrnb or libvo_amrwbenc), `tone.aiff`, `tone.tta`, `tone.w64`,
   `tone.caf`, `tone.mp1` / `tone.mp2`. Encoders missing from the build → the format goes to the
   download list instead, noted in the README.
2. **Third-party samples, not committed**, listed with their `curl` lines in the feature doc §4 under
   a new "Samples" heading, for what the bundle cannot encode: `ape`, `mpc`, `dts`, `mlp`/`truehd`,
   `eac3`, `spx`, `qcp`, `oma`, `ra`, `voc`, `vqf`, `xa`, `aob`, `3ga`, `m4b`, `m4r` — from
   `samples.ffmpeg.org` where present.
3. **`jvmTest/kotlin/com/woka/probe/AudioFormatProbe.kt`** (new), on the `VideoFormatProbe` pattern:
   env `WOKA_AUDIO_SAMPLES`, skips without it, probes the committed `media/audio/` fixtures always.
   Per file, opened exactly as `openAudioGrabber` and as `openVideoGrabber` open it
   (`feature/viewer/video/engine/decode/VideoPlayerGrabber.kt`):
   - demuxer, `audioCodecName`, channels, sample rate;
   - `lengthInTime` and `lengthMs(file)`; container origin;
   - **`videoStream` index and what `grabImage()` returns** — the `attached_pic` question: is the
     cover reported as a video stream, how many frames, its size;
   - samples decoded to EOF (count, last media time) and whether that agrees with the length;
   - `setTimestamp` to 50 % → the audio media time actually reached;
   - `metadata` keys present (`title`, `artist`, `album`, …) and their values.
   Prints one table row per file; asserts nothing.
4. Run it; paste the table into the feature doc as §1a, **dated**. From it, write the decision per
   extension into the feature doc's decision log (kept / cut and why) and the final list into §4.
5. Update `next_steps.md` §2.3 and §3 with the cuts already decided (tracker modules, `m4p`, `ram`,
   `alac` as a codec) and any the probe adds.

## Files
`shared/src/jvmTest/kotlin/com/woka/probe/AudioFormatProbe.kt` (new),
`shared/src/jvmTest/resources/media/audio/*` (new fixtures),
`shared/src/jvmTest/resources/media/README.md` (edit),
`ai/docs/features/pending/audio-player/audio-player.md` (edit), `next_steps.md` (edit).

## Done when
- the probe table is in the feature doc, dated, one row per sample, every §4 extension covered by a
  committed fixture or a named download — or marked "no sample found";
- the `attached_pic` answer is stated in one sentence in §3.2;
- every candidate has a kept/cut row in the decision log;
- `./gradlew.bat :shared:build` green with the new fixtures (the probe skips the downloads without
  the variable).
