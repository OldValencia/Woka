package com.woka.probe

import androidx.compose.ui.graphics.asSkiaBitmap
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.EXIF_EXPECTED_HEIGHT
import com.woka.fixtures.EXIF_EXPECTED_WIDTH
import com.woka.fixtures.EXIF_ROTATED
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.SampleMedia
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test

/** JPEG segment markers, by the two bytes that introduce them. */
private fun markers(bytes: ByteArray): List<String> {
    if (bytes.size < 4) return emptyList()
    val found = mutableListOf<String>()
    var p = 2
    while (p + 4 <= bytes.size) {
        if (bytes[p].toInt() and 0xFF != 0xFF) break
        val marker = bytes[p + 1].toInt() and 0xFF
        if (marker == 0xDA || marker == 0xD9) break
        val length = ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
        if (length < 2 || p + 2 + length > bytes.size) break
        found += "%02X".format(marker) + if (marker == 0xE1) "(APP1)" else ""
        p += 2 + length
    }
    return found
}

/**
 * What EXIF survives, and whether the decode path already honours `Orientation`.
 *
 * §6 says every colour and metadata claim is probe-first. Two questions here:
 * does a rotated JPEG open upright, and does a Skia re-encode keep the `APP1`
 * segment that carries the camera, the lens, the date and the GPS.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.EditorExifProbe" -i
 */
class EditorExifProbe {

    @Test
    fun `orientation on the way in, and metadata on the way out`() {
        val rotated = SampleMedia.file(EXIF_ROTATED)
        val decoded = ImageLoader.load(rotated)
        val upright = decoded.width == EXIF_EXPECTED_WIDTH && decoded.height == EXIF_EXPECTED_HEIGHT

        println("Editor EXIF probe")
        println(
            renderTable(
                listOf("question", "answer"),
                listOf(
                    listOf(
                        "`$EXIF_ROTATED` decoded through `ImageLoader`",
                        "${decoded.width}×${decoded.height}"
                    ),
                    listOf(
                        "the tag is applied for us",
                        presenceCell(upright) +
                            " (expected ${EXIF_EXPECTED_WIDTH}×$EXIF_EXPECTED_HEIGHT if applied)"
                    )
                )
            )
        )

        val sourceBytes = SampleMedia.file(STILL_JPG).readBytes()
        val sourceMarkers = markers(sourceBytes)
        val rotatedMarkers = markers(rotated.readBytes())

        val reencoded = Image.makeFromBitmap(ImageLoader.load(rotated).asSkiaBitmap())
            .encodeToData(EncodedImageFormat.JPEG, 90)
            ?.bytes
        val reencodedMarkers = reencoded?.let { markers(it) } ?: emptyList()

        println()
        println(
            renderTable(
                listOf("bytes", "segments", "carries APP1"),
                listOf(
                    listOf("`$STILL_JPG` as shipped", sourceMarkers.joinToString(" "), presenceCell(sourceMarkers.any { it.contains("APP1") })),
                    listOf("`$EXIF_ROTATED` as shipped", rotatedMarkers.joinToString(" "), presenceCell(rotatedMarkers.any { it.contains("APP1") })),
                    listOf("after a skia JPEG re-encode", reencodedMarkers.joinToString(" "), presenceCell(reencodedMarkers.any { it.contains("APP1") }))
                )
            )
        )
        println()
        println("If the last row says **no**, an edited JPEG loses camera, lens, date and GPS")
        println("unless ME-104 splices the source APP1 back in with Orientation forced to 1.")
    }
}
