package com.woka.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.woka.app.shell.CanvasOverrideState
import com.woka.app.shell.LocalCanvasOverride
import com.woka.app.shell.LocalTopBarAccessories
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.shell.EditorFrameControls
import com.woka.feature.editor.shell.EditorScreen

/**
 * The editor as the shell composes it: the accessory slots are **rendered**, not merely
 * written to, and the canvas override the editor publishes into is a real one.
 *
 * `EditorScreen` draws no top bar of its own — it installs an action group into
 * `TopBarAccessoriesState` and repoints the viewer's pill through `CanvasOverrideState`,
 * and `GlobalTopBar` is what draws either. A test that composed only the screen would
 * find neither, and would report that as the buttons being missing rather than as
 * itself not being the shell.
 */
@Composable
internal fun EditorUnderShell(
    harness: EditorHarness,
    accessories: TopBarAccessoriesState,
    override: CanvasOverrideState = CanvasOverrideState(),
    onCancel: () -> Unit = {},
    onDone: () -> Unit = {},
    canRevert: Boolean = false,
    onRevert: () -> Unit = {},
    /** Overridden by a test that needs the engine to answer for a particular file. */
    engine: EditorEngine = FixtureEngine(),
    /** Null is a file holding one picture, which is what most tests are (ME-156). */
    frameControls: EditorFrameControls? = null
) {
    CompositionLocalProvider(
        LocalTopBarAccessories provides accessories,
        LocalCanvasOverride provides override
    ) {
        Column {
            Row {
                // The leading slot too: Revert lives beside Back, and a test that
                // rendered only the other two would report it as missing (ME-107).
                accessories.leadingAccessory?.invoke()
                accessories.centerAccessory?.invoke()
                accessories.rightAccessory?.invoke()
            }
            EditorScreen(
                session = harness.session,
                engine = engine,
                registry = harness.registry,
                onCancel = onCancel,
                onDone = onDone,
                canRevert = canRevert,
                onRevert = onRevert,
                frameControls = frameControls
            )
        }
    }
}
