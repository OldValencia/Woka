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

    /** HDR left the editor with RD-401: a copy takes the screen's colour mode instead. */
    @Test
    fun `HDR is no longer declared`() {
        assertTrue(registry().all().none { it.manifest.id == "hdr" })
    }

    @Test
    fun `a video rail keeps the reused tools, and the cut stays off a photograph's`() {
        val registry = registry()

        val onVideo = registry.tools(MediaKind.Video).value.map { it.manifest.id }
        val onStills = registry.tools(MediaKind.Image).value.map { it.manifest.id }

        assertEquals(setOf(MediaKind.Video), registry.all().single { it.manifest.id == "trim" }.manifest.supports)
        assertTrue("trim" !in onStills, "the cut is in a photograph's rail: $onStills")
        // The five the author asked to reuse, plus Transform, which came along free.
        listOf("crop", "transform", "adjust", "filter", "markup", "resize").forEach { id ->
            assertTrue(id in onVideo, "$id stopped being offered on video: $onVideo")
        }
    }
}
