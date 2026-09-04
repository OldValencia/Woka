package com.woka.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test

/** What the engine puts in the slot, tagged so the panel cannot be credited with it. */
private const val BAND = "fixture-band"

/**
 * The shell makes room for a band and asks the **engine** to fill it (ME-206).
 *
 * Asked against `FixtureEngine` rather than against a video, exactly as
 * `EditorCanvasPlaybackUiTest` asks about the canvas click: what is being proved is that
 * the panel places whatever its engine returns and draws nothing when the engine returns
 * nothing — not what a video does about it. A test that opened a decoder here would prove
 * the same thing more slowly and would fail for reasons that are not this.
 *
 * **The absent case is the load-bearing one.** `Timeline` is defaulted to `Unit` so that
 * no photograph's layout moves by a pixel, and a default nobody asserts is a default that
 * quietly stops holding.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorBandUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The pill inside the editor's top bar resolves its settings from Koin. */
    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `an engine that fills the slot has its band drawn`() = onEditor(emptyList()) { harness ->
        val engine = FixtureEngine(
            timeline = { Box(Modifier.size(24.dp).testTag(BAND)) }
        )
        onUi {
            setContent { EditorUnderShell(harness, TopBarAccessoriesState(), engine = engine) }
            waitForIdle()

            onNodeWithTag(BAND).assertIsDisplayed()
        }
    }

    @Test
    fun `an engine that does not draws none`() = onEditor(emptyList()) { harness ->
        onUi {
            setContent {
                EditorUnderShell(harness, TopBarAccessoriesState(), engine = FixtureEngine())
            }
            waitForIdle()

            onNodeWithTag(BAND).assertDoesNotExist()
        }
    }
}
