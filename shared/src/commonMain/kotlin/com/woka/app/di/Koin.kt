package com.woka.app.di

import com.woka.feature.home.HomeViewModel
import com.woka.feature.folder.FolderViewModel
import com.woka.app.navigation.Router
import com.woka.core.data.LocalMediaRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.module.Module

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

expect val platformModule: Module
