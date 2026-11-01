# VF-103 — Truncated and damaged files

| | |
|---|---|
| **Order** | after VF-102 |
| **Depends on** | nothing |
| **Feature** | [Video formats](../video-formats.md) |

**Goal.** A file cut mid-write plays to the cut and stops as at an ordinary end; a file that cannot be
opened at all says so instead of sitting on its placeholder at 0:00.
**Why now.** CCTV, camera and phone exports are cut mid-write often; the backlog asked to measure first
and fix only what fails.

## Measured — 2026-11-01, `probe/TruncatedFileProbe`
Through the real player: 13 cut MPEG-PS/TS/EVO/MKV/DAV files (`samples/deinterlace/`, `dahua.dav`,
`broken.mkv`, generated cuts). **Every one plays to its cut and raises the end; no crash, no hang.**
What fails:
- **An `.mp4` cut before its `moov` index was written** — the phone/camera case. FFmpeg cannot open it
  (`moov atom not found`, the CLI too) and nothing can be recovered without a reference file from the
  same device. The viewer showed the placeholder and 0:00, as if still loading.
- A cut `.mkv` keeps its header's full length (4 s stated, 2.1 s there): the seek bar jumps to the end
  at the cut. Cosmetic — **left as is** (the author, 2026-11-01).

Found on the way, in VF-102: on a cut 1080i H.264 `.ts` the frame that revealed the interlacing was
shown, the play head moved, and the re-open behind the graph sought back to it — landing 1.3 s late and
dropping the run that followed (9 pictures of 40). Fixed in `FrameDelivery.pictureOf`: that frame is
held back, so the re-open starts over at 0 with no seek (40 of 40).

## Steps
1. `VideoPlayerState.openFailed` (snapshot state, `internal set`), written in `startDecoderLoop` when
   `grabber.start()` throws.
2. `video/VideoOpenFailedNotice.kt` — the existing `NoticeCard` (made `internal`), centred, for as long
   as `openFailed`. Called from `PlaybackHealthNotice`, the per-file notice both windows already call —
   `MediaViewerContent.kt` sits at 149 lines.
3. Fixtures `nomoov.mp4` (first half of a 4 s H.264 + AAC `.mp4`, index at the end) and `cut.mkv`
   (first half of the same as `.mkv`).
4. `integration/TruncatedFilesTest` — `cut.mkv` and `dahua.dav` reach the end and stop; `nomoov.mp4`
   raises `openFailed` with no frame.

## Result — 2026-11-01
**Done** as above. Tests green; the notice itself not looked at on screen.
