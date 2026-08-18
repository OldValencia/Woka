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
    fun `a video is not editable`() = withFactory { factory ->
        // Not "not yet" from this side — the factory returns null for video until
        // ME-201, and the menu greys out rather than opening a shell with no canvas.
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
