"""Writes the .idx companion for a dvdsub MPEG-PS produced by ffmpeg.

FFmpeg has no vobsub muxer: it can put dvdsub packets into an MPEG program stream
(which is exactly what a .sub file is) but not produce the .idx index beside it.
This scans the stream for private_stream_1 PES packets carrying a PTS — each one
starts a new subtitle — and records the offset of the pack that contains it.
"""
import sys

# FFmpeg's dvdsub encoder picks entries 6 and 7 for the two-colour source, so those
# are set to the white fill and the black outline; the rest keep the conventional
# VobSub ramp. Verified by rendering, not assumed — the default ramp put red text
# on screen because 6 is `fa3333` in it.
PALETTE = ("000000, f0f0f0, cccccc, 999999, 3333fa, 1111bb, 101010, f0f0f0, "
           "33fa33, 11bb11, fafa33, bbbb11, fa33fa, bb11bb, 33fafa, 11bbbb")


def read_pts(d, i):
    return (((d[i] & 0x0E) << 29) | (d[i + 1] << 22) | ((d[i + 2] & 0xFE) << 14)
            | (d[i + 3] << 7) | ((d[i + 4] & 0xFE) >> 1))


def parse_pts(buf, pes_start):
    """FFmpeg's mpeg muxer emits **MPEG-1** PES headers here, not MPEG-2 ones:
    no 0x80 flag byte, just optional stuffing, an optional STD buffer field and
    then a PTS whose first nibble is 2 (PTS) or 3 (PTS+DTS)."""
    j = pes_start + 6
    if (buf[j] & 0xC0) == 0x80:          # MPEG-2 PES
        return read_pts(buf, j + 3) if buf[j + 1] & 0x80 else None
    while j < len(buf) and buf[j] == 0xFF:
        j += 1
    if (buf[j] & 0xC0) == 0x40:          # STD buffer scale/size
        j += 2
    nib = buf[j] >> 4
    if nib in (0x02, 0x03):
        return read_pts(buf, j)
    return None


def scan(path):
    buf = open(path, "rb").read()
    entries = []
    pack_off = 0
    i = 0
    n = len(buf)
    while i < n - 4:
        if buf[i:i + 4] == b"\x00\x00\x01\xBA":
            pack_off = i
            i += 4
            continue
        if buf[i:i + 4] == b"\x00\x00\x01\xBD":
            pts = parse_pts(buf, i)
            if pts is not None:
                entries.append((pts, pack_off))
            length = int.from_bytes(buf[i + 4:i + 6], "big")
            i += 6 + length
            continue
        i += 1
    return entries


def fmt_ts(pts90):
    ms = pts90 // 90
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d}:{ms:03d}"


def main(sub_path, idx_path, size, offset_ms=0, lang="en"):
    """[offset_ms] shifts every index entry.

    The mpeg muxer rebases the stream so its first packet sits at t=0, losing the
    source's leading offset. The .idx timestamp — not the PES PTS — is what a
    VobSub reader actually plays from (rippers routinely leave junk PTS in .sub),
    so restoring the offset here is the format's own mechanism, not a workaround.
    """
    entries = [(pts + offset_ms * 90, off) for pts, off in scan(sub_path)]
    lines = [
        "# VobSub index file, v7 (generated for Woka Step 10 testing)",
        f"size: {size}",
        f"palette: {PALETTE}",
        "langidx: 0",
        "",
        f"id: {lang}, index: 0",
    ]
    for pts, off in entries:
        lines.append(f"timestamp: {fmt_ts(pts)}, filepos: {off:09x}")
    open(idx_path, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print(f"{idx_path}: {len(entries)} entries")
    for pts, off in entries[:4]:
        print(f"   {fmt_ts(pts)} @ 0x{off:x}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2],
         sys.argv[3] if len(sys.argv) > 3 else "1920x1080",
         int(sys.argv[4]) if len(sys.argv) > 4 else 0)
