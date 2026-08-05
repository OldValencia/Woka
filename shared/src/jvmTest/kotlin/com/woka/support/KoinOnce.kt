package com.woka.support

import com.woka.app.di.appModule
import com.woka.app.di.platformModule
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Starts Koin once for the whole test JVM, and never stops it.
 *
 * `AppDependencies` is an object whose dependencies are `by inject()` delegates,
 * so the first resolution is cached for the life of the process — the decode loop
 * reads `AppDependencies.settingsRepository` for playback resume (§4). Restarting
 * Koin between tests would leave those delegates pointing at a dead container, so
 * the container is shared and the settings it writes land in the test storage root
 * the Gradle `Test` task sets.
 */
@Synchronized
fun startKoinOnce() {
    if (GlobalContext.getOrNull() != null) return
    startKoin { modules(appModule, platformModule) }
}
