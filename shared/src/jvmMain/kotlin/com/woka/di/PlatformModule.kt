package com.woka.di

import com.woka.data.SettingsRepository
import com.woka.data.SettingsRepositoryImpl
import org.koin.dsl.module

actual val platformModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl() }
}
