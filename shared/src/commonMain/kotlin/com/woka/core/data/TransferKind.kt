package com.woka.core.data

/**
 * The transfer function a picture's pixels were encoded with.
 *
 * Moved here from `feature/viewer/video/engine/probe/HdrInfo.kt` by the frame-set
 * work. A **still** has a transfer function too — a HEIC or AVIF photo carries PQ
 * or HLG exactly as a video stream does — and `commonMain` cannot reach into
 * `jvmMain` to ask. Declaring a second enum next to `StillColorInfo` would have
 * meant two answers to one question, and the extension-list mess `MediaKind`
 * documents is what that looks like a year later.
 *
 * `HdrInfo` keeps the FFmpeg-id mapping; only the vocabulary lives here.
 */
enum class TransferKind { SDR, PQ, HLG }

/**
 * Whether a picture with this colour description has to be tone mapped to reach
 * an SDR display. **One rule, one place** — `HdrInfo` (video) and
 * [StillColorInfo] (stills) both defer to it rather than each spelling it out.
 *
 * Wide gamut alone counts: BT.2020 primaries through an sRGB pipeline are wrong
 * even when the transfer is plain. Note that the shader this selects is PQ-only
 * today, so an HLG source reaches the wrong maths — a pre-existing defect
 * recorded in Phase 5 of `rare-image-formats-and-frame-sets.md`, not one this
 * function introduces.
 */
fun needsToneMap(transfer: TransferKind, isWideGamut: Boolean): Boolean =
    transfer != TransferKind.SDR || isWideGamut
