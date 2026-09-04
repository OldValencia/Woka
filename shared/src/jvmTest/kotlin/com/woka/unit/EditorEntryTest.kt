package com.woka.unit

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.isEditable
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single decision both doors ask.
 *
 * The interesting assertion is the last one: this runs while a context menu is being
 * drawn, so it has to be cheap. If it ever starts decoding, a folder of 40-megapixel
 * photographs will hitch every time somebody right-clicks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorEntryTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun withFactory(block: (EditorEngineFactoryImpl) -> Unit) = withTempStorageRoot {
        val registry = EditorToolRegistry(emptyList(), SettingsRepositoryImpl())
        block(EditorEngineFactoryImpl(registry))
    }

    @Test
    fun `the three encodable formats are editable`() = withFactory { factory ->
        listOf(STILL_PNG, STILL_JPG, STILL_WEBP).forEach { name ->
            assertTrue(
                isEditable(factory, SampleMedia.file(name).absolutePath),
                "$name should be editable"
            )
        }
    }

    @Test
    fun `a video with a carrier is editable`() = withFactory { factory ->
        // **Reversed by ME-201**, and this is the assertion that says so. It read
        // `assertFalse` while the factory answered null for every video; the canvas now
        // draws the player's frame through the folded document, so both doors offer `Edit`.
        val video = SampleMedia.file("livephoto.mov")
        assertTrue(isEditable(factory, video.absolutePath))
    }

    @Test
    fun `and one without is not, until its carrier lands`() = withFactory { factory ->
        // **Narrowed by ME-288.** Every video was editable while a capability existed
        // whose whole meaning was "no save yet"; now the container is asked, and Matroska
        // has no slot until ME-282 writes one. The refusal belongs at the menu rather than
        // at `Done` — the line moved forward for eight image formats in 2026-08-19.
        val video = SampleMedia.file("WokaSample.S01E02.2160p.HDR.TEST.mkv")
        assertFalse(isEditable(factory, video.absolutePath))
    }

    @Test
    fun `an unknown extension is not editable`() = withFactory { factory ->
        assertFalse(isEditable(factory, "C:/nowhere/notes.txt"))
    }

    @Test
    fun `a path that does not exist is not editable`() = withFactory { factory ->
        assertFalse(isEditable(factory, File("no-such-file.png").absolutePath))
    }

    @Test
    fun `deciding costs no decode`() = withFactory { factory ->
        val before = ImageBitmapCache.retainedBytes

        repeat(20) { isEditable(factory, SampleMedia.file(STILL_PNG).absolutePath) }

        // `sourceSize` is lazy and nothing here touches it, so twenty questions cost
        // twenty stats and not one pixel.
        assertEquals(before, ImageBitmapCache.retainedBytes)
    }
}
