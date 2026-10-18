import org.gradle.api.provider.Property
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)
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
        jvmArgs += listOf(
            "-Xmx1536m",
            "-XX:MaxNewSize=256m",
            "-XX:+TieredCompilation",
            "-XX:Tier4CompileThreshold=100000",
            "-XX:CICompilerCount=2",
            "-Xshare:auto",
            "--enable-native-access=ALL-UNNAMED"
        )

        val appVersion = providers.gradleProperty("app.version").getOrElse("1.0.0")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Woka"
            packageVersion = appVersion
            description = "Woka"
            copyright = "Copyright 2026 OldValencia"
            vendor = "OldValencia"
            licenseFile.set(project.file("../LICENSE"))

            modules("java.instrument", "java.management", "jdk.unsupported")

            val ico = project.file("src/main/resources/icons/woka.ico")
            val icns = project.file("src/main/resources/icons/Woka.icns")
            val png = project.file("src/main/resources/icons/woka.png")

            windows {
                menu = true
                menuGroup = "Woka"
                shortcut = true
                upgradeUuid = "d7e98a3e-43f1-4c17-91a5-8d5a2307ef01"
                iconFile.set(ico)
                dirChooser = true
                perUserInstall = true
            }

            macOS {
                bundleID = "com.woka.app"
                dockName = "Woka"
                packageBuildVersion = appVersion
                iconFile.set(icns)
            }

            linux {
                packageName = "woka"
                debMaintainer = "OldValencia <noreply@github.com>"
                shortcut = true
                menuGroup = "Graphics;AudioVideo;"
                iconFile.set(png)
            }

            val mediaAssociations = listOf(
                Triple("image/jpeg", "jpg", "JPEG Image"),
                Triple("image/jpeg", "jpeg", "JPEG Image"),
                Triple("image/jpeg", "jfif", "JPEG File Interchange Format"),
                Triple("image/jpeg", "jpe", "JPEG Image"),
                Triple("image/png", "png", "PNG Image"),
                Triple("image/webp", "webp", "WebP Image"),
                Triple("image/bmp", "bmp", "Bitmap Image"),
                Triple("image/bmp", "dib", "Device Independent Bitmap"),
                Triple("image/bmp", "rle", "Run-Length Encoded Bitmap"),
                Triple("image/gif", "gif", "GIF Image"),
                Triple("image/heic", "heic", "High Efficiency Image"),
                Triple("image/heif", "heif", "High Efficiency Image"),
                Triple("image/heif", "hif", "High Efficiency Image"),
                Triple("image/avif", "avif", "AVIF Image"),
                Triple("image/tiff", "tif", "TIFF Image"),
                Triple("image/tiff", "tiff", "TIFF Image"),
                Triple("image/svg+xml", "svg", "SVG Vector Image"),
                Triple("image/x-tga", "tga", "Targa Image"),
                Triple("image/qoi", "qoi", "Quite OK Image"),
                Triple("image/jp2", "jp2", "JPEG 2000 Image"),
                Triple("image/x-pcx", "pcx", "PCX Image"),
                Triple("image/x-portable-pixmap", "ppm", "PPM Image"),
                Triple("image/x-portable-graymap", "pgm", "Portable Graymap Image"),
                Triple("image/x-portable-bitmap", "pbm", "Portable Bitmap Image"),
                Triple("image/vnd.adobe.photoshop", "psd", "Photoshop Document"),
                Triple("image/vnd.adobe.photoshop", "psdt", "Photoshop Document Template"),
                Triple("image/x-photodeluxe", "pdd", "PhotoDeluxe Document"),
                Triple("video/mp4", "mp4", "MP4 Video"),
                Triple("video/x-matroska", "mkv", "Matroska Video"),
                Triple("video/x-matroska", "mk3d", "Matroska 3D Video"),
                Triple("video/x-msvideo", "avi", "AVI Video"),
                Triple("video/x-msvideo", "divx", "DivX Video"),
                Triple("video/x-msvideo", "gvi", "Google Video"),
                Triple("video/quicktime", "mov", "QuickTime Video"),
                Triple("video/webm", "webm", "WebM Video"),
                Triple("video/x-m4v", "m4v", "M4V Video"),
                Triple("video/3gpp", "3gp", "3GP Video"),
                Triple("video/3gpp2", "3g2", "3GPP2 Video"),
                Triple("video/mpeg", "mpg", "MPEG Video"),
                Triple("video/mpeg", "mpeg", "MPEG Video"),
                Triple("video/mpeg", "mpeg1", "MPEG-1 Video"),
                Triple("video/mpeg", "mpeg2", "MPEG-2 Video"),
                Triple("video/mp4", "mpeg4", "MPEG-4 Video"),
                Triple("video/mp4", "mpg4", "MPEG-4 Video"),
                Triple("video/mpeg", "mpv", "MPEG Video Stream"),
                Triple("video/mpeg", "mpv2", "MPEG-2 Video Stream"),
                Triple("video/mp2t", "m2ts", "MPEG-2 Transport Stream"),
                Triple("video/mp2t", "mts", "MPEG-2 Transport Stream"),
                Triple("video/mp2t", "ts", "MPEG-2 Transport Stream"),
                Triple("video/mp2t", "tp", "MPEG Transport Stream Video"),
                Triple("video/mp2t", "tod", "JVC Transport Stream Video"),
                Triple("video/mp2t", "rec", "Topfield PVR Video"),
                Triple("video/dvd", "vob", "DVD Video Object"),
                Triple("video/x-vro", "vro", "DVD Video Recording"),
                Triple("video/dvd", "evo", "Enhanced Video Object"),
                Triple("application/mxf", "mxf", "Material Exchange Format Video"),
                Triple("video/x-ms-wmv", "wmv", "Windows Media Video"),
                Triple("video/x-ms-asf", "asf", "Advanced Systems Format Video"),
                Triple("video/x-flv", "flv", "Flash Video"),
                Triple("video/x-f4v", "f4v", "Flash MP4 Video"),
                Triple("video/x-dirac", "drc", "Dirac Video"),
                Triple("video/x-dv", "dv", "Digital Video")
            )
            for ((mime, ext, desc) in mediaAssociations) {
                fileAssociation(
                    mimeType = mime,
                    extension = ext,
                    description = desc,
                    linuxIconFile = png,
                    windowsIconFile = ico,
                    macOSIconFile = null
                )
            }
        }
    }
}

tasks.withType<AbstractJLinkTask>().configureEach {
    val method = javaClass.methods.firstOrNull { it.name.startsWith("getStripNativeCommands") }
    @Suppress("UNCHECKED_CAST")
    (method?.invoke(this) as? Property<Boolean>)?.set(false)
    freeArgs.add("--generate-cds-archive")
}