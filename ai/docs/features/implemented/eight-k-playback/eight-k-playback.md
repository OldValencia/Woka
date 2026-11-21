# 8K playback acceleration

**Status:** completed and manually accepted by the author, 2026-11-12.
Baseline commit `43b7a04`.
The author approves trying to fix the two 8K/60 HEVC 150 Mbit/s samples, prioritising CPU/RAM,
colour correctness and preserving existing playback/editor behaviour. The author confirms that
playback works after manual testing and authorises committing this completed stage.

Baseline: [measurements §7](../../pending/dolby-vision-and-8k.md#7-повторное-исследование-живого-вывода--2026-11-11).
Software preparation is ~44–50 fps; the isolated viewer draws substantially fewer new frames.
GPU downscale was the first candidate; bundled filters failed its capability gate. The implemented
path uses hardware decode, reused host download and four-thread CPU conversion through the existing
rendering/retention path. FFmpeg binaries and colour coefficients are unchanged.

## Current work

| Ticket | Scope | State |
|---|---|---|
| [EK-001](tickets/EK-001-accelerated-frame-proof.md) | Capability, complete frame preparation, pixel/metadata parity | Done 2026-11-12; evidence, not a standalone player change |
| [EK-002](tickets/EK-002-viewer-integration.md) | Public-API native adapter and measured viewer playback | Done 2026-11-12; automated checks and author's manual acceptance |

Optional broader hardware coverage is recorded in [backlog](tickets/backlog.md), outside this
completed stage. No replacement of FFmpeg binaries or weakening of P5 rejection.
Unsupported hardware/OS must retain software playback. macOS/Linux are not removed.

## Measured result and scope — 2026-11-12

Two rounds of both samples in the real viewer component, small → screen-sized → small, with
seek and teardown: **59.33–60.00 newly drawn frames/s**, CPU **8.19–12.67%**. Previous software
measurements: 8.12–21.37 fps, approximately 24% CPU. Maximum measured inter-draw gap is 32.99 ms
instead of hundreds of milliseconds. This measures draw acknowledgements, not physical scanout.

Working-set snapshots are **1696–3099 MiB**; RAM reduction is not established. Decode/download and
conversion buffers are reused; existing bitmap retention and JVM heap behaviour remain. These are
short repeated tests, not a long-duration memory guarantee. There is no total RAM/VRAM budget or
free-memory admission gate. A smaller machine can still page or stutter on an 8K file.

Initial eligibility is deliberately narrow: Windows, MP4-family HEVC Main10, exactly 7680×4320,
no opened audio stream on this grabber, no rotation/RAW picture graph, RGBA output up to 3840×2160.
The two supplied samples qualify once render scale is set. Ordinary 1080p/4K, audio-containing
primary grabbers, poster/export/lane decoders, macOS and Linux keep the existing software path.
The four conversion threads are local to an active accelerated converter, never an application-wide
thread increase. Unavailable CUDA or a reported decode/conversion error uses software fallback.

All **1677 unit/integration/UI tests pass** (356 suites, no failures/errors/skips), plus opt-in
native adapter, metadata and live playback probes. No physical driver failure or low-memory machine
was available for testing. Details, reproduction and manual checks are in EK-002.

## Decisions

| Date | Question | Choice | Evidence |
|---|---|---|---|
| 2026-11-11 | Enable the existing CUDA download probe directly? | No | It retains the expensive single-threaded CPU conversion. |
| 2026-11-11 | Use `scale_cuda` in bundled FFmpeg? | Unavailable | Bundled 8.1.2 filter enumeration has no `scale_cuda`/`scale_npp`. |
| 2026-11-11 | Use bundled `scale_d3d11` on NVIDIA? | Gate failed | Device 0 is RTX 5080; output texture creation fails with HRESULT 80070057 before first output. |
| 2026-11-11 | Continue with a smaller alternative? | Probe native HEVC+CUDA download and bounded threaded conversion | Bundled CLI prepares 600 HDR frames at 2560×1440 at ~94 fps; excludes bitmap/UI and has high max RSS. |
| 2026-11-12 | Integrate which preparation path? | Native HEVC/CUDA, reused host download, four-thread legacy swscale | 69–79 fps with bitmap; all 24 pixel/metadata comparisons match, including non-empty HDR10+/DV controls. |
| 2026-11-12 | How to avoid private JavaCV mutation? | Own codec/packet/frame through public FFmpeg API, borrow public demux context | Keeps header probing and existing software fallback; seek/drain/ownership must be independently verified. |
| 2026-11-12 | Apply acceleration to ordinary films or all platforms? | No; retain the narrow measured eligibility | The author prioritises low resource use on weaker machines; 1080p/4K must not acquire additional conversion threads from this change. |
