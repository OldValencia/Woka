package com.woka.probe

import androidx.compose.ui.graphics.asSkiaBitmap
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.support.withTempStorageRoot
import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/** The proxy cap from §3.2: the canvas never draws anything larger. */
private const val PROXY_MAX_EDGE = 2560

/**
 * What a very large photograph costs, which is the number §10's "very large
 * sources" gap is waiting on — it decides whether the editor refuses, warns, or
 * tiles.
 *
 * The sources are **generated**, not committed: a 100 MP PNG is 400 MB of pixels
 * and has no business in the repository. They are built into the temp root once
 * per run and go with it.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.EditorMemoryProbe" -i
 */
class EditorMemoryProbe {

    private val sizes = listOf(
        "24 MP" to (6000 to 4000),
        "100 MP" to (11600 to 8700)
    )

    @Test
    fun `decode, proxy and full-size render against the heap`() = withTempStorageRoot { root ->
        val binary = runCatching { Loader.load(ffmpeg::class.java) }.getOrNull()
        if (binary == null) {
            println("Editor memory probe — the bundled ffmpeg could not be unpacked; nothing measured")
            return@withTempStorageRoot
        }

        val maxHeapMb = Runtime.getRuntime().maxMemory() / 1024 / 1024
        val rows = sizes.mapNotNull { (label, dimensions) ->
            row(binary, root, label, dimensions.first, dimensions.second)
        }

        println("Editor memory probe — heap cap ${maxHeapMb}MB, cache budget ${ImageBitmapCache.budgetBytes / 1024 / 1024}MB")
        println(
            renderTable(
                listOf("source", "pixels", "decode", "native MB", "proxy", "full render", "peak MB"),
                rows
            )
        )
        println()
        println("`native MB` is the decoded bitmap at 4 bytes per pixel — what one entry costs")
        println("`ImageBitmapCache`, whose whole budget is ${ImageBitmapCache.budgetBytes / 1024 / 1024}MB.")
        println("`peak MB` is JVM heap in use after the full-size render, not native Skia memory,")
        println("so it is a floor on the real cost rather than the whole of it.")
    }

    private fun row(
        binary: String,
        root: File,
        label: String,
        width: Int,
        height: Int
    ): List<String>? {
        val file = File(root, "large_$width.png")
        if (!synthesise(binary, file, width, height)) return null

        val decodeStart = System.nanoTime()
        val decoded = runCatching { ImageLoader.load(file) }.getOrElse {
            return listOf(label, "${width}×$height", "**✗** ${it.message.orEmpty().take(40)}", "—", "—", "—", "—")
        }
        val decodeMs = (System.nanoTime() - decodeStart) / 1_000_000
        val nativeMb = decoded.width.toLong() * decoded.height * 4 / 1024 / 1024

        val proxyStart = System.nanoTime()
        val scale = PROXY_MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
        val proxy = Surface.makeRasterN32Premul(
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1)
        )
        proxy.canvas.drawImageRect(
            Image.makeFromBitmap(decoded.asSkiaBitmap()),
            Rect.makeWH(proxy.width.toFloat(), proxy.height.toFloat())
        )
        val proxyMs = (System.nanoTime() - proxyStart) / 1_000_000

        val renderStart = System.nanoTime()
        val full = Surface.makeRasterN32Premul(decoded.width, decoded.height)
        full.canvas.drawImage(Image.makeFromBitmap(decoded.asSkiaBitmap()), 0f, 0f)
        full.makeImageSnapshot()
        val renderMs = (System.nanoTime() - renderStart) / 1_000_000

        val runtime = Runtime.getRuntime()
        val peakMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024

        return listOf(
            label,
            "${decoded.width}×${decoded.height}",
            "${decodeMs}ms",
            "$nativeMb",
            "${proxy.width}×${proxy.height} in ${proxyMs}ms",
            "${renderMs}ms",
            "$peakMb"
        )
    }

    /** `lavfi` rather than a committed file: 400 MB of pixels is not a fixture. */
    private fun synthesise(binary: String, target: File, width: Int, height: Int): Boolean {
        val process = ProcessBuilder(
            binary, "-y", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=size=${width}x$height",
            "-frames:v", "1", target.absolutePath
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return false
        }
        return process.exitValue() == 0 && target.isFile
    }
}
