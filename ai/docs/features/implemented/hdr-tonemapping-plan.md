# Plan: Automatic HDR / Dolby Vision Tone Mapping in the Media Viewer

> **TL;DR (RU):** HDR/Dolby-Vision видео (BT.2020 + PQ, 10-bit) сейчас рисуется как обычный SDR (Rec.709 gamma) — отсюда тусклая, выцветшая картинка и «оранжевый Netflix». Нужно **автоматически** определять такие ролики и делать tone mapping (BT.2020/PQ → BT.709/SDR), а обычные mp4 (SDR) оставить как есть. Проблема **подтверждена визуально** (см. ниже). Ключевое ограничение: наш bundled FFmpeg (Bytedeco 6.1.1) **не содержит `zscale`/`libplacebo`**, поэтому «просто добавить видеофильтр» — нельзя. Рекомендуемое решение — **GPU-шейдер (Skia SkSL)** на этапе рендера.

Status: ⏳ Pending — planned, not implemented. This document is implementation-ready.

---

## 1. The Problem (observed)

The user played `Avatar.The.Last.Airbender.S02E01.…DV.HDR.H.265….mkv`:
- In **VLC** → looks correct (rich colour, depth).
- In our viewer / mpc-hc → **dull, washed-out, low depth**, Netflix logo goes orange-with-green-tint.

This is the classic signature of **HDR content rendered as SDR without tone mapping**: pixels encoded with the PQ (SMPTE ST 2084) transfer function in the wide BT.2020 gamut are drawn as if they were Rec.709 / gamma 2.2. PQ is non-linear and scaled for up to 10 000 nits, so treating it as plain gamma crushes contrast and desaturates everything.

### Ground-truth probe of the actual file

```
codec_name      = hevc  (Main 10)
width x height  = 3840 x 2160
pix_fmt         = yuv420p10le           # 10-bit
color_range     = tv                    # limited range
color_space     = bt2020nc              # BT.2020 non-constant luminance
color_transfer  = smpte2084             # PQ  ← the HDR transfer function
color_primaries = bt2020                # wide gamut
side_data       = DOVI configuration record
                  dv_profile = 8
                  dv_bl_signal_compatibility_id = 1   # ← HDR10-compatible base layer
                  bl_present=1  el_present=0  rpu_present=1
```

**Important, and good news:** this is **Dolby Vision Profile 8.1** with **BL compatibility ID = 1**, i.e. the base layer is a **valid HDR10 stream** (BT.2020 + PQ + static metadata). It is **not** Profile 5. That means we do **not** need to decode/reshape the Dolby Vision RPU or handle the exotic IPT/ICtCp colour of Profile 5. A **standard HDR10 → SDR tone map** is sufficient to match VLC closely. (Profile 5 handling is deferred — see §7 "Out of scope".)

### Visual proof (this repo)

Two frames extracted from the file at `00:04:30`, downscaled to 960px:

- `assets/hdr-naive-current.png` — how our pipeline renders it today (swscale → RGB, no tone map). Dull, flat, desaturated.
- `assets/hdr-tonemapped-target.png` — after `BT.2020/PQ → tone map → BT.709`. Restored saturation, contrast and depth.

The delta between those two images **is** the bug and the target.

---

## 2. Root cause in our code

Current video pipeline (JVM, JavaCV/FFmpeg → Compose `ImageBitmap` → Skia):

| File | What it does | Problem |
|---|---|---|
| `shared/…/viewer/player/VideoPlayerDecoder.kt:23` | `grabber.pixelFormat = AV_PIX_FMT_RGBA` | Forces swscale to output **8-bit RGBA**. swscale does the YUV→RGB matrix but performs **no tone mapping and no PQ→display transfer**. HDR info is destroyed here, and it's only 8-bit. |
| `shared/…/viewer/player/VideoPlayerExt.kt:14` `Frame.toComposeBitmap()` | Wraps the 8-bit RGBA bytes into a Skia `RGBA_8888` raster | Treats PQ-encoded values as if display-ready sRGB/Rec.709. No colour management. |
| `shared/…/viewer/MediaViewerContent.kt:181` `Image(bitmap=…)` | Draws the bitmap straight to screen | No colour transform at render time. |

Nowhere in the pipeline is the stream's `color_transfer` / `color_primaries` inspected. SDR (mp4) happens to look fine because its encoding already matches the display assumptions; HDR does not.

---

## 3. Key constraint discovered — our bundled FFmpeg cannot tone-map via filters

The "textbook" fix is an FFmpeg filter graph:

```
zscale=t=linear:npl=100, tonemap=tonemap=hable:desat=0, zscale=t=bt709:m=bt709:p=bt709:r=tv, format=rgb24
```

This was **validated to work and look correct** using a system FFmpeg 8.0 build (it produced `hdr-tonemapped-target.png`). **However**, our app ships `org.bytedeco:ffmpeg-platform` **6.1.1-1.5.10** (see `gradle/libs.versions.toml:13`). Inspecting the bundled `avfilter-9.dll` (windows-x86_64):

| Filter | Present in bundled build? |
|---|---|
| `tonemap` | ✅ yes |
| `zscale` (needs libzimg) | ❌ **no** (0 occurrences) |
| `libplacebo` | ❌ **no** |
| `colorspace` | ✅ yes (but cannot linearise PQ / does not tone-map HDR) |

`tonemap` **requires linear-light float input**, and the only in-graph way to produce that from PQ is `zscale` (or `libplacebo`) — **both missing**. `colorspace` does not support the PQ transfer for tone mapping. **Therefore the pure filter-graph approach is not achievable with the current dependency.** This is the decision-driving fact.

---

## 4. Goals & non-goals

**Goals**
- Auto-detect HDR/PQ/HLG (and HDR10-compatible DV) content from stream metadata — no user toggle.
- Tone-map BT.2020/PQ (and HLG) → BT.709/SDR so playback matches VLC "closely enough".
- **Zero regression / zero cost for SDR content** (normal mp4 keeps its exact current path).
- Realtime for 4K (no per-pixel work on the JVM CPU hot path).

**Non-goals (for this feature)**
- Dolby Vision **Profile 5** (IPT/ICtCp, no HDR10 base) — deferred (§7).
- Outputting real HDR to an HDR display — we always tone-map to SDR for now.
- The editor's export/"bake" decisions (noted in §6, implemented with the editor feature).

---

## 5. Options considered & recommendation

### ❌ Option F — FFmpeg filter graph (`zscale`+`tonemap`)
Cleanest (~20 lines via `FFmpegFrameFilter`) **but blocked**: no `zscale`/`libplacebo` in the bundled build (§3). Only becomes viable if we ship a zimg-enabled FFmpeg — see §5.1 investigation. **Do the 30-min investigation first**; if it pans out, this beats everything on effort.

### ✅ Option G — GPU tone-mapping shader (Skia `RuntimeEffect` / SkSL) — **RECOMMENDED**
Do the colour transform on the GPU at render time, which is where Compose/Skia already draws the frame.
- **Pros:** no new native dependency; realtime for 4K; matches the user's own recommendation ("do tone mapping on the GPU via a shader"); cleanly **preserves wide-gamut data** for the future editor (view = apply shader; export = bake).
- **Cons:** more code than a filter string; must plumb ≥10-bit frames (16-bit) from the decoder to the shader; SkSL must be authored + validated.
- **Effort:** ~1–1.5 days.

### 🟡 Option C — CPU tone map on a downscaled frame (fallback)
Grab a **downscaled** 16-bit frame (e.g. to the viewport size) and run PQ→SDR on the CPU.
- **Pros:** simplest, fully correct, no shader/build changes. Good as a first correctness milestone or a fallback if the shader integration is painful.
- **Cons:** the user explicitly wants to avoid CPU tone mapping; can jank on weak machines even downscaled. **Fallback only.**

### 🔵 Option M — Swap the video backend to libmpv / VLCJ
Embed mpv/VLC, which do all of this (incl. DV P5) natively with GPU shaders — true VLC parity.
- **Pros:** best quality, handles everything, less colour code to own.
- **Cons:** large architectural change; replaces the current hand-built decode/seek/audio loop (generation-tagged frames, `SourceDataLine` audio, etc.); new native deps & packaging. **Revisit only if HDR/DV coverage must be comprehensive.**

**Recommendation:** Ship **Option G** (GPU SkSL shader). Do the **§5.1 zimg investigation first** — if a zimg-enabled FFmpeg is cheap to adopt, Option F is a far smaller change and we take it instead. Keep **Option C** as the fallback path and **Option M** as the long-term parity option.

### 5.1 Pre-work investigation (timebox ~30 min, do first)
- Check whether any Bytedeco artifact/classifier for FFmpeg 6.x/7.x bundles `libzimg` (i.e. exposes `zscale`). Historically the javacpp-presets FFmpeg build does **not** enable libzimg or libplacebo, so expectation is low — but confirm, because if it does, **Option F is the whole fix**.
- If not available off-the-shelf, do **not** build a custom FFmpeg for this — go with Option G.

---

## 6. Detailed design (Option G — recommended)

### 6.1 Detect HDR from stream metadata
Add an HDR classifier read once when the grabber starts, from the stream's codec parameters (JavaCV exposes the `AVFormatContext`/`AVStream`/`AVCodecParameters`; the fields are `color_trc()`, `color_space()`, `color_primaries()`), plus DOVI side data.

```kotlin
// new file: shared/…/viewer/player/HdrInfo.kt
package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.global.avutil.*

enum class TransferKind { SDR, PQ, HLG }

data class HdrInfo(
    val transfer: TransferKind,
    val isWideGamut: Boolean,      // primaries == BT.2020
    val dvProfile: Int?,           // from DOVI config record, if any
    val dvHdr10Compatible: Boolean // bl_signal_compatibility_id == 1
) {
    /** Do we need to tone-map this stream for an SDR display? */
    val needsToneMap: Boolean
        get() = transfer == TransferKind.PQ || transfer == TransferKind.HLG || isWideGamut
}

/** Reads colour characteristics from the already-started grabber's video stream. */
internal fun readHdrInfo(colorTrc: Int, colorPrimaries: Int /*, dovi side data */): HdrInfo {
    val transfer = when (colorTrc) {
        AVCOL_TRC_SMPTE2084 -> TransferKind.PQ          // ST 2084 / PQ
        AVCOL_TRC_ARIB_STD_B67 -> TransferKind.HLG      // HLG
        else -> TransferKind.SDR
    }
    val wide = colorPrimaries == AVCOL_PRI_BT2020
    // dvProfile / compatibility parsed from AV_PKT_DATA_DOVI_CONF side data when present.
    return HdrInfo(transfer, wide, dvProfile = null, dvHdr10Compatible = false)
}
```

> Implementation note: JavaCV's `FFmpegFrameGrabber` does not surface `color_trc` directly. Reach the values via the underlying format context — e.g. `grabber.formatContext.streams(videoStreamIndex).codecpar()` → `color_trc()`, `color_space()`, `color_primaries()`. DOVI side data is `AV_PKT_DATA_DOVI_CONF` on the stream. Confirm exact accessors against JavaCV 1.5.10 during implementation.

### 6.2 Decode HDR frames at 16-bit (preserve precision)
In `VideoPlayerDecoder.kt`, choose the output pixel format based on `HdrInfo`:

```kotlin
grabber.start()
val hdr = readHdrInfo(...)           // after start(), codecpar is populated
grabber.pixelFormat = if (hdr.needsToneMap)
        AV_PIX_FMT_RGBA64LE          // 16-bit/channel: keeps the 10-bit PQ precision
    else
        AV_PIX_FMT_RGBA              // unchanged SDR path (byte-for-byte as today)
```

- SDR path is **completely unchanged** (still `AV_PIX_FMT_RGBA`, still `toComposeBitmap()` as-is).
- For HDR, `RGBA64LE` gives swscale-produced **PQ-encoded R'G'B' in BT.2020 primaries, 16-bit** — the correct input for the shader (we deliberately do NOT let swscale tone-map; it can't anyway).
- Optionally set `grabber.imageWidth/imageHeight` to the viewport size so swscale downscales 4K→viewport during decode (big perf win; the window is rarely 3840px wide).

`VideoFrame` / `VideoPlayerState.currentFrame` carry the `HdrInfo` alongside the bitmap so the renderer knows whether/how to tone-map.

### 6.3 Build a 16-bit Skia bitmap
Add an HDR variant of `toComposeBitmap()` using a 16-bit colour type:

```kotlin
// RGBA_F16 or a 16-bit unorm type; F16 is the most shader-friendly.
val info = ImageInfo(width, height, ColorType.RGBA_F16, ColorAlphaType.UNPREMUL)
```
(If ingesting `RGBA64LE` unorm bytes into an F16 raster needs a convert, use the matching 16-bit unorm `ColorType` and normalise in the shader. Pin the exact `ColorType` during implementation against the Skiko version in use.)

### 6.4 Tone-mapping SkSL shader (applied at render)
Author a Skia `RuntimeEffect`. Standard, well-known math (BT.2390 / Hable); no custom colour science to invent:

Pipeline per pixel: **normalise → PQ EOTF (→ linear, BT.2020) → BT.2020→BT.709 matrix → tone-map operator → BT.709 OETF (gamma)**.

```glsl
uniform shader frame;   // 16-bit RGBA, PQ-encoded, BT.2020 primaries
// PQ (SMPTE ST 2084) constants
const float m1 = 0.1593017578125, m2 = 78.84375;
const float c1 = 0.8359375, c2 = 18.8515625, c3 = 18.6875;

vec3 pqEotf(vec3 e) {                    // -> normalised linear [0..1] of 10000 nits
    vec3 p = pow(e, vec3(1.0/m2));
    vec3 num = max(p - c1, 0.0);
    vec3 den = c2 - c3 * p;
    return pow(num / den, vec3(1.0/m1));
}
// BT.2020 -> BT.709 (linear)
const mat3 bt2020to709 = mat3(
     1.6605, -0.1246, -0.0182,
    -0.5876,  1.1329, -0.1006,
    -0.0728, -0.0083,  1.1187);
vec3 hable(vec3 x){                      // Uncharted2 filmic
    const float A=0.15,B=0.50,C=0.10,D=0.20,E=0.02,F=0.30;
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
}
half4 main(float2 c){
    vec3 e   = frame.eval(c).rgb;        // normalised PQ signal [0..1]
    vec3 lin = pqEotf(e);                // linear, referenced to 10000 nits
    lin *= (10000.0/100.0);             // scale so SDR white ~1.0 (tune peak-nits)
    lin = bt2020to709 * lin;             // gamut map
    const float W = 11.2;
    vec3 mapped = hable(lin) / hable(vec3(W));
    vec3 srgb = pow(clamp(mapped,0.0,1.0), vec3(1.0/2.2)); // BT.709/gamma OETF
    return half4(srgb, 1.0);
}
```
> Operator choice: `hable` matches the validated reference frame. `bt.2390` EETF is the "reference" perceptual roll-off and is what mpv/VLC lean on — easy to swap. Expose `desat` and `peak-nits` as uniforms for tuning. **These constants must be validated against `assets/hdr-tonemapped-target.png` before shipping (see §8).**

### 6.5 Apply the shader only for HDR frames
In `MediaViewerContent.kt` around the `Image(bitmap = frameToDraw, …)` (line 181): when the current `VideoFrame` is HDR, wrap the bitmap draw in the `RuntimeEffect`/`ShaderBrush` (e.g. draw via a `graphicsLayer`/`Canvas` with the runtime shader, or a `RenderEffect`). SDR frames render through the **existing** `Image(...)` path untouched. Gate strictly on `hdrInfo.needsToneMap` so there is **no** new work for mp4/SDR.

---

## 7. Out of scope / deferred

- **Dolby Vision Profile 5** (single-layer, IPT/ICtCp, no HDR10 base): needs RPU reshaping (libplacebo or a DV decoder). Our current file is **P8.1**, so this is not needed now. If P5 files appear later, they will look wrong under this plan → route to Option M (libmpv) or add a P5 detector that shows a "unsupported HDR variant" hint. Detection already records `dvProfile`.
- **HDR display output** (passthrough to an HDR monitor): always tone-map to SDR for now.
- **Still images.** Tone mapping here is a **video-frame** feature: `HdrInfo` is read from FFmpeg
  stream ids inside the player, and `MediaViewerFrame` selects `HdrImagePainter` from
  `video.player.currentFrameHdrInfo`, so a photo has no route to that decision at all. Raised as F4
  in `viewer-verification-pass.md`, and owned from here on by
  `pending/rare-image-formats-and-frame-sets.md` § Phase 5 — the decoders have to land first, since
  the formats that carry HDR stills in practice are HEIC and AVIF and the app opens neither.

---

## 8. Testing plan

1. **Offline math validation (do this first, cheap, no app run):** using the sample file, dump a 16-bit `RGBA64LE` frame (swscale, no tone map — exactly what the decoder will feed the shader), run the §6.4 math in a tiny script (numpy/py), and diff the result against `assets/hdr-tonemapped-target.png`. Tune `peak-nits` / operator until it visually matches. This de-risks the SkSL before touching Compose.
2. **SDR regression:** a normal `.mp4` must render **identically** to today (same code path; verify `needsToneMap == false`).
3. **HDR playback:** play `Avatar…S02E01…DV.HDR….mkv`; confirm colour/contrast ≈ VLC, no green/orange tint on the Netflix ident, smooth 4K playback (watch CPU/GPU; enable decode-time downscale if needed).
4. **HLG clip** (if available): confirm the HLG branch.
5. **Seek/audio unaffected:** the generation-tagged seek logic and `SourceDataLine` audio must behave exactly as before (frame format changes, timing logic doesn't).

---

## 9. Execution checklist

**Phase 0 — Investigation**
- [ ] Timebox: is a zimg-enabled Bytedeco FFmpeg cheaply available? If yes → pivot to Option F (filter graph) and skip most of the below.

**Phase 1 — Detection (safe, no behaviour change)**
- [ ] Add `HdrInfo.kt` + `readHdrInfo(...)`; read `color_trc/space/primaries` (+ DOVI side data) from the started grabber.
- [ ] Thread `HdrInfo` through `VideoFrame` → `VideoPlayerState.currentFrame`.
- [ ] Log detected classification for the sample file; verify `PQ + BT.2020 + DV P8.1`.

**Phase 2 — 16-bit HDR decode**
- [ ] In `VideoPlayerDecoder.kt`, pick `RGBA64LE` for `needsToneMap`, keep `RGBA` for SDR.
- [ ] Add HDR `toComposeBitmap()` variant (16-bit `ColorType`); confirm SDR path untouched.
- [ ] (Perf) optional decode-time downscale to viewport size.

**Phase 3 — GPU tone map**
- [ ] Validate §6.4 math offline vs `assets/hdr-tonemapped-target.png` (Test 8.1).
- [ ] Implement the SkSL `RuntimeEffect`; wire it into `MediaViewerContent.kt` for HDR frames only.
- [ ] Expose operator + peak-nits (+desat) constants; pick defaults matching the reference.

**Phase 4 — Verify**
- [ ] SDR regression (Test 8.2).
- [ ] HDR playback vs VLC (Test 8.3); performance check.
- [ ] Seek/audio unchanged (Test 8.5).

---

## 10. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Bundled FFmpeg lacks `zscale`/`libplacebo` | **Confirmed** | Use GPU shader (Option G), not a filter graph. |
| Skiko `RuntimeEffect`/16-bit `ColorType` API friction in this Compose version | Med | Pin API during impl; fallback to Option C (CPU tonemap, downscaled) if blocked. |
| 4K tone map too heavy | Low–Med | Shader is GPU; also downscale at decode to viewport size. |
| Colours don't match VLC exactly | Med | Tune operator (hable ↔ bt.2390), desat, peak-nits against the reference frame. |
| A future file is DV **Profile 5** | Low (now) | Out of scope; detector flags it; Option M later. |
| Regressing SDR/mp4 playback | Low | HDR branch is strictly gated on `needsToneMap`; SDR path is byte-for-byte unchanged. |

---

## Appendix — evidence & environment
- Sample: `D:/Movies/Avatar.The.Last.Airbender.S02…/…S02E01…DV.HDR.H.265….mkv` → **DV Profile 8.1, HDR10-compatible, BT.2020/PQ, 10-bit**.
- Dep: `org.bytedeco:ffmpeg-platform 6.1.1-1.5.10` (`gradle/libs.versions.toml:13`); bundled `avfilter-9.dll` has `tonemap`, lacks `zscale`/`libplacebo`.
- Comparison frames: `assets/hdr-naive-current.png` (bug) vs `assets/hdr-tonemapped-target.png` (target).
- Touch points: `VideoPlayerDecoder.kt:23`, `VideoPlayerExt.kt:14`, `VideoPlayerContent`/`MediaViewerContent.kt:181`, `VideoPlayerState.kt`, `VideoPlayerPresenter.kt` (`VideoFrame`).
