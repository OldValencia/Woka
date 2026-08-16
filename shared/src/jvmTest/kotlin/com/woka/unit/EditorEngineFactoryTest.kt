package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.fixtures.STILL_HEIGHT
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WIDTH
import com.woka.fixtures.SampleMedia
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Editability is decided in one place, off `MediaKind` (§4.1).
 *
 * Driven against the committed corpus rather than invented paths, so `sourceSize`
 * is checked against a file whose real dimensions are known.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorEngineFactoryTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun withFactory(block: (EditorEngineFactoryImpl) -> Unit) =
        withFoldRegistry(emptyList()) { registry, _ ->
            block(EditorEngineFactoryImpl(registry))
        }

    @Test
    fun `a png resolves to an image engine that knows its native size`() = withFactory { factory ->
        val engine = assertNotNull(factory.create(SampleMedia.file(STILL_PNG).absolutePath))
        assertEquals(MediaKind.Image, engine.kind)
        assertEquals(IntSize(STILL_WIDTH, STILL_HEIGHT), engine.sourceSize)
    }

    @Test
    fun `a jpg resolves too`() = withFactory { factory ->
        val engine = assertNotNull(factory.create(SampleMedia.file(STILL_JPG).absolutePath))
        assertEquals(MediaKind.Image, engine.kind)
    }

    @Test
    fun `a video resolves to nothing until the video engine exists`() = withFactory { factory ->
        assertNull(factory.create("D:/Films/a.mp4"))
    }

    @Test
    fun `an unsupported extension resolves to nothing`() = withFactory { factory ->
        assertNull(factory.create("D:/Notes/readme.txt"))
        assertNull(factory.create("D:/Notes/archive.zip"))
    }

    @Test
    fun `editability is a question about the format, not about the bytes`() = withFactory { factory ->
        // A path that does not exist still yields an engine: greying out a menu
        // item must not cost a decode of every file in the grid.
        assertNotNull(factory.create("D:/Nowhere/missing.png"))
    }
}
