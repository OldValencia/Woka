package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.engine.video.previewFold
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Resize on a film sizes the render only (reported 2026-11-02: at 25 % Crop could not
 * reach past the top-left quarter, because the canvas measured against the resized frame).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewFoldTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a quarter-size Resize leaves the shown frame at the source's size`() = withTempStorageRoot {
        val source = IntSize(720, 1280)
        val registry = EditorToolRegistry(listOf(ResizeTool), SettingsRepositoryImpl())
        val resized = EditDocument(ops = listOf(ToolOp(ResizeTool.manifest.id, ParamSet().with(ResizeTool.Scale, 25f))))
        assertEquals(source, previewFold(resized, registry, source).outputSize)
        assertNotEquals(source, fold(resized, registry, source).outputSize, "the render must still resize")
    }
}
