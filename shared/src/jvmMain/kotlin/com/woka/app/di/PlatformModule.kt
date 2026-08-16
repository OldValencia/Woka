package com.woka.app.di

import com.woka.core.data.SettingsRepository
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.di.editorModule
import org.koin.dsl.module

/**
 * `editorModule` is composed here rather than listed in `initKoin`, because
 * `initKoin` lives in `commonMain` and cannot name a JVM symbol.
 */
actual val platformModule = module {
    includes(editorModule)
    single<SettingsRepository> { SettingsRepositoryImpl() }
}
