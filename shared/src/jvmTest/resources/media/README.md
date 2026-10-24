# Test media corpus

Committed fixtures for `:shared:jvmTest`. Reached from Kotlin through
`com.woka.fixtures.SampleMedia`, never by absolute path.

They are checked in rather than generated at test time. ffmpeg cannot synthesise
bitmap subtitle tracks at all — it refuses text-to-bitmap transcoding — and
`lavfi` output would only approximate what real files look like: many audio
tracks sharing one language, sidecars named the way releases name them, genuine
PQ/BT.2020 colour metadata. Total size is 3.3 MB.

Names are synthetic. Nothing here carries a real title, release group or studio,
in the file names or in the stream metadata.

## Files

| File | What it is | Used for |
|------|-----------|----------|
| `WokaSample.S01E01.2160p.HDR.TEST.mkv` | h264 320×180; **8 audio tracks — 7 `rus` differing only by title, 1 `eng`**; 3 subtitle tracks, one with no language | `TrackMatching` on its hard case: language alone cannot disambiguate |
| `WokaSample.S01E01.2160p.HDR.TEST.srt` | UTF-8, exact stem | sidecar scan: matched by base name |
| `WokaSample.S01E01.2160p.HDR.TEST.ru.srt` | UTF-8 Cyrillic, language suffix | sidecar scan + charset |
| `WokaSample.S01E01.2160p.HDR.TEST.rus.srt` | **CP1251** Cyrillic | `SubtitleFileCharset`: non-UTF-8 must not garble |
| `WokaSample.S01E02.2160p.HDR.TEST.mkv` | h264 320×180, 1 audio `eng` | second stem, so E01's sidecars must not attach to it |
| `WokaSample.S01E02.2160p.HDR.TEST.en.srt` | UTF-8, language suffix | sidecar scan on the second stem |
| `WokaSample.S01E02.2160p.HDR.TEST.ass` | ASS with three styles, `{\i1}`/`{\b1}` overrides, `\N` | ASS parsing and rendering |
| `WokaSample.S01E03.2160p.HDR.TEST.sup` | raw HDMV PGS | external bitmap subtitles |
| `WokaSample.S01E04.2160p.HDR.TEST.idx` / `.sub` | VobSub pair | external bitmap subtitles |
| `WokaSample.BitmapSubs.mkv` | h264 1920×1080, 10:20; embedded `hdmv_pgs_subtitle` **and** `dvd_subtitle` | `BitmapSubtitleSource`, `BitmapSubtitleIndex` |
| `WokaSample.DualVideo.mkv` | **two video streams** — ffv1 `yuv420p10le` PQ/BT.2020 and h264 8-bit BT.709; audio `aac`(eng) + `ac3`(rus, titled); two `subrip` | video-track switching, `readHdrInfo` on real colour metadata |
| `textsubs.mkv` | E02's picture and audio plus **two `subrip` tracks that carry packets** — `eng` and `rus` | `decodeEmbeddedSubtitles` on the text path |
| `chapters.mkv` | 3 chapters: 0–0.8, 0.8–2.0, 2.0–3.0 s | `MediaChapterProbe`, chapter ticks |
| `noaudio.mkv` | video, no audio stream | volume and media keys on the boundary case |
| `broken.mkv` | first 2 KB of a valid container | error paths |
| `still.png` / `still.jpg` / `still.webp` | 640×480 | `ImageLoader` |
| `still.exr` / `still.dds` / `still.xbm` / `still.xpm` / `still.pnm` | 64×48 `gradient:red-blue` (red top, blue bottom); `dds` DXT5 with alpha fading to 0 at the left, `xpm` with `None` in the left half | photo-formats PF-001: the FFmpeg-read formats, channel order and alpha |
| `still.ico` / `still.cur` | **3** entries: 16 and 32 as 32-bit BMP, then 256 as PNG — largest **last**; a `#3478F6` circle on transparency. `cur` is the same bytes with type 2 and hotspot 3,5 | PF-002: largest-first frame order, alpha, PNG-in-ICO |
| `still.psb` | 64×48 `gradient:red-blue`, 8-bit, PSB (version 2) | PF-002: the large-document variant FFmpeg cannot read |
| `still.paint` / `multilayer.paint` | real Windows 11 Paint projects (the author's, cleared for the repo): one layer 830×500; four canvas-sized layers 1022×575 | PF-003: the HEIF `unci` + `iovl` reader, against `reference/*.paint.png` — ImageMagick/libheif's composite |
| `still.hdr` / `flat.hdr` | Radiance RGBE 64×48 written by ImageMagick: from `plasma:fractal -seed 7`, and from `gradient:red-blue` (compresses 56:1) | PF-003: `reference/still.hdr.png` is the **sRGB source**, not ImageMagick's linear read-back |
| `still.ai` | a 200×100 pt PDF-compatible `.ai` from ImageMagick: white, a `#3478F6` rectangle inset 20 pt | PF-003: page 1 through PDFBox at 2× |
| `jxl-*.jxl` | 64×48, encoded by libjxl 0.12's `cjxl` — lossy, lossless, alpha, 3-frame animation (100 ms), recompressed JPEG | PF-003: expected corners are `djxl`'s decode; values and commands in `natives/jxl/README.md` |
| `jxr-*.jxr` | 64×48, written by Windows' own WIC encoder — 8-bit, alpha, lossy, 16-bit gray, half-float HDR | PF-003: against `reference/jxr-*.jxr.png`, WIC's own decode |
| `still.bmp` | 160×120, uncompressed | §5.1's bucket C: the one format with neither a recipe slot nor an encoder. Small because a BMP is uncompressed — at 640×480 it would be 900 KB of a 3.3 MB corpus, and nothing asks about its pixels |
| `exif_rot90.jpg` | `still.jpg` with EXIF `Orientation=6` | **F3**: a correct loader returns 480×640 |
| `burst.heic` | **5** HEIC images in one container, 320×240 | frame sets: the HEIC burst case |
| `livephoto.heic` / `livephoto.mov` | still plus a 1.5 s hevc clip sharing a stem | Live Photo — the motion frame is a **sibling file**, not a track |
| `still.avif` | single-frame AVIF, 640×480, SDR | can anything open AVIF at all |
| `anim.avif` | **8** frames, 320×240, in **two** streams — a 1-frame cover item and the 8-frame animation | animated AVIF: "one stream per picture" is wrong, and only the frame count tells the two streams apart |
| `anim.gif` / `anim.webp` | **8** frames, 320×240, 120 ms each | animated stills that must not freeze on frame 1 |
| `multipage.tif` | **3** pages, 320×240, deflate | multi-page TIFF |
| `hdr_pq.avif` / `hdr_hlg.avif` | **SDR pixels wearing HDR tags — see the warning below** | tag *reading* only; useless for anything about pixels |
| `hdr_real_pq.avif` | **the one with real HDR pixels** — a frame of `DualVideo`'s PQ stream, re-encoded PQ AVIF 320×180 | anything about how HDR pixels are decoded, tone mapped or drawn |
| `hdr_pq.png` | same warning — `rgb48be` SDR data with a `cICP` chunk | tag reading on the PNG path |

> **These three files are not HDR pictures.** `setparams` writes the colour tags and does not touch a
> single pixel, so what they contain is an ordinary SDR `testsrc2` pattern that merely *claims* PQ or
> HLG. Proof: `hdr_pq.avif` and `hdr_hlg.avif` are pixel-identical — `ffmpeg -lavfi psnr` reports
> **inf** — and two genuinely different transfer curves cannot encode the same scene into the same
> numbers. Measured 2026-08-17.
>
> The consequence: a viewer that honours the tag reads their white as PQ code 1.0, which *means*
> 10000 nits. A frame cut from a real HDR master peaks at **144** (0.565 → 174 nits) where these peak
> at **255**, so these files drive the whole draw path far past anything real content reaches — useful
> for finding where it breaks, useless for judging whether it looks right. They did in fact expose a
> genuine `HdrShader` bug that way on 2026-08-18 (gamut matrix before the tone curve); the bug was
> ours, not theirs.
>
> So those three test that a transfer tag is *read*, and nothing else — which is still worth having,
> and they remain the fixtures for the tag path.
>
> **`hdr_real_pq.avif` is the genuine one** (added 2026-09-03). The corpus turned out to hold real HDR
> content all along: `WokaSample.DualVideo.mkv` stream `v:0` is ffv1 `yuv420p10le` PQ/BT.2020, and a
> frame cut from it and re-encoded PQ AVIF measures **YMAX 606/1023 = 0.592** against **YAVG 0.205** —
> a real PQ distribution, and the 0.579 of the source plus what lossy AV1 adds. Set it beside the
> three above, which reach 1.0, and the difference is the whole point.
>
> **Its limits, so nobody over-reads it.** 320×180, one scene, one master. Enough to measure a white
> point and to show that a tone curve normalised by `W = 11.2` compresses an image with a fraction of
> that range (ME-120). Not enough to judge a curve across a variety of content — that still wants a
> photograph somebody took.
| `tools/pgs_gen.py`, `tools/vobsub_idx.py` | generators for the `.sup` and `.idx`/`.sub` pair | how the bitmap subtitle files were produced |
| `tools/frameset_gen.py` | generator for every frame-set fixture above | how the HEIC/AVIF/animation/HDR files were produced |

## Provenance

`.sup`, `.idx`/`.sub`, `BitmapSubs.mkv` and the sidecar subtitles were authored by
hand (see `tools/`) and copied in unchanged apart from the rename.

The rest were derived with the ffmpeg bundled by Bytedeco. `$FF` is that binary,
`$SRC` the original file, `$D` this directory.

Downscaled derivations — chapters were dropped where the source's ran past the
new duration, and colour tags were set per stream so the SDR stream is not
mislabelled:

```sh
# DualVideo: keep the stream layout, lose the resolution
"$FF" -y -t 4 -i "$SRC" \
  -map 0:v:0 -map 0:v:1 -map 0:a:0 -map 0:a:1 -map 0:s:0 -map 0:s:1 -map_chapters -1 \
  -filter:v:0 "scale=320:180,fps=12" -filter:v:1 "scale=320:180,fps=12" \
  -c:v:0 ffv1 -pix_fmt:v:0 yuv420p10le \
  -color_trc:v:0 smpte2084 -color_primaries:v:0 bt2020 -colorspace:v:0 bt2020nc \
  -c:v:1 libopenh264 -pix_fmt:v:1 yuv420p \
  -color_trc:v:1 bt709 -color_primaries:v:1 bt709 -colorspace:v:1 bt709 \
  -c:a:0 aac -b:a:0 32k -ac:a:0 2 -c:a:1 ac3 -b:a:1 96k -ac:a:1 2 -c:s copy \
  -metadata:s:v:0 "title=HDR PQ BT.2020 (10-bit)" \
  -metadata:s:v:1 "title=SDR BT.709 (8-bit)" \
  "$D/WokaSample.DualVideo.mkv"

# E01: all audio tracks kept, every title replaced with a neutral one
"$FF" -y -t 3 -i "$SRC" -map 0:v:0 -map 0:a -map 0:s \
  -map_chapters -1 -map_metadata -1 \
  -vf "scale=320:180,fps=12" -c:v libopenh264 -pix_fmt yuv420p \
  -color_trc bt709 -color_primaries bt709 -colorspace bt709 \
  -c:a aac -b:a 32k -ac 2 -c:s copy \
  -metadata:s:a:0 "language=rus" -metadata:s:a:0 "title=Dub A" \
  ... -metadata:s:a:7 "language=eng" -metadata:s:a:7 "title=Original" \
  "$D/WokaSample.S01E01.2160p.HDR.TEST.mkv"
```

Built from the E02 stand-in:

```sh
"$FF" -y -i "$E2" -map 0:v:0 -an -c:v copy "$D/noaudio.mkv"
"$FF" -y -i "$E2" -f ffmetadata -i chapters.txt -map 0:v:0 -map 0:a:0 \
      -map_metadata 1 -c copy "$D/chapters.mkv"
head -c 2048 "$E2" > "$D/broken.mkv"
```

Added in Phase 3. Every container above was cut to three or four seconds, which
sliced out every subtitle event: `S01E01` and `DualVideo` still *declare* their
`subrip` tracks and a probe lists them, but a demux pass yields no packets, so
the embedded-text path had no fixture at all. Text subtitles are the one thing
ffmpeg will mux happily, so this is muxed rather than worked around:

```sh
"$FF" -y -i "$D/WokaSample.S01E02.2160p.HDR.TEST.mkv" \
      -i "$D/WokaSample.S01E02.2160p.HDR.TEST.en.srt" \
      -i "$D/WokaSample.S01E01.2160p.HDR.TEST.ru.srt" \
      -map 0:v:0 -map 0:a:0 -map 1:0 -map 2:0 -c:v copy -c:a copy -c:s srt \
      -metadata:s:s:0 "language=eng" -metadata:s:s:0 "title=English" \
      -metadata:s:s:1 "language=rus" -metadata:s:s:1 "title=Русские" \
      "$D/textsubs.mkv"
```

Stills:

```sh
"$FF" -y -f lavfi -i "testsrc2=size=640x480" -frames:v 1 -c:v png     "$D/still.png"
# PF-001 fixtures — ImageMagick 7.1.2 ($M = magick), since the bundled ffmpeg writes no dds/xpm:
"$M" -size 64x48 gradient:red-blue base.png
"$M" base.png -compress zip "$D/still.exr"
"$M" base.png -alpha set -channel A -fx "i/w" +channel -define dds:compression=dxt5 -define dds:mipmaps=0 "$D/still.dds"
"$M" base.png -threshold 50% "$D/still.xbm"
"$M" base.png -colors 4 -alpha set -channel A -fx "i<32?0:1" +channel "$D/still.xpm"
"$M" base.png -depth 8 "$D/still.pnm"
# PF-002 — the 256 entry is spliced in as PNG by a few lines of Python (struct-packed
# ICONDIR, entries appended in order; for .cur the type is 2 and planes/bpp hold the hotspot):
"$M" -size 256x256 xc:none -fill "#3478F6" -draw "circle 128,128 128,8" icon.png
"$M" icon.png -define icon:auto-resize=16,32 small.ico   # then small.ico entries + icon.png
"$M" -size 64x48 gradient:red-blue -depth 8 "$D/still.psb"
"$FF" -y -f lavfi -i "testsrc2=size=640x480" -frames:v 1 -c:v mjpeg   "$D/still.jpg"
"$FF" -y -f lavfi -i "testsrc2=size=640x480" -frames:v 1 -c:v libwebp "$D/still.webp"
```

`exif_rot90.jpg` — ffmpeg cannot write EXIF orientation, so a 36-byte APP1
segment is spliced in right after the SOI marker: marker `FFE1`, length `0022`,
`Exif\0\0`, a big-endian TIFF header, one IFD0 entry (tag `0112` Orientation,
type SHORT, count 1, value 6), then a null next-IFD pointer.

```sh
head -c 2 "$D/still.jpg" > "$D/exif_rot90.jpg"
printf 'FFE100224578696600004D4D002A0000000800010112000300000001000600000000' \
  | xxd -r -p >> "$D/exif_rot90.jpg"
tail -c +3 "$D/still.jpg" >> "$D/exif_rot90.jpg"
```

## Frame sets

Added for `rare-image-formats-and-frame-sets.md` Phase 0. These are the one
group **not** built by the Bytedeco ffmpeg, because it cannot build them: no
avif muxer, and no video encoder past openh264. `tools/frameset_gen.py` writes
all ten from `pillow` + `pillow-heif` (whose wheel carries an x265 HEIF
encoder) and a system ffmpeg with `libaom-av1` and `libx265`:

```sh
python tools/frameset_gen.py "$D"
```

Total 148 KB. Two things the script had to work out, kept here so the next
person does not:

- `-color_trc`/`-color_primaries` as **output** options reach neither the AVIF
  `colr` box nor the PNG `cICP` chunk — libaom and the png encoder both read
  the frame's own side data, and only `setparams` in the filter chain puts it
  there. Tagged the output way, `hdr_pq.avif` and `hdr_hlg.avif` come out
  byte-identical and both say `color_transfer=unknown`.
- A **Live Photo is two files**. Apple exports a HEIC and a MOV sharing a stem;
  there is no motion track inside the container to find. `livephoto.heic` and
  `livephoto.mov` are that pair, so whatever resolves the `Motion` frame is
  tested against the shape it actually meets on disk.

**`hdr_real_pq.avif`** — one frame of the PQ stream this corpus already had, re-encoded so a still
carries real HDR pixels (ME-172, 2026-09-03). Written with the **bundled Bytedeco ffmpeg** rather
than a system one, because that is the encoder Woka itself runs:

```sh
"$FF" -i "$D/WokaSample.DualVideo.mkv" -map 0:v:0 -frames:v 1   -c:v libaom-av1 -still-picture 1 -pix_fmt yuv420p10le   -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc   -crf 20 "$D/hdr_real_pq.avif"
```

4.9 KB. Two things worth keeping, for the same reason as the notes above:

- **The output tags stick here, unlike above.** `libaom-av1` is re-encoding a frame that already
  carries PQ side data from the source stream, so the `colr` box comes out right without `setparams`
  in the chain. The three tags-only files needed the filter precisely because their source had
  nothing to carry.
- **AVIF and nothing else.** `libx265` cannot open a HEIF output at all, and `-color_trc
  arib-std-b67` is silently ignored, so HLG is not an offerable target — both measured in ME-010.

