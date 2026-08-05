package com.woka.app.di

import com.woka.core.data.SettingsRepository
import com.woka.core.data.SettingsRepositoryImpl
import org.koin.dsl.module

actual val platformModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl() }
}
