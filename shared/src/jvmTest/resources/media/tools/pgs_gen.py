"""Generates a raw HDMV PGS (.sup) subtitle fixture.

FFmpeg has no PGS encoder and refuses text->bitmap conversion, so the bitmaps are
rendered with drawtext into raw gray8, thresholded into a 3-colour indexed image
here, RLE-encoded and wrapped in PGS segments by hand.
"""
import os
import struct
import subprocess
import sys

W, H = 1920, 1080
# Copied next to this script: a drive letter's colon cannot be escaped reliably
# through ffmpeg's filter parser, so the font is referenced by bare filename.
FONT = "arial.ttf"
FONT_SIZE = 52
MARGIN_BOTTOM = 90

# (start_ms, end_ms, text) — mirrors the .srt fixtures so the same seek points work.
CUES = [
    (1000, 6000, "[PGS] If you can read this, the bitmap path works."),
    (7000, 12000, "Single short line."),
    (13000, 19000, "Three lines in one cue.\nThe overlay must keep all of them,\nwithout eating the line breaks."),
    (20000, 26000, "A long line, wide enough to test a wide bitmap without clipping."),
    (27000, 32000, "Punctuation: 100% - \"quotes\", 'apostrophe'."),
    (33000, 38000, "Kirillica: yozhik syel schuku."),
    (40000, 45000, "Gap before this one: 2 s of nothing."),
    (60000, 65000, "One minute mark."),
    (300000, 308000, "Five minute mark - seek here to test\nthat the lookup follows the play head."),
    (600000, 606000, "Ten minute mark."),
]


def render_gray(text: str) -> bytes:
    """One frame of white text on black, as raw gray8 at W*H."""
    with open("cue.txt", "w", encoding="utf-8") as f:
        f.write(text)
    draw = (
        # expansion=none: a bare '%' in the text is otherwise taken as a
        # strftime/expression escape and the whole draw silently produces nothing.
        f"drawtext=textfile=cue.txt:fontfile={FONT}:fontsize={FONT_SIZE}:expansion=none"
        # The border is drawn mid-grey, not black: on a black canvas a black
        # outline is indistinguishable from "no pixel". It becomes palette index 2,
        # which the palette then defines as opaque black — the usual PGS look, and
        # it exercises two genuinely different palette entries.
        f":fontcolor=white:borderw=4:bordercolor=0x9A9A9A"
        f":x=(w-text_w)/2:y=h-text_h-{MARGIN_BOTTOM}:line_spacing=8"
    )
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-f", "lavfi", "-i", f"color=c=black:s={W}x{H}:d=0.04",
         "-vf", draw, "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "gray", "-"],
        capture_output=True, check=True
    )
    return out.stdout


def to_indexed(gray: bytes):
    """Crops to the text and maps luma to 0=transparent, 1=white, 2=black edge."""
    rows = [gray[y * W:(y + 1) * W] for y in range(H)]
    ys = [y for y, r in enumerate(rows) if max(r) > 20]
    if not ys:
        raise RuntimeError("nothing rendered")
    xs_min, xs_max = W, 0
    for y in ys:
        r = rows[y]
        for x in range(W):
            if r[x] > 20:
                xs_min = min(xs_min, x)
                xs_max = max(xs_max, x)
    # A little padding so the outline is not clipped flush to the object edge.
    x0, x1 = max(0, xs_min - 4), min(W - 1, xs_max + 4)
    y0, y1 = max(0, ys[0] - 4), min(H - 1, ys[-1] + 4)
    w, h = x1 - x0 + 1, y1 - y0 + 1
    bmp = []
    for y in range(y0, y1 + 1):
        r = rows[y]
        bmp.append([1 if r[x] > 190 else (2 if r[x] > 40 else 0)
                    for x in range(x0, x1 + 1)])
    return bmp, w, h, x0, y0


def rle_line(line):
    out = bytearray()
    i, n = 0, len(line)
    while i < n:
        c = line[i]
        j = i
        while j < n and line[j] == c:
            j += 1
        run = j - i
        while run > 0:
            take = min(run, 16383)
            if c == 0:
                if take <= 63:
                    out += bytes([0x00, take])
                else:
                    out += bytes([0x00, 0x40 | (take >> 8), take & 0xFF])
            else:
                if take <= 63:
                    out += bytes([0x00, 0x80 | take, c])
                else:
                    out += bytes([0x00, 0xC0 | (take >> 8), take & 0xFF, c])
            run -= take
        i = j
    out += b"\x00\x00"
    return bytes(out)


def seg(kind: int, payload: bytes, pts_ms: int) -> bytes:
    pts90 = int(pts_ms * 90)
    return b"PG" + struct.pack(">IIBH", pts90, 0, kind, len(payload)) + payload


def pcs(comp_num, state, objs):
    p = struct.pack(">HHBHBBBB", W, H, 0x10, comp_num, state, 0x00, 0x00, len(objs))
    for oid, wid, x, y in objs:
        p += struct.pack(">HBBHH", oid, wid, 0x00, x, y)
    return p


def wds(windows):
    p = bytes([len(windows)])
    for wid, x, y, w, h in windows:
        p += struct.pack(">BHHHH", wid, x, y, w, h)
    return p


def pds():
    # index, Y, Cr, Cb, A — 1 is the white fill, 2 the dark edge.
    entries = [(1, 235, 128, 128, 255), (2, 16, 128, 128, 255)]
    p = bytes([0x00, 0x00])
    for e in entries:
        p += bytes(e)
    return p


def ods(w, h, rle):
    p = struct.pack(">HBB", 0x0000, 0x00, 0xC0)
    p += (len(rle) + 4).to_bytes(3, "big")
    p += struct.pack(">HH", w, h)
    return p + rle


def main(out_path, tag="PGS"):
    blob = bytearray()
    for n, (start, end, raw) in enumerate(CUES):
        text = raw.replace("[PGS]", f"[{tag}]")
        bmp, w, h, x, y = to_indexed(render_gray(text))
        rle = b"".join(rle_line(line) for line in bmp)
        win = [(0, x, y, w, h)]
        blob += seg(0x16, pcs(n * 2, 0x80, [(0, 0, x, y)]), start)
        blob += seg(0x17, wds(win), start)
        blob += seg(0x14, pds(), start)
        blob += seg(0x15, ods(w, h, rle), start)
        blob += seg(0x80, b"", start)
        blob += seg(0x16, pcs(n * 2 + 1, 0x00, []), end)
        blob += seg(0x17, wds(win), end)
        blob += seg(0x80, b"", end)
        print(f"cue {n}: {w}x{h} at ({x},{y}), rle {len(rle)} B, {start}-{end} ms")
    with open(out_path, "wb") as f:
        f.write(blob)
    print(f"wrote {out_path}: {len(blob)} bytes")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "test.sup",
         sys.argv[2] if len(sys.argv) > 2 else "PGS")
