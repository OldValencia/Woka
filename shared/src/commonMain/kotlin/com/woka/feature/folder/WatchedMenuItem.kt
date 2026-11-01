package com.woka.feature.folder

import androidx.compose.runtime.Composable
import com.woka.core.data.WatchRecord
import com.woka.core.ui.components.DropdownItem

/**
 * Asserting watched state by hand, for a video finished somewhere else or one the
 * viewer has no intention of opening.
 *
 * It writes exactly the record a full playthrough writes — one state reached two
 * ways — so the card cannot tell, and has no reason to. The entry inverts once a
 * record exists: being able to claim a state without being able to withdraw it
 * makes a mis-click permanent.
 */
@Composable
internal fun WatchedMenuItem(
    target: String,
    watch: WatchRecord?,
    viewModel: FolderViewModel,
    dismiss: () -> Unit
) {
    if (watch?.finished == true) {
        DropdownItem("Mark as not watched") { dismiss(); viewModel.markUnwatched(target) }
    } else {
        DropdownItem("Mark as watched") { dismiss(); viewModel.markWatched(target) }
    }
}
