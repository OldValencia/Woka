package com.woka.app.shell.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.core.data.WokaSettings
import com.woka.core.ui.components.AccessoryPill
import com.woka.core.ui.components.floatingPanel
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** An unlink that can still be taken back: the root, and the record before it. */
class PendingUnlink(val path: String, val before: WokaSettings)

/**
 * The last unlink, for as long as it can be undone (SIDE-010).
 *
 * Unlinking sweeps the watch history and remembered tracks for the whole tree, at
 * once and from a menu. An undo rather than a confirm dialog: a dialog taxes every
 * unlink to guard the rare mistake (`home-rethink`, 2026-09-20), and an undo costs
 * nothing when it was meant.
 */
object UnlinkUndo {
    var pending by mutableStateOf<PendingUnlink?>(null)
        private set

    fun offer(path: String, before: WokaSettings) {
        pending = PendingUnlink(path, before)
    }

    fun undo() {
        val last = pending ?: return
        pending = null
        AppDependencies.settingsRepository.restoreUnlinkedFolder(last.before, last.path)
    }

    fun dismiss(of: PendingUnlink) {
        if (pending === of) pending = null
    }
}

/** "Unlinked Movies · Undo", for six seconds, among the status banners. */
@Composable
fun UnlinkUndoBanner() {
    val last = UnlinkUndo.pending ?: return
    LaunchedEffect(last) {
        delay(6.seconds)
        UnlinkUndo.dismiss(last)
    }
    Row(
        modifier = Modifier.floatingPanel(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BasicText(
            "Unlinked “${File(last.path).name.ifEmpty { last.path }}”",
            modifier = Modifier.padding(horizontal = 12.dp),
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 13.sp)
        )
        AccessoryPill("Undo", isActive = true, isSmall = true, onClick = { UnlinkUndo.undo() })
    }
}
