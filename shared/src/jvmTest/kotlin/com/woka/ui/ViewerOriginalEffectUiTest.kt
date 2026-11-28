package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerOriginalEffect
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.SampleMedia
import com.woka.support.onUi
import com.woka.support.withTempStorageRoot
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether the viewer offers `Original` follows the file (RD-502): a still carrying a recipe
 * offers it and a plain one does not, and paging to another file ends a comparison.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerOriginalEffectUiTest {

    private fun onPhotos(block: (edited: MediaItem, plain: MediaItem) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-original-effect").toFile()
        try {
            val bytes = SampleMedia.file(STILL_JPG).readBytes()
            val edited = File(dir, "edited.jpg").apply { writeBytes(bytes) }
            val plain = File(dir, "plain.jpg").apply { writeBytes(bytes) }
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(CropTool), SettingsRepositoryImpl()))
            )
            val rect = CropRect(left = 0, top = 0, width = 320, height = 240)
            val crop = EditDocument(ops = listOf(ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect))))
            assertTrue(EditRecipe.write(edited, crop).isSuccess)
            block(MediaItem.Image(edited), MediaItem.Image(plain))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an edited still offers Original and paging to a plain one ends it`() = onPhotos { edited, plain ->
        onUi {
            val state = MediaViewerState(edited.file.absolutePath)
            var active by mutableStateOf(edited)
            setContent { ViewerOriginalEffect(state, active) }
            waitUntil(timeoutMillis = 5_000) { state.canShowOriginal }

            state.original.toggle()
            active = plain
            waitForIdle()
            assertFalse(state.original.active, "the comparison outlived its file")
            waitUntil(timeoutMillis = 5_000) { !state.canShowOriginal }
        }
    }

    /** Edited to edited: what is offered stays, but the comparison was the other file's. */
    @Test
    fun `paging between edited files still ends the comparison`() = onPhotos { edited, _ ->
        onUi {
            val twin = File(edited.file.parentFile, "twin.jpg").apply { writeBytes(edited.file.readBytes()) }
            val state = MediaViewerState(edited.file.absolutePath)
            var active by mutableStateOf(edited)
            setContent { ViewerOriginalEffect(state, active) }
            waitUntil(timeoutMillis = 5_000) { state.canShowOriginal }

            state.original.toggle()
            active = MediaItem.Image(twin)
            waitForIdle()
            assertFalse(state.original.active, "the comparison was carried to the next file")
        }
    }
}
