package com.woka.unit

import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.support.startKoinOnce
import org.koin.core.context.GlobalContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which of the declared tools a video's rail offers (ME-264).
 *
 * **The app's own registry**, resolved from Koin, rather than one built over a fixture:
 * what is being checked is a decision each tool makes in its own manifest, and a fixture
 * would only prove that `EditorToolRegistry` can filter — which `EditorToolRegistryTest`
 * already proves. The failure this catches is a tool joining `editorModule` and quietly
 * appearing on a video because `supports` defaults to both kinds.
 *
 * **That default is deliberate and is why this names the exception rather than the whole
 * rail.** Every tool reused on video is reused *because* it did not have to say anything;
 * asserting the full video rail would turn adding an image-and-video tool into a test
 * edit.
 */
class VideoRailToolsTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun registry(): EditorToolRegistry = GlobalContext.get().get()

    @Test
    fun `HDR is declared, and offered to stills alone`() {
        val hdr = registry().all().single { it.manifest.id == "hdr" }

        assertEquals(setOf(MediaKind.Image), hdr.manifest.supports)
    }

    @Test
    fun `a video rail excludes HDR and keeps the reused tools`() {
        val registry = registry()

        val onVideo = registry.tools(MediaKind.Video).value.map { it.manifest.id }
        val onStills = registry.tools(MediaKind.Image).value.map { it.manifest.id }

        assertTrue("hdr" !in onVideo, "the HDR tool is still in a video's rail: $onVideo")
        assertTrue("hdr" in onStills, "the HDR tool left a photograph's rail too")
        // The five the author asked to reuse, plus Transform, which came along free.
        listOf("crop", "transform", "adjust", "filter", "markup", "resize").forEach { id ->
            assertTrue(id in onVideo, "$id stopped being offered on video: $onVideo")
        }
    }
}
