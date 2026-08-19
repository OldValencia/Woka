package com.woka.unit

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_WEBP
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §5.1's three buckets, asked of the committed corpus rather than of invented
 * paths — the whole question is about real containers.
 *
 * The set here is a **snapshot, not a rule**: every carrier ME-108 adds moves a
 * row from `Unavailable` to `Recorded`, and this test is where that is recorded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditCapabilityTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun withFactory(block: (EditorEngineFactoryImpl) -> Unit) = withTempStorageRoot {
        block(EditorEngineFactoryImpl(EditorToolRegistry(emptyList(), SettingsRepositoryImpl())))
    }

    private fun EditorEngineFactoryImpl.of(fixture: String): EditCapability =
        capability(SampleMedia.file(fixture).absolutePath)

    @Test
    fun `a jpeg and a png carry their own history`() = withFactory { factory ->
        assertEquals(EditCapability.Recorded, factory.of(STILL_JPG))
        assertEquals(EditCapability.Recorded, factory.of(STILL_PNG))
    }

    /** Until ME-108's RIFF carrier promotes it to `VP8X`, this is the only bucket B. */
    /**
     * **Bucket B is empty** (ME-137). `Destructive` is still reachable in code — it is
     * the fallback for a format that ever gains an encoder before a carrier — but no
     * format Woka lists lands there any more, which is what the enum's own doc predicted.
     */
    @Test
    fun `a webp records its edit rather than being re-encoded`() = withFactory { factory ->
        assertEquals(EditCapability.Recorded, factory.of(STILL_WEBP))
        assertEquals(EditCapability.Recorded, factory.of(ANIM_WEBP))
    }

    /** ME-108's row: the `uuid` box moved this family from the test below to here. */
    @Test
    fun `an ISOBMFF photograph records its edit`() = withFactory { factory ->
        assertEquals(EditCapability.Recorded, factory.of(BURST_HEIC))
        assertEquals(EditCapability.Recorded, factory.of(STILL_AVIF))
    }

    /** ME-139 took TIFF; what is left is GIF, and it is left on purpose. */
    @Test
    fun `a tiff records its edit, pages and all`() = withFactory { factory ->
        assertEquals(EditCapability.Recorded, factory.of(MULTIPAGE_TIFF))
    }

    /**
     * **GIF is deferred, not missing** (ME-138): nothing in Woka animates one, so the slot
     * its format provides is left unwritten until something plays it. `bmp` has no slot at
     * all and never will.
     */
    @Test
    fun `a container with neither a slot nor an encoder is not editable`() = withFactory { factory ->
        assertEquals(EditCapability.Unavailable, factory.of(ANIM_GIF))
    }

    @Test
    fun `a video and a missing file are not editable`() = withFactory { factory ->
        assertEquals(EditCapability.Unavailable, factory.capability("D:/Films/a.mp4"))
        assertEquals(EditCapability.Unavailable, factory.capability("D:/Nowhere/missing.png"))
        assertEquals(EditCapability.Unavailable, factory.capability("D:/Notes/readme.txt"))
    }
}
