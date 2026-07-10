package io.iris.di

import io.iris.data.SettingsRepository
import io.iris.data.SettingsRepositoryImpl
import org.koin.dsl.module

actual val platformModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl() }
}
