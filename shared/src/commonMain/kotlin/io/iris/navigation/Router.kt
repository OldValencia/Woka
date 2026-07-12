package io.iris.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class Screen {
    data object Home : Screen()
    data class Folder(val path: String, val highlightFile: String? = null) : Screen()
}

class Router {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    fun navigateTo(screen: Screen) {
        _stack.update { it + screen }
    }

    fun replaceTop(screen: Screen) {
        _stack.update { if (it.isNotEmpty()) it.dropLast(1) + screen else listOf(screen) }
    }

    fun pop() {
        _stack.update { if (it.size > 1) it.dropLast(1) else it }
    }

    fun popTo(screen: Screen) {
        _stack.update { current ->
            val index = current.indexOf(screen)
            if (index != -1) current.take(index + 1) else current
        }
    }
}
