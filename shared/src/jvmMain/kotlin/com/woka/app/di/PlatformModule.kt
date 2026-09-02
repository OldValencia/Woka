package com.woka.app.di

import com.woka.core.data.HdrView
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
    // Installing here rather than in a module of its own, for the reason `EditorModule`
    // installs `AppliedStillEdits` beside what it wraps: the decode path reads the mode
    // from a holder because it resolves nothing from Koin, and the holder's one source is
    // this repository. A supplier, so nothing observes the flow and pushes (ME-119).
    single<SettingsRepository> {
        SettingsRepositoryImpl().also { repo ->
            HdrView.install { repo.settings.value.hdrViewMode }
        }
    }
}
