# Video formats — backlog

Added by the author for the same 1.0.4 pass (2026-10-24). Each block is promoted to a ticket against
the tree as it is then (`ai/docs/conventions/tickets.md`); none was measured yet — the first step of
each is the measurement named in it.

### VF-103 — Truncated and damaged files
CCTV and camera exports are often cut mid-write — `dahua.dav` is exactly that (17 frames, then a
broken one). The player must play to the cut and stop as at a normal end: no crash, no hang, no
endless spinner. First: open `dahua.dav` and the `broken.mkv` fixture in the player and record what
happens; then fix only what fails. A test on both fixtures.

### VF-104 — "Save as MP4" for formats nothing else opens
A context-menu action on `rm`, `rmvb`, `amv`, `mtv`, `dav`, `bik`, `thp`, `rpl`, `nsv`, `nuv`, `xesc`,
`h264`…: a copy beside the original as MP4 through the bundled FFmpeg — stream copy where MP4 can hold
the codec (H.264 from `dav`/raw `h264`), re-encode to H.264 with `libopenh264` + AAC otherwise. First:
whether the video editor (1.0.6, `pending/video-editor/`) already has a save/encode path to reuse
rather than a second one; the LGPL build has no x264, so openh264 is the only H.264 encoder.

### VF-105 — Rotation from metadata
Phone video stores its orientation as a `rotate` tag or a display matrix. First: whether the player,
the poster and the hover reel honour it today — record per path on a 90° and a 180° sample (generate
with the bundled FFmpeg `-display_rotation`). Fix only the paths that ignore it.
