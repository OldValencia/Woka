package com.woka.unit

import com.woka.core.data.isReadOnlyMedia
import com.woka.feature.editor.EditorNoticeStatus
import com.woka.feature.editor.READONLY_MEDIA_EDIT_NOTICE
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadOnlyMediaEditNoticeTest {

    @BeforeTest
    fun reset() {
        EditorNoticeStatus.clear()
    }

    @Test
    fun `photoshop family files classify as read only media`() {
        val readOnlyExtensions = listOf("psd", "psb", "psdt", "pdd", "PSD", "PSB")
        readOnlyExtensions.forEach { ext ->
            assertTrue(File("test.$ext").isReadOnlyMedia, "Expected .$ext to be read-only")
        }
    }

    @Test
    fun `editable still and video files are not read only media`() {
        val otherExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "mp4", "mkv")
        otherExtensions.forEach { ext ->
            assertFalse(File("test.$ext").isReadOnlyMedia, "Expected .$ext to NOT be read-only")
        }
    }

    @Test
    fun `editor notice status displays and clears the forbidden edit message`() {
        assertNull(EditorNoticeStatus.message)
        EditorNoticeStatus.show()
        assertEquals(READONLY_MEDIA_EDIT_NOTICE, EditorNoticeStatus.message)
        assertEquals("Нельзя редактировать, нет функционала", EditorNoticeStatus.message)
        EditorNoticeStatus.clear()
        assertNull(EditorNoticeStatus.message)
    }
}
