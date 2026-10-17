package com.woka.feature.viewer

import com.woka.core.data.MediaItem
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.shell.audioDetail
import com.woka.feature.viewer.shell.formatDuration
import com.woka.feature.viewer.shell.formatFps
import com.woka.feature.viewer.shell.resolution
import com.woka.feature.viewer.shell.subtitleDetail
import com.woka.feature.viewer.shell.toReadableSize
import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.feature.viewer.video.model.MediaTracks
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO

/** One titled group of the panel: label and value, in two columns. */
internal class InfoSection(val title: String, val rows: List<Pair<String, String>>)

/** What the disk adds to a file's name and size: a video's streams, a picture's size. */
internal class Probed(
    val tracks: MediaTracks?,
    val durationMs: Long?,
    val transfer: TransferKind?,
    val dimensions: String?
)

/**
 * Reads what the panel shows beyond the file's own attributes. Blocking; the caller
 * is on `Dispatchers.IO`. A video gets the seek preview's capped probe; a picture its
 * header only, through `ImageIO`, which answers for JPEG, PNG, GIF, BMP and TIFF and
 * returns nothing for HEIC or AVIF — the panel then simply has no Dimensions row.
 */
internal fun probe(item: MediaItem): Probed? = when (item) {
    is MediaItem.Video -> probeVideo(item.file)
    is MediaItem.Image -> Probed(null, null, null, imageDimensions(item.file))
    else -> null
}

private fun probeVideo(file: File): Probed? = try {
    val grabber = FFmpegFrameGrabber(file).apply {
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        start()
    }
    try {
        val length = grabber.lengthInTime.takeIf { it > 0 }?.div(1000)
        Probed(probeMediaTracks(grabber), length, grabber.readStreamHdrInfo().transfer, null)
    } finally {
        grabber.stop()
        grabber.release()
    }
} catch (_: Exception) {
    null
}

private fun imageDimensions(file: File): String? = try {
    ImageIO.createImageInputStream(file)?.use { stream ->
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
        try {
            reader.input = stream
            "${reader.getWidth(0)} × ${reader.getHeight(0)}"
        } finally {
            reader.dispose()
        }
    }
} catch (_: Exception) {
    null
}

/** The panel's sections, General first, then a video's streams. */
internal fun panelSections(item: MediaItem, probed: Probed?): List<InfoSection> {
    val file = item.file
    val general = listOfNotNull(
        "Kind" to kindOf(item),
        ("Size" to file.length().toReadableSize()).takeIf { item !is MediaItem.Folder },
        probed?.dimensions?.let { "Dimensions" to it },
        probed?.durationMs?.let { "Duration" to formatDuration(it) },
        "Modified" to SimpleDateFormat("d MMM yyyy, HH:mm").format(Date(file.lastModified()))
    )
    val tracks = probed?.tracks ?: return listOf(InfoSection("General", general))
    val video = tracks.video.firstOrNull()
    val picture = video?.resolution()?.let { res ->
        listOfNotNull(res, video.frameRate?.let(::formatFps)).joinToString(" · ")
    }
    val videoRows = listOfNotNull(
        video?.let { "Codec" to it.codec.uppercase() },
        picture?.let { "Picture" to it },
        probed.transfer?.let { "HDR" to hdrLabel(it) }
    )
    val audioRows = tracks.audio.map { "${it.number}" to "${it.label} · ${audioDetail(it)}" }
    val subtitleRows = tracks.subtitle.map { "${it.number}" to subtitleDetail(it) }
    return listOfNotNull(
        InfoSection("General", general),
        InfoSection("Video", videoRows).takeIf { videoRows.isNotEmpty() },
        InfoSection("Audio", audioRows).takeIf { audioRows.isNotEmpty() },
        InfoSection("Subtitles", subtitleRows).takeIf { subtitleRows.isNotEmpty() }
    )
}

private fun kindOf(item: MediaItem): String = when (item) {
    is MediaItem.Folder -> "Folder"
    is MediaItem.Video -> "${item.file.extension.uppercase()} video"
    else -> "${item.file.extension.uppercase()} image"
}

private fun hdrLabel(transfer: TransferKind): String = when (transfer) {
    TransferKind.PQ -> "HDR · PQ"
    TransferKind.HLG -> "HDR · HLG"
    else -> "SDR"
}
