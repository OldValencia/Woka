import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.woka.MainKt"

        // A decoded video frame is 15MB of native memory behind a Java wrapper of a
        // few dozen bytes, so what paces the collector is the staging array in
        // `FramePacer.convert` — see the note there. Default sizing gave it an 8GB
        // heap (a quarter of this machine's RAM) and an adaptive young generation,
        // which meant a collection every twenty seconds and seven gigabytes of
        // frames waiting on it. Capping the young generation caps the frames:
        // 256MB of staging arrays is a collection roughly every second at 24fps.
        jvmArgs += listOf("-Xmx1536m", "-XX:MaxNewSize=256m")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.woka"
            packageVersion = "1.0.0"
        }
    }
}