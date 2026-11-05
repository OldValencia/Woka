package com.woka.feature.viewer.video.engine.probe

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import org.bytedeco.ffmpeg.avutil.AVContentLightMetadata
import org.bytedeco.ffmpeg.avutil.AVDynamicHDRPlus
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVMasteringDisplayMetadata
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_CONTENT_LIGHT_LEVEL
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DYNAMIC_HDR_PLUS
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_MASTERING_DISPLAY_METADATA
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data

/** Nits per unit of SDR white, the unit `HDR_SHADER` reasons in. */
private const val NITS_PER_UNIT = 100f

/** PQ's full scale. */
private const val MAX_NITS = 10_000

/** A mastering display dimmer than SDR white was not mastering HDR. */
private const val MIN_MASTERING_NITS = 100.0

/**
 * The white point a film declares, in units of SDR white — or [ASSUMED_WHITE_POINT] when it
 * declares nothing believable (ME-301).
 *
 * **Checked, not trusted**, because Woka opens files from anywhere (the author, 2026-11-05):
 * MaxCLL counts when it is in range and not below MaxFALL — a brightest pixel dimmer than the
 * average frame is a broken encoder, not a picture — and the mastering display's peak caps it.
 * Without a usable MaxCLL the mastering peak is the answer; it is 1000 nits on all 17 PQ
 * files measured, and MaxCLL matched their frames' own peaks (`VideoHdrPeakProbe`).
 *
 * **Never below 1.0**, `HdrWhitePoint`'s rule: under SDR white the curve would expand.
 */
internal fun plausibleWhitePoint(maxCll: Int, maxFall: Int, masteringNits: Double?): Float {
    val mastering = masteringNits?.takeIf { it in MIN_MASTERING_NITS..MAX_NITS.toDouble() }
    val cll = maxCll.takeIf { it in 1..MAX_NITS && (maxFall == 0 || it >= maxFall) }
    val nits = when {
        cll != null -> minOf(cll.toDouble(), mastering ?: cll.toDouble())
        mastering != null -> mastering
        else -> return ASSUMED_WHITE_POINT
    }
    return (nits.toFloat() / NITS_PER_UNIT).coerceAtLeast(1f)
}

/** [plausibleWhitePoint] over this frame's content-light and mastering-display side data. */
internal fun AVFrame.declaredWhitePoint(): Float {
    val light = av_frame_get_side_data(this, AV_FRAME_DATA_CONTENT_LIGHT_LEVEL)
        ?.let { AVContentLightMetadata(it.data()) }
    val mastering = av_frame_get_side_data(this, AV_FRAME_DATA_MASTERING_DISPLAY_METADATA)
        ?.let { AVMasteringDisplayMetadata(it.data()) }
        ?.takeIf { it.has_luminance() != 0 }
        ?.max_luminance()
        ?.takeIf { it.den() != 0 }
        ?.let { it.num().toDouble() / it.den() }
    return plausibleWhitePoint(light?.MaxCLL() ?: 0, light?.MaxFALL() ?: 0, mastering)
}

/**
 * This HDR info with the film's white point read off [picture] — once: a set white point, a
 * stream that is not PQ or no frame to read leave it as it is.
 *
 * **PQ only.** HLG is relative — the shader already reads it against its nominal peak — and a
 * MaxCLL of 400 on an HLG film would put the white point under that peak and clip everything
 * above it. No HLG film in the corpus carries the metadata to measure otherwise.
 */
internal fun HdrInfo.readFrom(picture: AVFrame?): HdrInfo {
    if (whitePoint != null || transfer != TransferKind.PQ || picture == null) return this
    return copy(whitePoint = picture.declaredWhitePoint())
}

/**
 * After a reopen of the **same** stream — a rescale, the picture graph — the white point it
 * already had: read again from scratch it went missing for a frame, and the seek preview and
 * the editor's thumbnail flickered to the assumed one (1.0.6 review).
 */
internal fun HdrInfo.keptOn(previous: HdrInfo?): HdrInfo =
    if (previous != null && previous.transfer == transfer && previous.isWideGamut == isWideGamut) {
        copy(whitePoint = previous.whitePoint)
    } else {
        this
    }

/**
 * The scene's peak from HDR10+ in units of SDR white, or null (ME-305): the largest `maxscl`
 * of the first window, a fraction of 10000 nits. Authored per scene, so it changes at a cut
 * and a frame carries the same value however it was reached — which a peak measured off the
 * pixels would not.
 */
internal fun AVFrame.scenePeak(): Float? {
    val side = av_frame_get_side_data(this, AV_FRAME_DATA_DYNAMIC_HDR_PLUS) ?: return null
    val meta = AVDynamicHDRPlus(side.data())
    if (meta.num_windows().toInt() == 0) return null
    val params = meta.params(0)
    val brightest = (0 until 3).maxOf { channel ->
        val scl = params.maxscl(channel)
        if (scl.den() == 0) 0.0 else scl.num().toDouble() / scl.den()
    }
    val units = (brightest * MAX_NITS / NITS_PER_UNIT).toFloat()
    return units.takeIf { it > 0f && it <= MAX_NITS / NITS_PER_UNIT }?.coerceAtLeast(1f)
}

/** This frame's [scenePeak] on a PQ stream; anything else is left as it is. */
internal fun HdrInfo.withSceneOf(picture: AVFrame?): HdrInfo =
    if (transfer == TransferKind.PQ) copy(scenePeak = picture?.scenePeak()) else this
