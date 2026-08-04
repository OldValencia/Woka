# Full ASS/SSA Subtitle Rendering (libass)

> **Status: ❌ REJECTED — 2026-08-05. Not deferred; decided against.** The base subtitle system —
> SRT, **ASS-as-plain-text**, PGS/VobSub bitmap, external sidecars, track switching, delay/advance —
> shipped in `video-player-enhancements.md` §2–§3 on the current stack without this, and that is
> where it stays. The design below is kept as the record of what was weighed, not as work to do.
> Reasoning in *Why this was rejected*; everything after it is the original plan, unedited.

## Why this was rejected

**What plain-text ASS actually costs us, measured against the shipped code.** `SubtitleParser.cleanText`
strips `{...}` override tags cleanly and unfolds `\N` / `\h`, so an ASS track plays as ordinary
text — correctly timed, switchable, with delay/advance and the app's own styling. Exactly three
things do not work:

1. **Positioning** (`\pos`, `\an`), and with it **overlap**. Signs and typesetting — on-screen text,
   captions, chapter cards — lose their placement and fall to bottom-centre. They do not pile up
   there, though: `findActiveCue` returns the *first* cue covering the timestamp, so when a sign and
   a line of dialogue overlap, **one of them simply does not appear.** That silent drop, not a
   collision, is the real defect — and it is the only one of the three that actually hurts.
2. **Karaoke** (`\k`, `\kf`). An OP line appears whole instead of filling per syllable, often as
   three simultaneous rows (romaji + kanji + translation).
3. **Per-line styling** — colour per speaker, fonts, rotation. Cosmetic.

**What it would cost.** A native chain — libass + freetype + fontconfig + HarfBuzz — with no bytedeco
preset, so our own binding, then per-OS bundling into a jpackage distribution for platforms this
machine does not have. Step 13 in the same week spent a whole phase on hand-rolled native
integration and ended with the code deleted; that one at least failed against a single readable
HRESULT, where this would fail against three platform builds.

**Against a scenario the project does not contain.** The corpus is Netflix WEB-DL, whose subtitles
are text or bitmap — ASS does not occur in it. The ASS files under test are fixtures built for
Step 9, not real content. Full ASS serves fansubbed anime with dense typesetting, which is not what
Woka is used for. And Woka is personal and undeployed: there is no other user whose files have to
work.

**The cheap fix, if it ever bites.** Should a release land where a sign and a line of dialogue
overlap and one of them vanishes, the answer is **not** libass. It is two small changes in Kotlin,
neither of which needs a native dependency:

- Make `findActiveCue` collect *every* cue covering the timestamp instead of returning the first.
- Read `\an` / `\pos` out of the override tag **before** `stripTags` discards it, and use them to
  place a second simultaneous cue at the top of the frame rather than over the dialogue.

Around thirty lines, and it closes the whole visible problem.

**Reopen only if** a real file in actual use is unwatchable *and* the Kotlin mitigation above has
been tried and proved insufficient. Absent both, this document stays closed.

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
