package io.iris.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TopBarAccessoriesState {
    var centerAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var rightAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
    var backActionOverride by mutableStateOf<(() -> Unit)?>(null)
}

val LocalTopBarAccessories = compositionLocalOf { TopBarAccessoriesState() }
