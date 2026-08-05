package com.woka.support

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps a `SharingStarted.WhileSubscribed` flow hot while [block] runs.
 *
 * Both ViewModels publish their state that way, so with no collector `state.value`
 * never leaves its initial value and every assertion against it would pass or fail
 * for the wrong reason. The collector runs on `Dispatchers.Default` — a real
 * thread, because the work upstream of it (`getItems` on `Dispatchers.IO`, the
 * sort on `Dispatchers.Default`) is real too.
 */
fun <T> withCollector(flow: StateFlow<T>, block: () -> Unit) {
    val scope = CoroutineScope(Dispatchers.Default)
    scope.launch { flow.collect { } }
    try {
        block()
    } finally {
        scope.cancel()
    }
}
