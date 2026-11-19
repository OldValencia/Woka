package com.woka.integration

import androidx.compose.ui.unit.IntSize
import com.woka.app.AppDependencies
import com.woka.core.data.TransferKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.video.render.renderVideo
import com.woka.feature.editor.engine.video.render.saveFramePhoto
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.probe.isDoviProfile5
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_METADATA
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoviExportTest {
    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `P5 photo and encoded edit are SDR while a packet copy retains Dolby Vision`() = withTempStorageRoot { root ->
        val sample = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", sample.isFile)
        val source = sample.copyTo(File(root, "p5.mp4"))
        val before = digest(source)
        val registry = EditorToolRegistry(listOf(TrimTool), AppDependencies.settingsRepository)
        val size = IntSize(1920, 1080)
        val photo = saveFramePhoto(source, EditDocument(), { emptyList() }, registry, size, 5_000L)
        val jpeg = ImageIO.read(File(assertIs<EditSaveResult.Saved>(photo).path))
        assertEquals(1920, jpeg.width)
        assertEquals(1080, jpeg.height)
        // The authored purple patch stays coloured after the shared HDR-to-SDR stage.
        val pixel = jpeg.getRGB(100, 100)
        assertTrue((pixel and 255) > ((pixel shr 8) and 255))

        fun render(clip: TrimClip): File {
            val document = EditDocument(ops = listOf(TrimTool.opFor(listOf(clip))))
            val result = renderVideo(source, document, { emptyList() }, registry, size, { true }, {})
            return File(assertIs<EditSaveResult.Saved>(result).path)
        }
        val encoded = render(TrimClip("encode", 5_000L, 5_200L, speed = 2f))
        FFmpegFrameGrabber(encoded).use { grabber ->
            grabber.start()
            assertFalse(grabber.isDoviProfile5())
            assertEquals(TransferKind.SDR, grabber.readStreamHdrInfo().transfer)
            assertNotNull(grabber.grabImage())
        }
        val copied = render(TrimClip("copy", 0L, 500L))
        FreshFrameGrabber(copied).use { grabber ->
            grabber.start()
            assertTrue(grabber.isDoviProfile5())
            assertNotNull(grabber.grabImage())
            assertNotNull(av_frame_get_side_data(grabber.lastPicture, AV_FRAME_DATA_DOVI_METADATA))
        }
        assertContentEquals(before, digest(source))
        assertNull(root.listFiles()?.firstOrNull { it.name.endsWith(".part") })
    }

    private fun digest(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest()
    }
}
