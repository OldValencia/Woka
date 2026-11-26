package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp

/**
 * What the editor still stacks along the canvas's bottom edge: the two notices (RD-201). The
 * medium's own band left for the docked timeline under the canvas (RD-301, `EditorScreen`);
 * these stay over the picture, so they keep `ViewerChrome`'s scrims.
 *
 * **It still names no `MediaKind` and no raster type.**
 */
@Composable
internal fun BoxScope.EditorBottomPanel(
    session: EditorSession,
    engine: EditorEngine,
    registry: EditorToolRegistry,
    skipped: List<ToolOp>
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = EditorTokens.Band.bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.bottomInset)
    ) {
        // Bucket B, for the whole session: the cost of `Done` on a format with no
        // carrier is stated before the work, never after it (§4.5).
        when (engine.capability) {
            EditCapability.Destructive -> EditorNoHistoryNotice()
            EditCapability.RenderOnly -> EditorRenderOnlyNotice(session.media.file)
            else -> Unit
        }
        // Independent of the inspector: the warning has to be visible whichever tab is open.
        if (skipped.isNotEmpty()) {
            EditorSkippedNotice(skipped.size, skippedToolTitles(skipped, registry))
        }
    }
}
