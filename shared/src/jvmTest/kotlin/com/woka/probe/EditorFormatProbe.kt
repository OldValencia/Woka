package com.woka.probe

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.ANIM_AVIF
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.fixtures.SampleMedia
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.test.Test

/**
 * The conversion matrix §7 needs: what survives a decode → Skia encode → decode
 * round trip, and what it costs.
 *
 * **It reports, it does not assert** (`code-style/test.md`). "Skia cannot write
 * this format" is the input to a decision, not a regression.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.EditorFormatProbe" -i
 */
class EditorFormatProbe {

    private val sources = listOf(
        STILL_PNG, STILL_JPG, STILL_WEBP, STILL_AVIF,
        BURST_HEIC, ANIM_GIF, ANIM_WEBP, ANIM_AVIF, MULTIPAGE_TIFF
    )

    private val targets = listOf(
        "png" to EncodedImageFormat.PNG,
        "jpg" to EncodedImageFormat.JPEG,
        "webp" to EncodedImageFormat.WEBP
    )

    @Test
    fun `what a round trip through skia costs each format`() {
        val rows = sources.flatMap { name -> targets.map { row(name, it.first, it.second) } }
        println("Editor format round trip — decode, re-encode with skia, decode again")
        println(renderTable(listOf("source", "→", "size", "max Δ", "encode", "bytes"), rows))
    }

    private fun row(name: String, label: String, format: EncodedImageFormat): List<String> {
        val file = SampleMedia.file(name)
        val decoded = runCatching { ImageLoader.load(file) }.getOrElse {
            return listOf(name, label, "**✗** decode: ${it.message.orEmpty().take(40)}", "—", "—", "—")
        }
        val started = System.nanoTime()
        val encoded = runCatching {
            Image.makeFromBitmap(decoded.asSkiaBitmap())
                .encodeToData(format, ENCODE_QUALITY)
                ?.bytes
        }.getOrNull()
            ?: return listOf(name, label, "**✗** skia cannot write $label", "—", "—", "—")
        val encodeMs = (System.nanoTime() - started) / 1_000_000

        val reread = runCatching { Image.makeFromEncoded(encoded).toComposeImageBitmap() }.getOrNull()
            ?: return listOf(name, label, "**✗** re-decode failed", "—", "${encodeMs}ms", "${encoded.size / 1024}KB")

        val sizeCell = "${reread.width}×${reread.height}" +
            if (reread.width == decoded.width && reread.height == decoded.height) "" else " **≠ source**"
        return listOf(
            name,
            label,
            sizeCell,
            maxChannelDelta(decoded, reread)?.toString() ?: "—",
            "${encodeMs}ms",
            "${encoded.size / 1024}KB"
        )
    }

    /**
     * Sampled on a grid rather than every pixel: the question is "did the encode
     * change the picture", which a few hundred samples answer, and a full compare
     * of a 24 MP pair is slower than the encode being measured.
     */
    private fun maxChannelDelta(before: ImageBitmap, after: ImageBitmap): Int? {
        if (before.width != after.width || before.height != after.height) return null
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var worst = 0
        val stepX = maxOf(1, before.width / 16)
        val stepY = maxOf(1, before.height / 16)
        var y = 0
        while (y < before.height) {
            var x = 0
            while (x < before.width) {
                val one = a[x, y]
                val two = b[x, y]
                worst = maxOf(
                    worst,
                    maxOf(
                        abs((one.red * 255).toInt() - (two.red * 255).toInt()),
                        maxOf(
                            abs((one.green * 255).toInt() - (two.green * 255).toInt()),
                            abs((one.blue * 255).toInt() - (two.blue * 255).toInt())
                        )
                    )
                )
                x += stepX
            }
            y += stepY
        }
        return worst
    }

    private companion object {
        /** JPEG/WebP fidelity; PNG reads it as compression effort (see `PngEncoding`). */
        const val ENCODE_QUALITY = 90
    }
}
