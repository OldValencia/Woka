package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.toneMapped
import java.io.File

/**
 * The picture as the app must **draw** it, whatever colour the file was written in
 * (ME-177).
 *
 * Every still in Woka passes through a decoder and then through here, and that ordering
 * is the whole design: an HDR file becomes an ordinary sRGB bitmap before the viewer, the
 * editor, a grid tile or an export ever sees it. None of them then carries a tone map, so
 * none of them can disagree about one — which is exactly what went wrong. The editor drew
 * PQ codes as sRGB for as long as it has existed, because tone mapping lived in the
 * viewer's painter and the editor has its own draw path.
 *
 * **Called on what was decoded, not instead of decoding.** The proxy and the preview
 * decode at a cap — a tiled HEIF is assembled *at* 2560 px rather than assembled whole —
 * and this maps whatever they hold, so the cost lands on the small copy. That also keeps
 * `stillDecoderFor` returning the decoders themselves: three callers branch on the
 * concrete type to get that cap, and `readStillColor` below compares by identity to
 * decide how deeply to probe, so a wrapper would break the very function that answers
 * whether the file is HDR.
 *
 * **A new decode site must call this.** There is no seam that can enforce it — the point
 * of the design is that the decoders stay ordinary — so it is written here instead.
 */
internal fun displayable(file: File, decoded: ImageBitmap): ImageBitmap {
    val colour = colourToBelieve(file) ?: return decoded
    return toneMapped(decoded, colour)
}

/**
 * The colour to draw [file] as, or null to leave its pixels alone (ME-119).
 *
 * Null rather than an SDR [StillColorInfo] because the two are different answers: the
 * caller must return the **same instance** it was handed, the contract `StillEdits.apply`
 * already sets. Almost every picture in a library is SDR, and none of them should pay a
 * full-size surface to find that out.
 */
private fun colourToBelieve(file: File): StillColorInfo? = when (HdrView.mode) {
    HdrViewMode.ForceSdr -> null
    // Wide gamut as well as PQ: a file mistagged for transfer was written by something
    // that did not describe its colour, and BT.2020 is what HDR content actually carries.
    HdrViewMode.ForceHdr -> StillColorInfo(TransferKind.PQ, isWideGamut = true)
    HdrViewMode.Auto -> readStillColor(file).takeIf { it.needsColorManagement }
}
