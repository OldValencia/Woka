package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.StillColorInfo
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.viewer.shell.MediaViewerCarousel
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.feature.viewer.video.engine.probe.containerLengthMs
import com.woka.feature.viewer.video.formatTime
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.SampleMedia
import com.woka.support.onUi
import com.woka.support.poster
import com.woka.support.startKoinOnce
import com.woka.support.withTempStorageRoot
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The carousel's badges on real files (RD-502, `#viewer/photo`): one chip per kind that has
 * something to say, nothing on a single still, and the edited dot on the file with a recipe.
 */
@OptIn(ExperimentalTestApi::class)
class CarouselBadgesUiTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun copy(dir: File, fixture: String, name: String = fixture.substringAfterLast('/')) =
        File(dir, name).apply { writeBytes(SampleMedia.file(fixture).readBytes()) }

    @Test
    fun `each kind says what it is and the edited still carries the dot`() = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-carousel").toFile()
        try {
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(CropTool), SettingsRepositoryImpl()))
            )
            val edited = copy(dir, STILL_JPG, "edited.jpg")
            val rect = CropRect(left = 0, top = 0, width = 320, height = 240)
            val crop = EditDocument(ops = listOf(ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect))))
            assertTrue(EditRecipe.write(edited, crop).isSuccess)
            val film = copy(dir, CHAPTERS)
            val items = listOf(
                MediaItem.Image(copy(dir, STILL_JPG)),
                MediaItem.Image(edited),
                MediaItem.Image(copy(dir, ANIM_GIF)),
                MediaItem.Image(copy(dir, BURST_HEIC)),
                MediaItem.Video(film),
                MediaItem.Audio(copy(dir, "audio/tone.aac"))
            )
            onUi {
                setContent { MediaViewerCarousel(items, items[0].file.absolutePath, 0, {}, Blank) }
                // Four kinds speak — the GIF, the burst, the film, the track; the two stills do not.
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("carousel-chip").fetchSemanticsNodes().size == 4 }
                onNodeWithText("GIF").assertExists()
                onNodeWithText("$BURST_FRAME_COUNT").assertExists()
                onNodeWithText(formatTime(containerLengthMs(film))).assertExists()
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("frame-edited").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(1, onAllNodesWithTag("frame-edited").fetchSemanticsNodes().size)
                onNodeWithTag("viewer-carousel").assertExists()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

/** Nothing is decoded for the tiles: the badges are the subject. */
private object Blank : ViewerThumbnails {
    override suspend fun full(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun preview(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun sourceSize(item: MediaItem) = IntSize.Zero
    override suspend fun stillColor(item: MediaItem) = StillColorInfo.Sdr
    override fun cachedFull(item: MediaItem, frameIndex: Int) = null
}
