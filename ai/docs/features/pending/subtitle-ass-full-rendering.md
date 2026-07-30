# Full ASS/SSA Subtitle Rendering (libass)

> **Status:** Deferred phase (the "5%"). The base subtitle system — SRT, **ASS-as-plain-text**, PGS/VobSub bitmap, external sidecars, track switching, delay/advance — ships in `video-player-enhancements.md` §2–§3 on the current stack **without** this. This document covers only *pixel-accurate* ASS rendering.

## 1. Scope — what this adds
Advanced ASS/SSA features that a plain-text renderer cannot reproduce:
- Absolute positioning (`\pos`, `\move`, `\an` alignment overrides).
- Per-line / per-style formatting (fonts, outline, shadow, colours, scale, rotation `\frx/\fry/\frz`).
- Karaoke timing (`\k`, `\kf`), fades (`\fad`), transforms (`\t`), clips (`\clip`).
- Correct font substitution + shaping via fontconfig/HarfBuzz.

Out of scope here (already handled by the base phase): text extraction, timing/sync, on/off, track selection, external files.

## 2. Why it is separate
- The base phase covers ~95% of real content; splitting keeps that shippable now.
- Full ASS is effectively a small rendering engine — must not be hand-rolled.
- It introduces a **native** dependency (libass + freetype + fontconfig + HarfBuzz) that needs per-OS bundling (win/mac/linux) in the JVM desktop build — a heavier build/packaging concern to isolate.

## 3. Approach — libass → RGBA overlay (keeps §1 intact)
libass renders a subtitle event, at a given timestamp and target resolution, into an **RGBA image on a transparent background**. We composite that image as a Compose overlay over the clean video frame — exactly like bitmap (PGS) subtitles.

Consequences:
- **§1 zoom/pan is untouched** — video frames stay clean; we never burn subtitles into them.
- Instant on/off, track switch, and delay/advance all still work (same overlay model as the base phase).
- Overlay is re-rendered on timestamp change; libass is fast enough for this.

Flow:
1. On selecting an ASS track, feed libass the ASS **header** (styles + script info) once.
2. Feed the **events** (from the same demux/decode path already built for the base phase — `avcodec_decode_subtitle2` gives the `.ass` line per event).
3. For the current `currentPositionMs - subtitleDelayMs`, ask libass for the frame → RGBA `ImageBitmap` → draw as overlay, positioned/scaled to the video panel size.

## 4. Dependency options (decide at implementation)
- **(A) Custom libass JNI / javacpp preset — preferred.** Direct control, standalone RGBA overlay, clean fit with §1. Cost: build + bundle libass (+freetype/fontconfig/HarfBuzz) native libs per OS.
- **(B) FFmpeg `subtitles`/`ass` libavfilter (libass-enabled build).** Reuses bytedeco FFmpeg if it was compiled `--enable-libass`. **But the filter burns onto the video frame** → conflicts with §1 zoom/pan and instant on/off. Fallback only. First verify with a probe: `avfilter_get_by_name("subtitles")` / list filters of the current bytedeco FFmpeg build.
- Bytedeco has **no** standalone libass preset today — option A means our own binding.

## 5. Integration points (reuses base phase)
- **Demux/decode:** the direct-libav subtitle path from the base phase already yields per-event ASS text + timing — no new decode work.
- **State/VM:** an `isTextBased && codec == ass` track routes to the libass renderer instead of the plain-text `Text` overlay.
- **Overlay:** same Compose overlay slot as PGS bitmap subs.
- **Customization (§3 of base doc):** app-side style overrides do **not** apply to full-ASS tracks — ASS carries its own styling (as already noted). Our customization stays for SRT / basic text.

## 6. Open items
- Native packaging strategy for libass + deps across win/mac/linux in the desktop bundle.
- Font provisioning: rely on system fonts via fontconfig, or ship a fallback font set.
- Probe result: is libass already inside the current bytedeco FFmpeg (enables fallback B with zero new native deps)?
- Performance target: overlay re-render cadence vs. karaoke (`\k`) which updates within a single event.
