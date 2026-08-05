package com.woka.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class DockAccessoriesState {
    var isVisible by mutableStateOf(true)

    private var leftKey by mutableStateOf<String?>(null)
    var leftAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    private var topKey by mutableStateOf<String?>(null)
    var topAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun setLeft(key: String, content: @Composable () -> Unit) {
        leftKey = key
        leftAccessory = content
    }

    fun clearLeft(key: String) {
        if (leftKey == key) {
            leftKey = null
            leftAccessory = null
        }
    }

    fun setTop(key: String, content: @Composable () -> Unit) {
        topKey = key
        topAccessory = content
    }

    fun clearTop(key: String) {
        if (topKey == key) {
            topKey = null
            topAccessory = null
        }
    }
}

val LocalDockAccessories = compositionLocalOf { DockAccessoriesState() }
