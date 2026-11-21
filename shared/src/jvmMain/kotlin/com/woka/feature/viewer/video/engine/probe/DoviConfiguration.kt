package com.woka.feature.viewer.video.engine.probe

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_DOVI_CONF
import org.bytedeco.ffmpeg.global.avcodec.av_packet_side_data_get

/** Copied scalars only: no borrowed FFmpeg pointer may outlive this header read. */
internal data class DoviConfiguration(val profile: Int, val compatibility: Int)

internal sealed interface DoviConfigurationRead {
    data object Absent : DoviConfigurationRead
    data class Present(val configuration: DoviConfiguration) : DoviConfigurationRead
    data class Unavailable(val reason: String) : DoviConfigurationRead
}

/** Header side data, never inferred from a filename or missing PQ tags. */
internal fun readDoviConfiguration(parameters: AVCodecParameters): DoviConfigurationRead {
    val side = av_packet_side_data_get(
        parameters.coded_side_data(), parameters.nb_coded_side_data(), AV_PKT_DATA_DOVI_CONF
    )
    if (side == null || side.isNull) return DoviConfigurationRead.Absent
    return DoviNative.read(side.data(), side.size())
}
