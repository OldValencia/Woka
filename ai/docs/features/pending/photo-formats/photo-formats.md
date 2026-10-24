# Photo formats — the rest of `next_steps.md` §2.1 (1.0.3)

**Goal.** Every still format §2.1 lists opens in the viewer, draws a grid tile, and reaches the
editor (through the existing "Convert first" save path) — on Windows **and** macOS, with nothing for
the user to install. `.psb` and `.ai` are view-only, like `.psd`.

**Out of scope.** `.thumb`, `.ei` — no specification and no sample. Moved to `next_steps.md` §3.
Video formats (§2.2) are a separate feature.

---

## 1. Who reads what

Measured 2026-10-24 against the bundled FFmpeg 8.1.2, LibRaw 0.22.2's own tools, and real files
(raw.pixls.us, `C:\Windows\Cursors`, ImageMagick 7.1.2-generated fixtures).

| Formats | Reader | Why this one |
|---|---|---|
| `exr` `dds` `xbm` `xpm` `pnm` | bundled FFmpeg | decoded correctly as-is; no new dependency |
| `psb` `ico` `cur` | TwelveMonkeys ImageIO plugins (pure Java, BSD) | FFmpeg's `psd` decoder is v1-only; its `ico` demuxer refuses `cur` (type 2) unless forced and drops alpha |
| `hdr` | Woka's own RGBE reader + an sRGB encode | FFmpeg's output is linear — mid-grey 128 came back as 55, and JavaCV hands a float frame back planar; TwelveMonkeys' reader was measured and rejected (PF-002) |
| 32 RAW extensions | LibRaw via `org.bytedeco:libraw` | FFmpeg: CR2/DNG/PEF/ARW error, NEF yields a 160×120 1-bit thumbnail, CR3/RAF/ORF/RW2 don't open. LibRaw decoded all nine samples (DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2, PEF) |
| `jxl` | libjxl, bundled native, via FFM | the bundled FFmpeg has no JPEG XL decoder; pure-Java decoders run 1–2.5 s/MP |
| `jxr` `wdp` | jxrlib, bundled native, via FFM | no decoder exists in Java, FFmpeg, or on macOS |
| `ai` | Apache PDFBox, page 1 | an `.ai` since CS is a PDF composite; pure Java |
| `paint` | Woka's own HEIF `unci` reader, pure Kotlin | Windows 11 Paint projects are HEIF: deflate-compressed `unci` RGBA8 items under an `iovl` overlay. FFmpeg 8.1 opens the container and finds 0 streams; libheif (ImageMagick) renders the samples, which gives a pixel reference |

Embedded RAW previews were measured and rejected as the *full* image: full-size on CR2/CR3/NEF/PEF,
but 1616×1080 on a 6048×4024 ARW and 1920×1440 on a 10368×7776 RW2.

## 2. Architecture

- **The routing table stays the only place that knows which library reads which format.** Every new
  reader is a `StillDecoder`; `MediaKind` gains the extensions; `StillDecoderRoutingTest` walks them.
- **TwelveMonkeys plugs into `ImageIO`.** The PSD and BMP plugins register by SPI, so `psb`, `ico`
  and `cur` route to the existing `ImageIO` decoder. An icon's frame order is the file's order,
  so the ICO path sorts entries **largest first** — frame 0 is what the viewer and the grid show.
- **FFmpeg stills keep alpha.** The grabber asks for `BGRA` instead of `BGR24`. Fixes the new `dds`
  and `xpm`, and the existing `tga`/`qoi`/`psd`, whose transparency currently renders black.
- **RAW: full decode for the viewer, LibRaw's own thumbnail for the grid.** `libraw_dcraw_process`
  costs 0.6–8 s on the samples, too slow per grid tile; the preview path asks LibRaw for its
  embedded thumbnail (`unpack_thumb`) or a half-size decode.
- **Natives are part of the app, never the user's machine.** `org.bytedeco:libraw` ships
  Windows/macOS/Linux binaries and packages like FFmpeg (`nativeOf`). libjxl and jxrlib are built
  from **pinned sources** by a checked-in script into `desktopApp/appResources/<os>-<arch>/`, which
  Compose copies into the MSI and into `Woka.app/Contents/app/resources/`. The `windows-x64` set is
  committed, so a fresh clone tests and runs with no setup; `package.yml` builds the macOS and Linux
  sets on their runners before packaging, and those are never committed.
  Loaded by absolute path from `compose.application.resources.dir` — nothing searches system paths.
- **FFM, not JNI.** JDK 25's Foreign Function API calls the C APIs directly; the launcher already
  passes `--enable-native-access=ALL-UNNAMED`. No glue library to build per platform.

## 3. Status

| Ticket | Title | Status |
|---|---|---|
| [PF-001](tickets/PF-001-ffmpeg-ready-formats.md) | FFmpeg-ready formats, with alpha | **done** 2026-10-24 |
| [PF-002](tickets/PF-002-twelvemonkeys-formats.md) | PSB, ICO, CUR through ImageIO (HDR → PF-108) | **done** 2026-10-24 |
| [PF-003](tickets/PF-003-the-rest.md) | `paint`, `hdr`, `ai`, `jxl`, `jxr`/`wdp`, 32 RAW — PF-101…108 merged | **done** 2026-10-24 (Windows; macOS natives untested) |
| PF-109 | Move to `implemented/` once the DMG is proven | backlog |

## 4. Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-10-24 | RAW: embedded preview or real decode? | LibRaw | previews are 1600–1900 px on ARW/RW2; LibRaw read every sample |
| 2026-10-24 | Natives on macOS? | bundled in the `.app`, built in CI | the user installs nothing; drag to Applications is the whole install |
| 2026-10-24 | JXL: Java fallback beside libjxl? | no | libjxl ships on both OSes, so a fallback would never run |
| 2026-10-24 | JXR: WIC on Windows or jxrlib everywhere? | jxrlib everywhere | one code path; WIC leaves macOS without the format |
| 2026-10-24 | FFmpeg stills drop alpha — fix for existing formats too? | yes | transparency rendering black is wrong for `tga`/`qoi`/`psd` as well |
| 2026-10-24 | `.thumb` `.ei` `.paint` | excluded | no spec, no sample — nothing to verify against |
| 2026-10-24 | `.paint`, reopened: two real samples | own `unci`+`iovl` reader, view-only | the samples are standard HEIF (ISO 23001-17 + 23008-12), not a closed format; no library on the classpath reads `unci` |
| 2026-10-24 | `.paint`: honour hidden layers? | compose all layers, as libheif does | Paint does not persist visibility — it resets when the project is closed, and nothing in the file records it |
| 2026-10-24 | `.paint`: bundle ImageMagick to read it? | no — own reader, ImageMagick's PNG as the test reference | ImageMagick is tens of MB of natives per OS for one format; the reader is a box walk, an inflate and an alpha blend |
| 2026-10-24 | `.hdr` through TwelveMonkeys? | no — FFmpeg + sRGB encode (PF-108) | a hard 16:1 expansion guard refuses flat images, and its tone mapper lifts mid-tones (`4F4128` → `70582C`) |
| 2026-10-24 | `.hdr`: FFmpeg + sRGB, or own reader? | own reader | RGBE is a text header and RLE lines; JavaCV returns a float frame planar, which costs more code than the format |
| 2026-10-24 | RGBE mantissa: add ½ (Radiance) or not (ImageMagick)? | add ½ | against the sRGB source: max 6 / mean 0.11 with it, 12 / 0.23 without |
| 2026-10-24 | `org.bytedeco:libraw` built for javacpp 1.5.11 under Woka's 1.5.14? | yes | loads with a version warning only; 25 real files from 24 cameras develop |
| 2026-10-24 | `.ari`, `.rwz` | dropped | LibRaw 0.21.2 refuses an Alexa Mini `.ari`; it has no Rawzor decoder at all |
| 2026-10-24 | RAW pixels through `toStillBitmap`'s PNG round trip? | no — straight into a Skia bitmap | an 81 MP RW2 broke `ImageIO`'s file cache (`EOFException`); dropping the round trip also cut full development 2–3× |
| 2026-10-24 | jxrlib: call its glue API over FFM, or a C shim? | a three-function shim built with it | jxrlib's API is structs of function pointers; a flat `decode`/`info`/`free` is the only shape FFM calls cleanly |
| 2026-10-24 | Natives in git? | no — built by `natives/*/build.*` into a gitignored folder, in CI per OS | binaries in the repo cannot be reviewed; pinned sources and hashes can |
| 2026-10-24 | Natives in git, reopened: a fresh clone failed the JXL/JXR tests until a build script ran | commit `windows-x64`; macOS/Linux stay CI-built | the author runs tests without setup steps; deleting the tests would leave the two hardest decoders unchecked. CI drops its Windows native build — the committed set is what ships |
