package com.woka.probe

import com.woka.core.data.RAW_IMAGE_EXTENSIONS
import com.woka.feature.viewer.image.RawStillDecoder
import java.io.File
import kotlin.test.Test

/** A folder of real camera files — too large to commit, so pointed at rather than shipped. */
private const val SAMPLES_ENV = "WOKA_RAW_SAMPLES"

/**
 * Which RAW extensions LibRaw actually develops — the measurement behind
 * `RAW_IMAGE_EXTENSIONS`. Run by hand over a folder of samples (raw.pixls.us):
 *
 *     $env:WOKA_RAW_SAMPLES = "D:\raw"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.RawFormatProbe" -i
 *
 * Each file goes through the viewer's own decoder — size, half-size preview,
 * full development — and the table says how long each took. Skips without the
 * variable; asserts nothing.
 */
class RawFormatProbe {

    @Test
    fun `which RAW samples LibRaw develops`() {
        val folder = System.getenv(SAMPLES_ENV)?.let(::File) ?: return
        println("file | native size | half | full | ms (half / full)")
        folder.listFiles().orEmpty()
            .filter { it.extension.lowercase() in RAW_IMAGE_EXTENSIONS }
            .sortedBy { it.extension.lowercase() }
            .forEach { file -> println(row(file)) }
    }

    private fun row(file: File): String = runCatching {
        val size = RawStillDecoder.nativeSize(file, 0)
        val halfStart = System.nanoTime()
        val half = RawStillDecoder.halfSize(file)
        val fullStart = System.nanoTime()
        val full = RawStillDecoder.frame(file, 0)
        val end = System.nanoTime()
        "${file.name} | ${size.width}×${size.height} | ${half.width}×${half.height} | " +
            "${full.width}×${full.height} | ${(fullStart - halfStart) / 1_000_000} / ${(end - fullStart) / 1_000_000}"
    }.getOrElse {
        "${file.name} | FAILED: ${it::class.simpleName}: ${it.message?.take(80)} " +
            it.stackTrace.take(6).joinToString(" < ")
    }
}
