"""Generates the frame-set fixtures for `:shared:jvmTest`.

    python frameset_gen.py <output-dir>

Needs `pillow`, `pillow-heif` (its wheel carries an x265 HEIF encoder) and an
ffmpeg on PATH that can encode AV1. The Bytedeco build cannot do either job:
it has no avif muxer and no encoder beyond openh264, which is why these files
are not produced by the same binary as the rest of the corpus.

Every plate is a flat colour with its own index painted across it, so a frame
strip showing the wrong frame is wrong at a glance rather than on inspection.

Colour tagging note: `-color_trc`/`-color_primaries` as output options reach
neither the AVIF `colr` box nor the PNG `cICP` chunk — libaom and the png
encoder both take the frame's own side data. `setparams` in the filter chain
puts it there, and that is the only spelling of this that works.
"""

import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw
import pillow_heif

pillow_heif.register_heif_opener()

W, H = 320, 240
BURST_FRAMES = 5
ANIM_FRAMES = 8
TIFF_PAGES = 3
FFMPEG = "ffmpeg"

PALETTE = [
    (198, 40, 40), (216, 133, 27), (46, 125, 50),
    (21, 101, 192), (106, 27, 154), (0, 121, 107),
    (191, 54, 12), (69, 90, 100),
]


def plate(index, total, width=W, height=H):
    """A flat colour with `index/total` painted large in the middle."""
    background = PALETTE[index % len(PALETTE)]
    image = Image.new("RGB", (width, height), background)
    draw = ImageDraw.Draw(image)
    label = f"{index + 1}/{total}"
    box = draw.textbbox((0, 0), label)
    text = Image.new("RGB", (box[2] - box[0], box[3] - box[1]), background)
    ImageDraw.Draw(text).text((-box[0], -box[1]), label, fill=(255, 255, 255))
    text = text.resize((text.width * 6, text.height * 6), Image.NEAREST)
    image.paste(text, ((width - text.width) // 2, (height - text.height) // 2))
    draw.rectangle([0, 0, width - 1, height - 1], outline=(255, 255, 255), width=2)
    return image


def series(total):
    return [plate(i, total) for i in range(total)]


def ffmpeg(*args):
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error", *args],
                   check=True, capture_output=True)


def burst_heic(path):
    frames = series(BURST_FRAMES)
    frames[0].save(path, format="HEIF", save_all=True,
                   append_images=frames[1:], quality=70)


def live_photo(still, motion):
    """An Apple Live Photo is a pair on disk, not one file: HEIC plus MOV."""
    plate(0, 1).save(still, format="HEIF", quality=70)
    ffmpeg("-f", "lavfi", "-i", f"testsrc2=size={W}x{H}:rate=15:duration=1.5",
           "-c:v", "libx265", "-crf", "34", "-pix_fmt", "yuv420p",
           "-tag:v", "hvc1", "-an", str(motion))


def still_avif(path):
    plate(0, 1, 640, 480).save(path, format="AVIF", quality=60)


def anim_gif(path):
    frames = series(ANIM_FRAMES)
    frames[0].save(path, format="GIF", save_all=True,
                   append_images=frames[1:], duration=120, loop=0)


def anim_webp(path):
    frames = series(ANIM_FRAMES)
    frames[0].save(path, format="WEBP", save_all=True,
                   append_images=frames[1:], duration=120, loop=0, quality=60)


def multipage_tiff(path):
    frames = series(TIFF_PAGES)
    frames[0].save(path, format="TIFF", save_all=True,
                   append_images=frames[1:], compression="tiff_deflate")


def hdr_avif(path, transfer):
    ffmpeg("-f", "lavfi", "-i", "testsrc2=size=640x480", "-frames:v", "1",
           "-vf", "format=yuv420p10le,setparams="
                  f"color_primaries=bt2020:color_trc={transfer}:colorspace=bt2020nc",
           "-c:v", "libaom-av1", "-crf", "40", "-f", "avif", str(path))


def hdr_png(path, transfer):
    ffmpeg("-f", "lavfi", "-i", "testsrc2=size=640x480", "-frames:v", "1",
           "-vf", "format=rgb48be,setparams="
                  f"color_primaries=bt2020:color_trc={transfer}",
           "-c:v", "png", str(path))


def main():
    out = Path(sys.argv[1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    jobs = [
        ("burst.heic", lambda: burst_heic(out / "burst.heic")),
        ("livephoto.heic",
         lambda: live_photo(out / "livephoto.heic", out / "livephoto.mov")),
        ("still.avif", lambda: still_avif(out / "still.avif")),
        ("anim.gif", lambda: anim_gif(out / "anim.gif")),
        ("anim.webp", lambda: anim_webp(out / "anim.webp")),
        ("multipage.tif", lambda: multipage_tiff(out / "multipage.tif")),
        ("hdr_pq.avif", lambda: hdr_avif(out / "hdr_pq.avif", "smpte2084")),
        ("hdr_hlg.avif", lambda: hdr_avif(out / "hdr_hlg.avif", "arib-std-b67")),
        ("hdr_pq.png", lambda: hdr_png(out / "hdr_pq.png", "smpte2084")),
    ]
    for name, job in jobs:
        try:
            job()
            print(f"OK    {name:16} {(out / name).stat().st_size:>8} bytes")
        except Exception as error:
            detail = (getattr(error, "stderr", b"") or b"").decode(errors="replace")
            print(f"FAIL  {name:16} {error} {detail[:400]}")


if __name__ == "__main__":
    main()
