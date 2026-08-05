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
| `chapters.mkv` | 3 chapters: 0–0.8, 0.8–2.0, 2.0–3.0 s | `MediaChapterProbe`, chapter ticks |
| `noaudio.mkv` | video, no audio stream | volume and media keys on the boundary case |
| `broken.mkv` | first 2 KB of a valid container | error paths |
| `still.png` / `still.jpg` / `still.webp` | 640×480 | `ImageLoader` |
| `exif_rot90.jpg` | `still.jpg` with EXIF `Orientation=6` | **F3**: a correct loader returns 480×640 |
| `tools/pgs_gen.py`, `tools/vobsub_idx.py` | generators for the `.sup` and `.idx`/`.sub` pair | how the bitmap subtitle files were produced |

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

Stills:

```sh
"$FF" -y -f lavfi -i "testsrc2=size=640x480" -frames:v 1 -c:v png     "$D/still.png"
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
