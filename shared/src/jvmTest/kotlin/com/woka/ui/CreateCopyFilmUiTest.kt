package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.EditorRenderStatus
import com.woka.support.COPY_EXPORT
import com.woka.support.ExportingEngine
import com.woka.support.FRAME_EXPORT
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.WaysOutHost
import com.woka.support.copying
import com.woka.support.createCopy
import com.woka.support.sizeFields
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Create a finished copy` for a film (RD-401): presets from the source's own size, even custom
 * sides, `Audio only…` closed — and `Save frame as photo`'s moment.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CreateCopyFilmUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = EditorRenderStatus.clear()

    @Test
    fun `a 720p film offers 480p, and Audio only is drawn closed`() =
        copying(FixtureEngine(sourceSize = IntSize(1280, 720), kind = MediaKind.Video)) { engine ->
            onNodeWithText("720p").assertDoesNotExist()
            onNodeWithTag("editor-copy-audio").assertIsNotEnabled()
            onNodeWithText("480p").performClick()
            sizeFields("854", "480")
            createCopy()
            assertEquals(IntSize(854, 480), engine.exported.single().size)
        }

    @Test
    fun `a film's custom size links to even sides, or stands free`() =
        copying(FixtureEngine(sourceSize = IntSize(1280, 720), kind = MediaKind.Video)) { engine ->
            onNodeWithTag("output-width").performTextReplacement("1000")
            sizeFields("1000", "562")
            onNodeWithTag("output-keep-proportions").performClick()
            onNodeWithTag("output-height").performTextReplacement("400")
            sizeFields("1000", "400")
            createCopy()
            assertEquals(IntSize(1000, 400), engine.exported.single().size)
        }

    @Test
    fun `a 1080p film offers 720p and 480p`() =
        copying(FixtureEngine(sourceSize = IntSize(1920, 1080), kind = MediaKind.Video)) { _ ->
            onNodeWithText("720p").assertIsDisplayed()
            onNodeWithText("480p").assertIsDisplayed()
        }

    @Test
    fun `a film's frame is saved at the moment the dialog was opened`() = onEditor(emptyList()) { harness ->
        val engine = ExportingEngine(FixtureEngine(kind = MediaKind.Video), listOf(COPY_EXPORT, FRAME_EXPORT))
        onUi {
            setContent { WaysOutHost(harness, engine) }
            onNodeWithTag("host-frame").performClick()
            waitForIdle()
            onNodeWithText("Current frame · PNG").assertIsDisplayed()
            onNodeWithTag("editor-frame-save").performClick()
            waitForIdle()
            assertTrue(engine.exported.single().frame)
            assertEquals(0L, engine.exported.single().atMs)
        }
    }
}
