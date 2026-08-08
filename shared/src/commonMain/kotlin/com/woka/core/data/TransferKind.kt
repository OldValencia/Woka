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
 * True when this picture cannot go straight to an sRGB display untouched.
 *
 * **This was called `needsToneMap`, and the name was the bug.** It selects
 * correctly — anything that is not plain SDR BT.709 needs handling — but what
 * it selected was fed to one PQ tone-mapping shader, and only one of the three
 * cases it catches wants that:
 *
 * - **PQ** — a different transfer function *and* HDR luminance. Needs
 *   linearising by the PQ EOTF and a tone curve to fit an SDR display.
 * - **HLG** — a different transfer function again, and not remotely PQ's. Sent
 *   through the PQ EOTF, as it was, its shadows crush and its highlights lie.
 * - **Wide-gamut SDR** — BT.2020 primaries with an ordinary transfer. **Not HDR
 *   at all.** It needs the gamut matrix and nothing else; a tone curve here
 *   flattens a picture that had nothing wrong with its luminance.
 *
 * So the selection stayed and the *painter* learnt to tell the three apart —
 * see `HdrImagePainter`. [needsToneCurve] and [needsGamutMap] are what it asks.
 */
fun needsColorManagement(transfer: TransferKind, isWideGamut: Boolean): Boolean =
    needsToneCurve(transfer) || needsGamutMap(isWideGamut)

/** Only a genuinely high-dynamic-range transfer gets a tone curve. */
fun needsToneCurve(transfer: TransferKind): Boolean = transfer != TransferKind.SDR

/** Only BT.2020 primaries need converting to BT.709. */
fun needsGamutMap(isWideGamut: Boolean): Boolean = isWideGamut
