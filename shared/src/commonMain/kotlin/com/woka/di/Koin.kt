package com.woka.di

import com.woka.ui.HomeViewModel
import com.woka.ui.FolderViewModel
import com.woka.navigation.Router
import com.woka.data.LocalMediaRepository
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
