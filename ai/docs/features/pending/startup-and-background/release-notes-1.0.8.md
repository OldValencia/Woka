# 1.0.8 — Faster start, far less memory

Measured on the author's machine (Ryzen 7 9800X3D, RTX 5080, Windows 11), warm file cache,
medians of 5 alternating launches unless noted. "Before" is the 1.0.8 branch at the start of
the work, which behaves as 1.0.7. Details and methods: [ST-001](tickets/ST-001-startup-measurements.md).

## Start-up

| | Before | After |
|---|---:|---:|
| Double-click → photo on screen (through `Woka.exe`) | 802 ms | 612 ms¹ |
| Java start → photo on screen | 453 ms | **215 ms** — the photo is in the very first frame |
| First launch after install or update | ~8.8 s | no extra wait — the start-up cache ships in the installer |
| First video poster when opening a folder | 790 ms | 643 ms |

¹ Measured before the last two start-up improvements (window and first frame), which took a
further ~240 ms off; the double-click figure is to be re-measured.

## Memory (private / physical)

| | Before | After |
|---|---:|---:|
| Empty window | 376 / 176 MB | 352 / 164 MB |
| Folder of 16 heavy videos, peak while thumbnails build | 5.0 / 3.75 GB | 1.7 / 1.4 GB |
| Same folder, idle | 735 / 482 MB | 420 / 224 MB |
| Opening a 4K video, peak | 5–21 GB | ~2.4 / 1.5 GB |
| Playing 4K video | 1.7–18 GB / 0.7–17 GB | 1.1 GB / **409 MB** |
| After closing a video | 1086 / 360 MB | 918 / **238 MB** |

## What changed

- Video frames are freed as soon as they are no longer drawn, even when nothing is on screen;
  4K playback no longer depends on the garbage collector's timing.
- A closed video's player is fully released (a subtitle loop used to keep it alive).
- Thumbnail, probe and cover decoders use 2 threads instead of one per CPU core.
- Idle memory is returned to the system; the initial heap no longer reserves ~500 MB.
- Opening a JPEG no longer loads FFmpeg at all.
- A start-up cache (JEP 483 AOT) is built at packaging time and installed with Woka.
- The GPU driver is warmed up while the window is built; Java2D's unused Direct3D 9 is off.
- FFmpeg's libraries load from a folder instead of being searched for inside the app's jars.
