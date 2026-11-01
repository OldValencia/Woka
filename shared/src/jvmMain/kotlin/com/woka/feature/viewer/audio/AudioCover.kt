package com.woka.feature.viewer.audio

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.ui.image.ImageBitmapCache
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.Image
import java.io.File

/** Picture files a folder of music conventionally carries its cover in, in the order asked. */
private val SIDECAR_NAMES = listOf("cover", "folder", "front", "album")
private val SIDECAR_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * The cover of an audio file (AP-003): the picture the file carries, else the one
 * lying beside it, else none.
 *
 * **The embedded one is read as bytes, not decoded as video.** FFmpeg reports a cover
 * as a video stream flagged `attached_pic` whose one packet *is* the JPEG or PNG
 * (AP-001, `audio-player.md` §1a); Skia decodes that directly, so no frame ever passes
 * through the player's video path.
 */
object AudioCover {

    fun load(file: File): ImageBitmap? {
        val key = ImageBitmapCache.key(file.absolutePath, 0)
        ImageBitmapCache[key]?.let { return it }
        val bytes = embedded(file) ?: sidecar(file)?.readBytes() ?: return null
        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
        ImageBitmapCache[key] = bitmap
        return bitmap
    }

    private fun embedded(file: File): ByteArray? {
        val grabber = FFmpegFrameGrabber(file)
        try {
            grabber.start()
            val context = grabber.formatContext ?: return null
            for (index in 0 until context.nb_streams()) {
                val stream = context.streams(index)
                if (stream.disposition() and AV_DISPOSITION_ATTACHED_PIC == 0) continue
                val packet = stream.attached_pic()
                if (packet.size() <= 0) continue
                val bytes = ByteArray(packet.size())
                packet.data().get(bytes)
                return bytes
            }
            return null
        } finally {
            try {
                grabber.stop()
                grabber.release()
            } catch (_: Exception) {}
        }
    }

    /** `cover.jpg` and its kin beside [file], matched without regard to case. */
    internal fun sidecar(file: File): File? {
        val siblings = file.absoluteFile.parentFile?.listFiles().orEmpty()
        return SIDECAR_NAMES.firstNotNullOfOrNull { name ->
            siblings.firstOrNull {
                it.isFile && it.nameWithoutExtension.equals(name, ignoreCase = true) &&
                    it.extension.lowercase() in SIDECAR_EXTENSIONS
            }
        }
    }
}
