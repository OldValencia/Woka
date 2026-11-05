package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.TransferKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.probe.CLIP_FPS
import com.woka.probe.clipRecorder
import com.woka.probe.writeClip
import com.woka.support.MainDispatcherRule
import com.woka.support.cuts
import com.woka.support.render
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HDR through a render: a PQ film that is encoded goes through the canvas's own tone map,
 * and one that is only cut is copied with its colour intact. The corpus has no HDR video
 * (`*.HDR.TEST.mkv` are SDR h264), so the PQ film is the generated clip with its stream
 * tagged — the pixels are the same, which is what lets the two renders be compared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRenderHdrTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `an encoded PQ film is tone mapped`() = onTwins { plain, pq, registry ->
        val retimed = EditDocument(ops = listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 2_000L, speed = 2f)))))
        val asIs = meanLevel(render(plain, retimed, registry))
        val mapped = meanLevel(render(pq, retimed, registry))
        assertTrue(abs(asIs - mapped) > 10.0, "the PQ render looks like the SDR one: $asIs vs $mapped")
    }

    @Test
    fun `a cut PQ film is copied and stays PQ`() = onTwins { _, pq, registry ->
        val out = render(pq, cuts(0L to 1_000L, 2_000L to 3_000L), registry)
        assertEquals(TransferKind.PQ, colourOf(out))
    }

    private fun onTwins(block: (File, File, EditorToolRegistry) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-render-hdr").toFile()
        try {
            val plain = File(dir, "plain.mkv").also { writeTagged(it, pq = false) }
            val pq = File(dir, "pq.mkv").also { writeTagged(it, pq = true) }
            assertEquals(TransferKind.PQ, colourOf(pq), "the fixture did not carry its PQ tag")
            block(plain, pq, EditorToolRegistry(listOf(TrimTool), SettingsRepositoryImpl()))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Four seconds, a keyframe every half second, tagged BT.2020 PQ when [pq]. */
    private fun writeTagged(target: File, pq: Boolean) {
        val recorder = clipRecorder(target, "libopenh264", 640, 360, gop = 15) {
            it.format = "matroska"
            it.videoBitrate = 1_500_000
            if (pq) {
                it.setVideoOption("color_trc", "smpte2084")
                it.setVideoOption("color_primaries", "bt2020")
            }
        }
        recorder.start()
        try {
            writeClip(recorder, 640, 360, 4 * CLIP_FPS)
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    private fun colourOf(file: File): TransferKind {
        val grabber = FFmpegFrameGrabber(file)
        try {
            grabber.start()
            return grabber.readStreamHdrInfo().transfer
        } finally {
            grabber.stop()
            grabber.release()
        }
    }

    /** Mean of the red, green and blue bytes of the frame half a second in. */
    private fun meanLevel(file: File): Double {
        val grabber = FFmpegFrameGrabber(file).apply { pixelFormat = AV_PIX_FMT_RGBA }
        try {
            grabber.start()
            grabber.setTimestamp(500_000L)
            val frame = generateSequence { grabber.grabImage() }.first { it.image != null }
            val bytes = frame.image[0] as ByteBuffer
            var sum = 0L
            var count = 0L
            for (at in 0 until bytes.limit() - 3 step 4) {
                sum += (bytes.get(at).toInt() and 0xFF) + (bytes.get(at + 1).toInt() and 0xFF) + (bytes.get(at + 2).toInt() and 0xFF)
                count += 3
            }
            return sum.toDouble() / count.coerceAtLeast(1L)
        } finally {
            grabber.stop()
            grabber.release()
        }
    }
}
