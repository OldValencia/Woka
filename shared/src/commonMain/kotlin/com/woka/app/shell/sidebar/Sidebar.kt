package com.woka.app.shell.sidebar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.app.collectField
import com.woka.app.navigation.Screen
import com.woka.core.data.PinnedSession
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker
import com.woka.core.utils.directoryChanges
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.io.File

/**
 * The window's left column: the linked folders as a tree, the pinned folders under
 * them, and `Link Folder…` at the foot (SIDE-001, `features/implemented/sidebar/`).
 *
 * It reads the router and the settings itself, as `GlobalTopBar` does, rather than
 * taking them through `App`: navigation is the shell's business, and threading six
 * callbacks through `MainShell` would only have moved this file's wiring somewhere
 * further from where it is used.
 *
 * Spans the full height of the window, so its header sits in the title bar's row and
 * is wrapped in [topBarArea] to drag the window the same way.
 */
@OptIn(FlowPreview::class)
@Composable
fun Sidebar(
    currentPath: String?,
    topBarArea: @Composable (content: @Composable () -> Unit) -> Unit
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val router = remember { AppDependencies.router }
    val linked by settingsRepo.collectField { it.linkedFolders }
    val pinned by settingsRepo.collectField { it.pinnedSessions }
    val tree = remember { SidebarTreeState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<SidebarMenuRequest?>(null) }
    val current by rememberUpdatedState(currentPath)

    LaunchedEffect(currentPath, linked) { tree.reveal(currentPath, linked) }

    // The open folder's row and its parent's follow the disk (SIDE-007): a new
    // sub-folder changes this row's children, and its count sits on the parent's.
    LaunchedEffect(currentPath) {
        val open = currentPath ?: return@LaunchedEffect
        directoryChanges(open).debounce(300.milliseconds).collect {
            tree.relist(open)
            File(open).parentFile?.path?.let { tree.relist(it) }
        }
    }

    val actions = remember(router, settingsRepo, tree) {
        SidebarActions(
            open = { path -> if (!samePath(path, current)) router.navigateTo(Screen.Folder(path)) },
            toggle = { path -> scope.launch { tree.toggle(path) } },
            pin = { path -> settingsRepo.addPinnedSession(PinnedSession(path)) },
            unpin = { path -> settingsRepo.removePinnedSession(path) },
            unlink = { root ->
                // What Home's card did, plus the one thing Home never had to: the
                // folder on screen may be inside it, and then the window moves to
                // the next root that still exists, or to Welcome (SIDE-002).
                UnlinkUndo.offer(root, settingsRepo.settings.value)
                settingsRepo.removeLinkedFolder(root)
                settingsRepo.removePinnedSession(root)
                val open = current
                if (open != null && isWithin(root, open)) {
                    val next = settingsRepo.settings.value.linkedFolders
                        .firstOrNull { File(it).isDirectory }
                    router.start(listOf(next?.let { Screen.Folder(it) } ?: Screen.Welcome))
                }
            },
            menu = { request -> menu = request }
        )
    }

    // Hidden by width, not by removal (SIDE-003): the tree keeps its open rows and
    // its cache, and the column slides out to the left at its full width.
    val visible by settingsRepo.collectField { it.sidebarVisible }
    val width by animateDpAsState(
        if (visible) SidebarTokens.Width else 0.dp,
        tween(250, easing = FastOutSlowInEasing)
    )
    Box(modifier = Modifier.width(width).fillMaxHeight().clipToBounds()) {
        Row(
            modifier = Modifier
                .wrapContentWidth(Alignment.End, unbounded = true)
                .width(SidebarTokens.Width)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Theme.colors.surface)) {
                topBarArea { SidebarHeader(onHide = { settingsRepo.setSidebarVisible(false) }) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SidebarTokens.ListInset)
                ) {
                    SidebarFolders(linked, tree, current, menu, pinned, actions)
                    SidebarPinned(pinned, linked, current, menu, actions)
                }
                SidebarFoot(prominent = linked.isEmpty(), onLink = {
                    val path = openDirectoryPicker("Link folder")
                    if (path != null) {
                        settingsRepo.addLinkedFolder(path)
                        router.navigateTo(Screen.Folder(path))
                    }
                })
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Theme.colors.border))
        }
    }
}
