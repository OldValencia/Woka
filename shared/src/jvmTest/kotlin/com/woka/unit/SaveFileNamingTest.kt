package com.woka.unit

import com.woka.feature.editor.core.copyName
import kotlin.test.Test
import kotlin.test.assertEquals

/** A predicate instead of a directory: the naming rule needs no filesystem. */
private fun taken(vararg names: String): (String) -> Boolean = { it in names }

class SaveFileNamingTest {

    @Test
    fun `the first copy is the plain suffix`() {
        assertEquals("photo-edited.jpg", copyName("photo", "jpg", taken()))
    }

    @Test
    fun `the second copy is numbered from two`() {
        assertEquals(
            "photo-edited-2.jpg",
            copyName("photo", "jpg", taken("photo-edited.jpg"))
        )
    }

    @Test
    fun `numbering walks past every taken name`() {
        val existing = (2..9).map { "photo-edited-$it.jpg" } + "photo-edited.jpg"
        assertEquals(
            "photo-edited-10.jpg",
            copyName("photo", "jpg", taken(*existing.toTypedArray()))
        )
    }

    @Test
    fun `a changed extension does not drag the old one into the stem`() {
        // A converted HEIC: the caller passes the stem and the *new* extension, so
        // `photo.heic-edited.jpg` is not expressible.
        assertEquals("photo-edited.jpg", copyName("photo", "jpg", taken()))
        assertEquals("photo-edited.png", copyName("photo", "png", taken()))
    }

    @Test
    fun `a source already named edited does not gain a second suffix`() {
        assertEquals(
            "photo-edited-2.jpg",
            copyName("photo-edited", "jpg", taken("photo-edited.jpg"))
        )
        // …and when the first is free it stays as it is.
        assertEquals("photo-edited.jpg", copyName("photo-edited", "jpg", taken()))
    }

    @Test
    fun `dots inside the stem are part of the name, not an extension`() {
        assertEquals("my.photo.v2-edited.jpg", copyName("my.photo.v2", "jpg", taken()))
    }

    @Test
    fun `a file with no extension gets none invented`() {
        assertEquals("photo-edited", copyName("photo", "", taken()))
    }
}
