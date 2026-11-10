package com.woka.feature.editor.engine.video.render

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.HdrView
import com.woka.core.data.applyTo
import com.woka.core.data.replaceAtomically
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorExport
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.codedColour
import com.woka.feature.editor.engine.video.hdrStageFor
import com.woka.feature.editor.engine.video.previewFold
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import com.woka.feature.viewer.video.engine.probe.clockwiseQuarterTurns
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.io.File

/** `Done ▾`'s second way out (ME-294): the frame under the play head, as a photograph. */
internal val SaveFramePhoto = EditorExport(
    id = "frame-photo",
    title = "Save frame as photo",
    summary = "This frame as a JPEG beside the video."
)

private const val JPEG_QUALITY = 100

/**
 * The frame at timeline [atMs], drawn as the canvas draws it, written beside [source] as a JPEG.
 *
 * **The canvas's picture, at the film's coded size**: the clip under the play head through its
 * own look, the shared layer's crop and colour, markup as it stands at that moment, the HDR mode
 * the canvas is in — and no Resize, which sizes a render and nothing else (`previewFold`). The
 * frame is decoded to the exact one asked for, not the keyframe before it, and turned upright
 * the way a phone's film plays. Never an overwrite: `name 1m05s.jpg`, then `name 1m05s 2.jpg`.
 */
internal fun saveFramePhoto(
    source: File,
    document: EditDocument,
    looks: (Int) -> List<ToolOp>,
    registry: EditorToolRegistry,
    sourceSize: IntSize,
    atMs: Long
): EditSaveResult {
    val clips = TrimTool.clipsIn(document)
    val at = clips?.atTimeline(atMs.coerceIn(0L, (clips.totalMs() - 1L).coerceAtLeast(0L)))
    val sourceMs = at?.sourceMs ?: atMs
    val key = at?.let { clips[it.index].key } ?: 0
    val colour = HdrView.mode.applyTo(codedColour(source)).takeIf { it.needsColorManagement }
    val recipe = previewFold(EditDocument(document.ops + looks(key)), registry, sourceSize)
        .copy(source = hdrStageFor(colour), atMs = atMs)
    val (frame, turns) = decodeAt(source, sourceMs) ?: return EditSaveResult.Failed("No frame at that point could be read.")
    val drawn = renderAtNativeScale(frame, sourceSize, recipe)
    val upright = drawn.turnedClockwise(turns)
    val bytes = upright.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)?.bytes
    if (upright !== drawn) upright.close()
    drawn.close()
    bytes ?: return EditSaveResult.Failed("The frame could not be encoded.")
    val target = photoBeside(source, atMs)
    replaceAtomically(target, bytes)
    return EditSaveResult.Saved(target.absolutePath)
}

/** The first frame at or after [sourceMs] — decoded forward from the seek — and the film's quarter turns. */
private fun decodeAt(source: File, sourceMs: Long) = runCatching {
    val grabber = FreshFrameGrabber(source).apply { pixelFormat = AV_PIX_FMT_RGBA }
    try {
        grabber.start()
        val start = containerStartUs(grabber)
        val wantedUs = sourceMs * 1000L
        grabber.setTimestamp(start + wantedUs)
        var shown: Frame? = null
        while (true) {
            val frame = grabber.grabImage() ?: break
            shown = frame
            if (frame.timestamp - start >= wantedUs) break
        }
        shown?.let { it.toComposeBitmap() to clockwiseQuarterTurns(grabber.displayRotation) }
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}.getOrNull()

/** This image turned [turns] quarter turns clockwise; itself for none. */
private fun Image.turnedClockwise(turns: Int): Image {
    val quarter = turns.mod(4)
    if (quarter == 0) return this
    val sideways = quarter % 2 == 1
    val w = if (sideways) height else width
    val h = if (sideways) width else height
    return Surface.makeRasterN32Premul(w, h).use { surface ->
        val canvas = surface.canvas
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(90f * quarter)
        canvas.translate(-width / 2f, -height / 2f)
        canvas.drawImage(this, 0f, 0f)
        surface.makeImageSnapshot()
    }
}

/** `clip 1m05s.jpg` beside the film, numbered past any that exist. */
private fun photoBeside(source: File, atMs: Long): File {
    val seconds = atMs / 1000L
    val stamp = "${seconds / 60}m${(seconds % 60).toString().padStart(2, '0')}s"
    val dir = source.absoluteFile.parentFile
    fun named(counter: Int) =
        File(dir, if (counter == 1) "${source.nameWithoutExtension} $stamp.jpg" else "${source.nameWithoutExtension} $stamp $counter.jpg")
    var counter = 1
    while (named(counter).exists()) counter++
    return named(counter)
}
