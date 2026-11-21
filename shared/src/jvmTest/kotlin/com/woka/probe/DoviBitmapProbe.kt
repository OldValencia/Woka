package com.woka.probe

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test

/** DV5-104: preserve the staging array's GC pressure, compare the native upload only. */
class DoviBitmapProbe {
    @Test
    fun `measure staging array installation into a 4K Skia bitmap`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val width = 3840
        val height = 2160
        val stride = width * 4
        val size = stride * height
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        val report = mutableListOf<String>()
        Arena.ofConfined().use { arena ->
            val source = arena.allocate(size.toLong()).fill(101)
            for (direct in listOf(false, true, false, true)) {
                var duration = 0L
                var checksum = 0L
                repeat(180) { index ->
                    val begin = System.nanoTime()
                    val staging = ByteArray(size)
                    MemorySegment.copy(source, 0, MemorySegment.ofArray(staging), 0, size.toLong())
                    Bitmap().use { bitmap ->
                        if (direct) {
                            check(bitmap.allocPixels(info, stride))
                            checkNotNull(bitmap.peekPixels()).use { pixels ->
                                val target = MemorySegment.ofAddress(pixels.addr).reinterpret(size.toLong())
                                MemorySegment.copy(MemorySegment.ofArray(staging), 0, target, 0, size.toLong())
                            }
                        } else {
                            check(bitmap.installPixels(info, staging, stride))
                        }
                        bitmap.setImmutable()
                        checksum += bitmap.getColor(width / 2, height / 2)
                    }
                    if (index >= 30) duration += System.nanoTime() - begin
                }
                report += "direct=$direct totalMs=${duration / 150e6} checksum=$checksum"
                println("DV5-BITMAP ${report.last()}")
            }
        }
        File("build/dv-p5/bitmap-throughput.txt").writeText(report.joinToString("\n"))
    }
}
