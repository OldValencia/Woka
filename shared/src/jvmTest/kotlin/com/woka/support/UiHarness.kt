package com.woka.support

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.woka.core.data.SettingsRepositoryImpl

/**
 * A Compose UI test over a real `SettingsRepositoryImpl` on a throwaway root.
 *
 * The runner is the **v2** one: it drives the composition on a
 * `StandardTestDispatcher`, so coroutines queue the way they do in production
 * rather than running eagerly. The v1 function of the same name is deprecated,
 * and every scenario should come through here so none of them is written against
 * eager execution the app never has.
 *
 * The receiver type still lives in the v1 package — there is no
 * `androidx.compose.ui.test.v2.ComposeUiTest`; only the runner moved.
 *
 * Note that most screens do **not** need a repository at all: `Theme.colors`
 * resolves with no provider wrapped around the composable, and production
 * components render bare in `setContent`. Reach for [onUi] when a scenario is
 * pure UI, and for this when it has to prove a write landed.
 */
@OptIn(ExperimentalTestApi::class)
fun onUiWithSettings(block: ComposeUiTest.(SettingsRepositoryImpl) -> Unit) = withTempStorageRoot {
    val repository = SettingsRepositoryImpl()
    runComposeUiTest { block(repository) }
}

/**
 * The same runner, for a scenario that touches no settings. Called as a lambda
 * rather than passed through: the v2 runner takes a `CoroutineContext` first.
 */
@OptIn(ExperimentalTestApi::class)
fun onUi(block: ComposeUiTest.() -> Unit) = runComposeUiTest { block() }
