package com.woka.app.di

import com.woka.feature.home.HomeViewModel
import com.woka.feature.folder.FolderViewModel
import com.woka.app.navigation.Router
import com.woka.app.navigation.startupStack
import com.woka.core.data.LocalMediaRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.module.Module

/**
 * [launchArgs] are the process arguments; the route they resolve to is applied
 * here rather than in `main` because `desktopApp` does not carry Koin on its
 * compile classpath and cannot name the container itself.
 */
fun initKoin(launchArgs: List<String> = emptyList()) {
    val koin = startKoin {
        modules(appModule, platformModule)
    }.koin
    koin.get<Router>().start(startupStack(launchArgs))
}

val appModule = module {
    single { Router() }
    single { LocalMediaRepository() }
    factory { HomeViewModel(get()) }
    factory { (path: String) -> FolderViewModel(path, get(), get()) }
}

expect val platformModule: Module
