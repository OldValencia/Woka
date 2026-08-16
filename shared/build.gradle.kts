plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Which Bytedeco natives to package. `ffmpeg-platform` would pull all seven
// platforms it knows — 168MB, of which Linux and Android are 97MB this app can
// neither target nor run on — so the classifier is named instead. jpackage only
// ever builds for the OS it runs on, which makes the host's set the only one a
// distribution can use; `-PjavacppPlatform=macosx-arm64` overrides it.
val javacppPlatform: String = (findProperty("javacppPlatform") as String?) ?: hostPlatform()

/**
 * `group:name:version:classifier` for a catalogue entry's native jar. The KMP
 * source-set blocks have no `variantOf`, so the coordinate is spelled out — the
 * version still comes from the catalogue, never from a literal here.
 */
fun nativeOf(library: Provider<MinimalExternalModuleDependency>): String {
    val dependency = library.get()
    val module = dependency.module
    return "${module.group}:${module.name}:${dependency.versionConstraint.requiredVersion}:$javacppPlatform"
}

fun hostPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    // Bytedeco spells the architectures its own way, and the JVM's names differ
    // per vendor: amd64 on HotSpot/Windows, x86_64 on macOS.
    val arch = when (val raw = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> error("unsupported architecture '$raw' — Woka targets x86_64 and arm64")
    }
    return when {
        os.startsWith("windows") -> "windows-$arch"
        os.startsWith("mac") -> "macosx-$arch"
        else -> error("unsupported OS '$os' — Woka targets Windows and macOS")
    }
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
        }
        jvmMain.dependencies {
            implementation(libs.javacv)
            implementation(nativeOf(libs.javacpp))
            implementation(nativeOf(libs.ffmpeg))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // `jvmTest` used to inherit from `commonTest` alone, which is enough for
        // pure logic but not for `runTest`, for ViewModels bound to
        // `Dispatchers.Main`, or for Compose UI tests.
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.compose.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

// A safe storage root for the whole test JVM, not just for the tests that ask.
// Every SettingsStore registers a shutdown hook that writes settings.json, and
// those hooks fire long after any per-test `withTempStorageRoot` has restored the
// property — without a default they would land in the real
// `Documents/OldValencia/Woka`. The literal must match `STORAGE_ROOT_PROPERTY`;
// Gradle cannot see the Kotlin const.
tasks.withType<Test>().configureEach {
    systemProperty(
        "woka.storageRoot",
        layout.buildDirectory.dir("test-storage").get().asFile.absolutePath
    )
}