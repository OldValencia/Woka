package com.woka.unit

import com.woka.feature.editor.save.SaveFormat
import com.woka.feature.editor.save.formatFor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The quality policy (§9, 2026-08-30) and the refusal that goes with it.
 *
 * The loop is the point: a fourth format added at 90 because that is what its encoder
 * defaults to fails here rather than shipping a number nobody chose.
 */
class SaveFormatTest {

    @Test
    fun `every format is written at the ceiling`() {
        SaveFormat.entries.forEach {
            assertEquals(100, it.quality, "${it.name} is not written at q100")
        }
    }

    @Test
    fun `the writable extensions route to their encoder, whatever their case`() {
        assertEquals(SaveFormat.PNG, formatFor(File("picture.png")))
        assertEquals(SaveFormat.JPEG, formatFor(File("photo.jpg")))
        // Both spellings encode as JPEG, and the lowercasing is what makes a camera's
        // `.JPG` reach the same branch as a phone's `.jpg`.
        assertEquals(SaveFormat.JPEG, formatFor(File("photo.jpeg")))
        assertEquals(SaveFormat.JPEG, formatFor(File("PHOTO.JPG")))
        assertEquals(SaveFormat.WEBP, formatFor(File("photo.webp")))
    }

    @Test
    fun `an extension Skia cannot write is refused rather than substituted`() {
        listOf("shot.bmp", "photo.heic", "photo.heif", "scan.tif", "scan.tiff", "anim.gif")
            .forEach { assertNull(formatFor(File(it)), "$it was accepted") }
    }
}
