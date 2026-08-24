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

    /**
     * A control that belongs **inside** the dock panel rather than beside it.
     * Home's "Link folder" button is the first tenant: as its own round island it
     * was a second floating object at the same level as the dock, and the eye had
     * to process both.
     */
    private var inlineKey by mutableStateOf<String?>(null)
    var inlineAccessory by mutableStateOf<(@Composable () -> Unit)?>(null)
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

    fun setInline(key: String, content: @Composable () -> Unit) {
        inlineKey = key
        inlineAccessory = content
    }

    fun clearInline(key: String) {
        if (inlineKey == key) {
            inlineKey = null
            inlineAccessory = null
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
