package com.woka.app.di

import com.woka.feature.folder.FolderScrollMemory
import com.woka.feature.folder.FolderViewModel
import com.woka.app.navigation.Router
import com.woka.app.navigation.startupStack
import com.woka.core.data.LocalMediaRepository
import com.woka.core.data.SettingsRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.module.Module

/**
 * [launchArgs] are the process arguments; the route they resolve to is applied
 * here rather than in `main` because `desktopApp` does not carry Koin on its
 * compile classpath and cannot name the container itself. Settings are read here
 * too, for the place the last session left off (SIDE-002).
 */
fun initKoin(launchArgs: List<String> = emptyList()) {
    val koin = startKoin {
        modules(appModule, platformModule)
    }.koin
    val settings = koin.get<SettingsRepository>().settings.value
    koin.get<Router>().start(startupStack(launchArgs, settings.lastSession, settings.linkedFolders))
}

val appModule = module {
    single { Router() }
    single { LocalMediaRepository() }
    single { FolderScrollMemory() }
    factory { (path: String) -> FolderViewModel(path, get(), get()) }
}

expect val platformModule: Module
