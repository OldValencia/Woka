plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.filekit.compose)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.javacv)
                implementation(libs.ffmpeg.platform)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // `jvmTest` used to inherit from `commonTest` alone, which is enough for
        // pure logic but not for `runTest`, for ViewModels bound to
        // `Dispatchers.Main`, or for Compose UI tests.
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutinesTest)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.compose.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}