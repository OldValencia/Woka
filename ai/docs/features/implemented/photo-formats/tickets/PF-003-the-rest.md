# PF-003 — Every remaining photo format, one pass

| | |
|---|---|
| **Order** | 3 |
| **Depends on** | PF-001, PF-002 |
| **Feature** | [Photo formats](../photo-formats.md) |

**Goal.** `paint`, `hdr`, `ai`, `jxl`, `jxr`/`wdp` and 32 RAW extensions open in the viewer and draw
a grid tile; `paint` and `ai` are read-only.
**Why now.** The author merged backlog blocks PF-101…PF-108 into one run (2026-10-24): *«Объедини все
следующие тикеты в один»*. Their ids are retired, not reused.

## What landed
- **`.paint`** — `HeifBoxes`/`HeifMeta` (box walk), `UnciImage` (ISO 23001-17, 8-bit, raw-DEFLATE
  `cmpC`), `PaintStillDecoder` (`iovl` straight-alpha "over"). Matches libheif to 0 levels.
- **`.hdr`** — `RadianceStillDecoder`, Woka's own: new-style RLE, RGBE → linear → sRGB, clipped.
- **`.ai`** — `IllustratorStillDecoder`, PDFBox 3.0.8, page 1 at 2× (144 DPI), long edge ≤ 8192.
- **RAW** — `RawStillDecoder` over `org.bytedeco:libraw` 0.21.2-1.5.11, opened from memory (CJK-safe);
  half size for previews (`StillPreview`), full for the viewer; pixels go straight into a Skia bitmap
  (`RawBitmap`) instead of the PNG round trip.
- **`.jxl`** — `JxlNative`/`JxlSession`/`JxlStillDecoder`, libjxl 0.12 over FFM, sRGB via libjxl's CMS,
  animation as a frame set.
- **`.jxr`/`.wdp`** — `JxrStillDecoder` over `woka_jxr` (jxrlib behind a 3-function shim,
  `natives/jxr/`), FFM.
- **Natives** — `BundledNatives` loads from `compose.application.resources.dir`;
  `desktopApp/appResources/<os>-<arch>/`: `windows-x64` committed, macOS/Linux built by
  `natives/*/build.sh` in `package.yml` before packaging, never committed.

## Probe — RAW, 2026-10-24 (`RawFormatProbe`, raw.pixls.us samples)

| file | size | half ms | full ms |
|---|---|---|---|
| 3fr | 4096×4096 | 321 | 1423 |
| arw | 6024×4024 | 141 | 1775 |
| cr2 | 5796×3870 | 385 | 1934 |
| cr3 | 6742×4498 | 318 | 2380 |
| crw | 1552×1024 | 14 | 120 |
| dcr | 3040×2016 | 67 | 460 |
| dng (iPhone) | 3024×4032 | 588 | 572 |
| erf | 3040×2024 | 54 | 463 |
| fff | 7327×5502 | 609 | 3328 |
| iiq | 4490×3364 | 245 | 1255 |
| kdc | 768×512 | 5 | 32 |
| mdc | 1534×986 | 11 | 111 |
| mef | 5344×4016 | 210 | 1685 |
| mos | 5344×4008 | 333 | 1797 |
| mrw | 2288×1710 | 36 | 300 |
| nef | 6032×4032 | 345 | 1994 |
| nrw | 3664×2742 | 73 | 751 |
| orf / ori | 4640×3472 | 331 / 304 | 1391 / 1377 |
| pef | 4950×3284 | 237 | 1324 |
| raf (X-Trans) | 6246×4170 | 151 | 8156 |
| raw (Kodak) | 1216×912 | 18 | 18 |
| rw2 | 10402×7794 | 775 | 6258 |
| rwl | 2752×2754 | 82 | 606 |
| sr2 | 3925×2608 | 53 | 747 |
| **ari** (Alexa Mini) | — | — | **refused**: "Unsupported file format" → dropped |

No sample exists on raw.pixls.us for `bay`, `cap`, `dcs`, `drf`, `k25`, `ptx`, `pxn` — listed because
LibRaw claims them, **unverified**. `rwz` (Rawzor) dropped: no decoder in LibRaw.

## Not verified here
- macOS/Linux `natives/*/build.sh` — never run; the first `package.yml` run on `macos-latest` is the
  test. Without those libraries `jxl`/`jxr` fail to the placeholder on macOS; everything else is Java,
  FFmpeg or LibRaw, which already ship for macOS.
- The app itself — every decoder is covered by tests through `stillDecoderFor`, not by a manual pass.

## Done when
- `./gradlew.bat :shared:build` green — see the status table.
