package com.woka.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.woka.feature.editor.shell.EditorHost
import com.woka.feature.editor.shell.EditorTokens

@Composable
actual fun NativeMediaEditor(
    target: EditorTarget?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    // Held past the target being cleared, so the slide out has a picture to carry.
    // Never cleared: once the animation has finished the whole subtree is gone anyway,
    // and clearing it mid-exit is what makes an editor blink out and then slide.
    var lastTarget by remember { mutableStateOf(target) }
    LaunchedEffect(target) { if (target != null) lastTarget = target }

    // `Editor` sits to the right of `Library` in the dock, so moving to it moves
    // rightward: the editor enters from the right edge and leaves back through it.
    // The direction is the app's spatial model, not decoration — going somewhere and
    // coming back should not look the same.
    AnimatedVisibility(
        visible = target != null,
        enter = slideInHorizontally(
            animationSpec = tween(EditorTokens.Motion.PANEL_MS, easing = EditorTokens.Motion.easing)
        ) { fullWidth -> fullWidth },
        exit = slideOutHorizontally(
            animationSpec = tween(EditorTokens.Motion.PANEL_MS, easing = EditorTokens.Motion.easing)
        ) { fullWidth -> fullWidth },
        modifier = Modifier.fillMaxSize()
    ) {
        lastTarget?.let { open ->
            EditorHost(target = open, onClose = onClose, onSaved = onSaved)
        }
    }
}
