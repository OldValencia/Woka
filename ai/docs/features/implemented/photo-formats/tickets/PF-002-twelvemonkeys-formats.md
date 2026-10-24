# PF-002 — PSB, ICO, CUR, HDR through ImageIO

| | |
|---|---|
| **Order** | 2 |
| **Depends on** | nothing (independent of PF-001) |
| **Feature** | [Photo formats](../photo-formats.md) |

**Goal.** Add the TwelveMonkeys PSD, BMP and HDR plugins and route `psb`, `ico`, `cur`, `hdr` to
`ImageIoStillDecoder`; `psb` is read-only; an icon shows its largest entry first.
**Why now.** Four formats, one pure-Java dependency, no natives — the cheapest slice after PF-001.

## Steps
1. `gradle/libs.versions.toml` — `twelvemonkeys = "3.15.3"`; libraries `twelvemonkeys-psd`,
   `twelvemonkeys-bmp`, `twelvemonkeys-hdr` (`com.twelvemonkeys.imageio:imageio-*`).
   `shared/build.gradle.kts` `jvmMain` — add the three.
2. Measure first (a probe, output recorded in this ticket with the date): the HDR reader's default
   tone mapping on `grey.hdr` (sRGB 128 must come back 120–136), and that `ico`/`cur` keep alpha. If
   either fails, stop and report — do not tune a tone mapper by eye.
3. `ImageIoStillDecoder.kt` — for `ico`/`cur`, map frame index → entry index sorted by area
   descending, so frame 0 is the largest entry; `nativeSize`, `frameSet`, `frame` all go through the
   mapping. If this pushes the file past 150 lines, the mapping moves to `IconEntryOrder.kt` first.
4. `StillDecoders.kt` — `IMAGE_IO_EXTENSIONS` += `psb`, `ico`, `cur`, `hdr`.
5. `MediaKind.kt` — the four into `IMAGE_EXTENSIONS` and `COIL_BLIND_EXTENSIONS`; `psb` into
   `READONLY_IMAGE_EXTENSIONS`; `ico`, `cur` into `FRAME_CAPABLE_EXTENSIONS`.
6. Fixtures `still.psb` (ImageMagick), `still.ico` (16/32/256 entries, 16 first), `still.cur`
   (copied from `aero_arrow.cur`'s shape — generated, not the Windows file), `grey.hdr`; README rows.
7. Tests: `StillDecoderRoutingTest` (four → `ImageIoStillDecoder`), `NewStillFormatsTest` (frame 0
   of `still.ico` is 256×256; `psb` decodes; `grey.hdr` mid-grey in range),
   `ReadOnlyMediaEditNoticeTest` (`psb`), `WindowsFileAssociationsTest` count +4,
   `desktopApp/build.gradle.kts` four association rows.

## Files
`gradle/libs.versions.toml`, `shared/build.gradle.kts`,
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/ImageIoStillDecoder.kt` (edit),
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/StillDecoders.kt` (edit),
`shared/src/commonMain/kotlin/com/woka/core/data/MediaKind.kt` (edit),
tests and fixtures as in steps 6–7, `desktopApp/build.gradle.kts` (edit)

## Done when
- `.psb` opens and the edit button shows the read-only notice;
- a multi-size `.ico` opens on its largest entry, transparent areas show the backdrop;
- `.hdr` is not darker than the same picture as PNG;
- `./gradlew.bat :shared:build` green.

## Probe — 2026-10-24
TwelveMonkeys 3.15.3, plain `ImageIO.getImageReaders` → `read`, fixtures as committed:

| file | reader | images | result |
|---|---|---|---|
| `still.ico` | `ICOImageReader` | 3 | 16, 32 (BMP), 256 (PNG); corner `00000000`, centre `ff3478f6` — alpha kept |
| `still.cur` | `CURImageReader` | 3 | same as `ico` |
| `still.psb` | `PSDImageReader` | **2** | composite + its one layer → frames trimmed to the composite |
| `grey.hdr` / vertical-gradient `.hdr` | `HDRImageReader` | — | **refused**: "allocation of 36864 bytes, exceeding 657 bytes of input at a maximum expansion ratio of 16:1" — a hard-coded guard, not configurable |
| `plasma.hdr` (11 KB) | `HDRImageReader` | 1 | decodes, but the default tone mapper lifts mid-tones: source `4F4128` → `70582C` |

**`hdr` fails step 2 on both counts and is dropped from this ticket** — `imageio-hdr` is not added.
It moves to PF-108.
