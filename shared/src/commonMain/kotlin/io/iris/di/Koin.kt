package io.iris.di

import io.iris.ui.HomeViewModel
import io.iris.ui.FolderViewModel
import io.iris.navigation.Router
import io.iris.data.LocalMediaRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun initKoin() {
    startKoin {
        modules(appModule, platformModule)
    }
}

val appModule = module {
    single { Router() }
    single { LocalMediaRepository() }
    factory { HomeViewModel(get()) }
    factory { (path: String) -> FolderViewModel(path, get(), get()) }
}

expect val platformModule: org.koin.core.module.Module
