import org.gradle.api.provider.Property
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

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
        // few dozen bytes. Default sizing gave an 8GB heap and an adaptive young
        // generation, and with frames freed only by the collector seven gigabytes waited
        // on it; `-Xmx` and `-XX:MaxNewSize` capped that. Frames are now closed by
        // `FrameRetention` and copied without a Java staging array (ST-001, 2026-11-19),
        // so the caps remain as a ceiling, not as what paces the frames.
        //
        // Idle memory (ST-001, 2026-11-19): live heap is ~15MB, so without a periodic
        // cycle nothing collects and both an oversized heap and the native pixels behind
        // dead Skia wrappers stay committed — a folder of 4K posters idled at 735MB
        // private / 482MB working set, 411/218 with these. -Xms16m drops the ~500MB the
        // default initial heap commits at startup; four parallel and one concurrent GC
        // thread shrink G1's own tables. Safe for playback only because the decoder no
        // longer runs ahead of the draw (`FrameRetention.isBacklogged`): before that, a
        // small young generation promoted frame wrappers and 4K reached 20GB.
        jvmArgs += listOf(
            "-Xmx1536m",
            "-Xms16m",
            "-XX:MaxNewSize=256m",
            "-XX:G1PeriodicGCInterval=5000",
            "-XX:ParallelGCThreads=4",
            "-XX:ConcGCThreads=1",
            // Compose draws through skiko's own D3D12 device; Java2D's D3D9 one only cost
            // ~110 ms of AWT start-up and ~25 MB. `D3d12Prewarm` loads the driver instead.
            "-Dsun.java2d.d3d=false",
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
            // Bundled natives (libjxl, jxrlib), one folder per `<os>-<arch>`: `windows-x64` is
            // committed, the others built by `natives/*/build.sh` in CI; `BundledNatives` loads them.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))

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
                Triple("image/x-exr", "exr", "OpenEXR Image"),
                Triple("image/vnd-ms.dds", "dds", "DirectDraw Surface"),
                Triple("image/x-xbitmap", "xbm", "X BitMap Image"),
                Triple("image/x-xpixmap", "xpm", "X PixMap Image"),
                Triple("image/x-portable-anymap", "pnm", "Portable Anymap Image"),
                Triple("image/vnd.adobe.photoshop", "psb", "Photoshop Large Document"),
                Triple("image/vnd.microsoft.icon", "ico", "Icon"),
                Triple("image/vnd.microsoft.icon", "cur", "Cursor"),
                Triple("image/x-paint", "paint", "Paint Project"),
                Triple("image/vnd.radiance", "hdr", "Radiance HDR Image"),
                Triple("application/illustrator", "ai", "Adobe Illustrator Artwork"),
                Triple("image/jxl", "jxl", "JPEG XL Image"),
                Triple("image/jxr", "jxr", "JPEG XR Image"),
                Triple("image/vnd.ms-photo", "wdp", "HD Photo"),
                Triple("image/x-adobe-dng", "dng", "Digital Negative"),
                Triple("image/x-canon-cr2", "cr2", "Canon RAW Image"),
                Triple("image/x-canon-cr3", "cr3", "Canon RAW Image"),
                Triple("image/x-canon-crw", "crw", "Canon RAW Image"),
                Triple("image/x-nikon-nef", "nef", "Nikon RAW Image"),
                Triple("image/x-nikon-nrw", "nrw", "Nikon RAW Image"),
                Triple("image/x-sony-arw", "arw", "Sony RAW Image"),
                Triple("image/x-sony-sr2", "sr2", "Sony RAW Image"),
                Triple("image/x-fuji-raf", "raf", "Fujifilm RAW Image"),
                Triple("image/x-olympus-orf", "orf", "Olympus RAW Image"),
                Triple("image/x-olympus-orf", "ori", "Olympus RAW Image"),
                Triple("image/x-panasonic-rw2", "rw2", "Panasonic RAW Image"),
                Triple("image/x-pentax-pef", "pef", "Pentax RAW Image"),
                Triple("image/x-pentax-pef", "ptx", "Pentax RAW Image"),
                Triple("image/x-hasselblad-3fr", "3fr", "Hasselblad RAW Image"),
                Triple("image/x-hasselblad-fff", "fff", "Hasselblad RAW Image"),
                Triple("image/x-phaseone-iiq", "iiq", "Phase One RAW Image"),
                Triple("image/x-phaseone-cap", "cap", "Phase One RAW Image"),
                Triple("image/x-leica-rwl", "rwl", "Leica RAW Image"),
                Triple("image/x-casio-bay", "bay", "Casio RAW Image"),
                Triple("image/x-kodak-dcr", "dcr", "Kodak RAW Image"),
                Triple("image/x-kodak-dcs", "dcs", "Kodak RAW Image"),
                Triple("image/x-kodak-drf", "drf", "Kodak RAW Image"),
                Triple("image/x-kodak-k25", "k25", "Kodak RAW Image"),
                Triple("image/x-kodak-kdc", "kdc", "Kodak RAW Image"),
                Triple("image/x-epson-erf", "erf", "Epson RAW Image"),
                Triple("image/x-mamiya-mef", "mef", "Mamiya RAW Image"),
                Triple("image/x-leaf-mos", "mos", "Leaf RAW Image"),
                Triple("image/x-minolta-mrw", "mrw", "Minolta RAW Image"),
                Triple("image/x-minolta-mdc", "mdc", "Minolta RAW Image"),
                Triple("image/x-logitech-pxn", "pxn", "Logitech RAW Image"),
                Triple("image/x-panasonic-raw", "raw", "Camera RAW Image"),
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
                Triple("video/x-dv", "dv", "Digital Video"),
                Triple("video/ogg", "ogv", "Ogg Video"),
                Triple("video/ogg", "ogm", "Ogg Media Video"),
                Triple("application/ogg", "ogx", "Ogg Multimedia"),
                Triple("application/vnd.rn-realmedia", "rm", "RealMedia Video"),
                Triple("application/vnd.rn-realmedia-vbr", "rmvb", "RealMedia Variable Bitrate Video"),
                Triple("video/x-nsv", "nsv", "Nullsoft Streaming Video"),
                Triple("video/x-bink", "bik", "Bink Video"),
                Triple("video/x-thp", "thp", "Nintendo THP Video"),
                Triple("video/x-rpl", "rpl", "ARMovie Video"),
                Triple("video/x-amv", "amv", "AMV Video"),
                Triple("video/x-mtv", "mtv", "MTV Video"),
                Triple("video/x-gxf", "gxf", "General eXchange Format Video"),
                Triple("video/x-xesc", "xesc", "Expression Encoder Screen Capture"),
                Triple("video/x-dav", "dav", "Dahua DVR Video"),
                Triple("audio/mpeg", "mp3", "MP3 Audio"),
                Triple("audio/mpeg", "mpga", "MPEG Audio"),
                Triple("audio/mpeg", "mp1", "MPEG-1 Layer I Audio"),
                Triple("audio/wav", "wav", "WAV Audio"),
                Triple("audio/wav", "wave", "WAV Audio"),
                Triple("audio/flac", "flac", "FLAC Audio"),
                Triple("audio/aac", "aac", "AAC Audio"),
                Triple("audio/x-ms-wma", "wma", "Windows Media Audio"),
                Triple("audio/mp4", "m4a", "MPEG-4 Audio"),
                Triple("audio/mp4", "m4b", "MPEG-4 Audiobook"),
                Triple("audio/mp4", "m4r", "iPhone Ringtone"),
                Triple("audio/aiff", "aiff", "AIFF Audio"),
                Triple("audio/aiff", "aif", "AIFF Audio"),
                Triple("audio/x-tta", "tta", "True Audio"),
                Triple("audio/x-w64", "w64", "Sony Wave64 Audio"),
                Triple("audio/x-caf", "caf", "Core Audio Format"),
                Triple("audio/ac3", "ac3", "Dolby Digital Audio"),
                Triple("audio/ac3", "a52", "Dolby Digital Audio"),
                Triple("audio/eac3", "eac3", "Dolby Digital Plus Audio"),
                Triple("audio/vnd.dts", "dts", "DTS Audio"),
                Triple("audio/vnd.dolby.mlp", "mlp", "Dolby TrueHD Audio"),
                Triple("audio/x-aob", "aob", "DVD-Audio Object"),
                Triple("audio/opus", "opus", "Opus Audio"),
                Triple("audio/ogg", "ogg", "Ogg Audio"),
                Triple("audio/ogg", "oga", "Ogg Audio"),
                Triple("audio/ogg", "spx", "Speex Audio"),
                Triple("audio/amr", "amr", "AMR Audio"),
                Triple("audio/3gpp", "3ga", "3GPP Audio"),
                Triple("audio/x-voc", "voc", "Creative Voice Audio"),
                Triple("audio/x-twinvq", "vqf", "TwinVQ Audio"),
                Triple("audio/x-oma", "oma", "Sony ATRAC Audio")
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

// The jlink archive below covers only JDK classes; Compose, Skiko, Kotlin and Woka
// itself were still parsed and verified from the jars on every launch. The launcher
// also maps a dynamic archive of those, `app/woka.jsa`, which the JVM records during
// the first launch after an install or update and rewrites whenever the JDK or jar
// set changes. That one launch takes ~7.6s to its first frame; every later one went
// from ~1000ms to ~615ms warm, ~1300–2400ms to ~750ms cold. The archive cannot ship
// in the MSI: the JVM checks each jar's modification time, and the installer's
// copies carry their own. Windows only: on macOS the archive would be written into
// the `.app` bundle, on Linux into a read-only `/opt`. Only the packaged launcher
// gets the flags — `run` would receive `$APPDIR` unexpanded. The compose plugin sets
// `launcherJvmArgs` in its own afterEvaluate, so this one runs after it.
//
// The MSI ships something better (ST-001, 2026-11-19): a JEP 483 AOT cache, recorded
// once here by `trainAotCache` and copied into the image `packageMsi` installs. Unlike
// the dynamic archive it survives the installer — JDK 25 does not check jar mtimes on
// it, and a moved image is matched by its common path prefix (843 of 843 Woka classes
// mapped from a relocated copy). JPEG first draw 946 → 844 ms against AppCDS, and the
// ~8s training launch after every install or update is gone. A missing or broken
// `-XX:AOTCache` starts with no archive at all, so only the image that carries the
// file gets the flag; `createDistributable` keeps the self-made AppCDS archive.
if (System.getProperty("os.name").startsWith("Windows")) {
    // The GraalVM native image beside the JVM build (ST-004); nothing here depends on it.
    apply(from = "native-image.gradle.kts")
    afterEvaluate {
        tasks.withType<AbstractJPackageTask>().configureEach {
            launcherJvmArgs.addAll(AotTrainingRun.APP_CDS_FLAGS + JAVACPP_FROM_FOLDER)
        }
        // FFmpeg's, LibRaw's and JavaCPP's own DLLs, unpacked into `resources/javacpp`. Left
        // in their jars, JavaCPP searched every jar on the classpath for each of ~60 runtime
        // DLLs it preloads before avutil: ~350 ms on the first FFmpeg use. Loaded from the
        // folder with `findLibraries=false`, 460 → 137 ms to a ready grabber (ST-001,
        // 2026-11-20, 4 pairs of runs; a 4K frame decoded the same). 68 DLLs, no name clashes.
        val nativeJars = configurations.runtimeClasspath.get().files.filter { it.name.endsWith("-windows-x86_64.jar") }
        tasks.named<Sync>("prepareAppResources") {
            nativeJars.forEach { jar ->
                from(zipTree(jar)) {
                    include("**/*.dll")
                    eachFile { path = "javacpp/$name" }
                    includeEmptyDirs = false
                }
            }
        }
        val distributable = tasks.named<AbstractJPackageTask>("createDistributable")
        val image = distributable.flatMap { it.destinationDir.dir("Woka") }
        val trainedCache = layout.buildDirectory.file("aot-training/woka.aot")
        val aotImage = layout.buildDirectory.dir("compose/binaries/main/aot-image")
        val trainAotCache = tasks.register("trainAotCache") {
            description = "Records the AOT cache the installed launcher maps, by opening a JPEG once."
            inputs.dir(image)
            outputs.file(trainedCache)
            doLast { AotTrainingRun.train(image.get().asFile, trainedCache.get().asFile) }
        }
        val aotAppImage = tasks.register<Sync>("aotAppImage") {
            description = "The distributable with the trained AOT cache in place of AppCDS."
            from(distributable.flatMap { it.destinationDir })
            into(aotImage)
            from(trainAotCache) { into("Woka/app") }
            filesMatching("Woka/app/Woka.cfg") {
                filter { line -> AotTrainingRun.cfgLine(line) }
            }
        }
        // `-Pwoka.native=true` installs the GraalVM native image instead (ST-005): jpackage
        // takes its folder as an app image, so the MSI keeps this product's upgrade UUID,
        // shortcuts and file associations — and replaces an installed JVM Woka, or back.
        val nativeMsi = providers.gradleProperty("woka.native").map { it.toBoolean() }.getOrElse(false)
        tasks.named<AbstractJPackageTask>("packageMsi") {
            if (nativeMsi) {
                dependsOn("nativeAppImage")
                appImage.set(layout.buildDirectory.dir("compose/binaries/main/native-image/Woka"))
            } else {
                dependsOn(aotAppImage)
                appImage.set(aotImage.map { it.dir("Woka") })
            }
        }
    }
}

/** JavaCPP loads its libraries from `resources/javacpp` only — see `prepareAppResources`. */
val JAVACPP_FROM_FOLDER = listOf(
    "-Djava.library.path=\$APPDIR/resources/javacpp",
    "-Dorg.bytedeco.javacpp.pathsFirst=true",
    "-Dorg.bytedeco.javacpp.findLibraries=false"
)

/**
 * The training launch behind `trainAotCache`: the built image's own java and options,
 * an isolated storage root, a generated JPEG, and `woka.aotTraining.exitMs` so the app
 * exits normally once the picture is drawn — the JVM writes the cache only then.
 */
object AotTrainingRun {
    val APP_CDS_FLAGS = listOf("-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile=\$APPDIR/woka.jsa")

    /** Archive options the AOT cache replaces; the JVM refuses `AOTCacheOutput` beside them. */
    private val replaced = APP_CDS_FLAGS + "-Xshare:auto"

    /** `Woka.cfg` of the installed image: the AOT cache in place of the AppCDS options. */
    fun cfgLine(line: String): String? = when {
        line == "java-options=${APP_CDS_FLAGS[0]}" -> "java-options=-XX:AOTCache=\$APPDIR/woka.aot"
        line.startsWith("java-options=") && line.substringAfter('=') in replaced -> null
        else -> line
    }

    fun train(image: File, cache: File) {
        val app = File(image, "app")
        val cfg = File(app, "Woka.cfg").readLines()
        fun values(key: String) = cfg.filter { it.startsWith("$key=") }
            .map { it.substringAfter('=') }
            .filterNot { it in replaced }
            .map { it.replace("\$APPDIR", app.path) }
        val work = File(cache.parentFile, "run").apply { deleteRecursively(); mkdirs() }
        val storage = File(work, "storage").apply { mkdirs() }
        // Present, so the first-run folder menu registration skips: no registry writes.
        File(storage, ".context_menu_initialized").writeText("")
        val picture = File(work, "training.jpg")
        ImageIO.write(BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB), "jpg", picture)
        cache.delete()
        val command = listOf(File(image, "runtime/bin/java.exe").path) + values("java-options") + listOf(
            "-XX:AOTCacheOutput=${cache.path}",
            "-Dwoka.storageRoot=${storage.path}",
            "-Dwoka.aotTraining.exitMs=1500",
            "-cp", values("app.classpath").joinToString(File.pathSeparator),
            values("app.mainclass").single(),
            picture.path
        )
        val log = File(work, "training.log")
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
        if (!process.waitFor(3, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw GradleException("AOT training did not exit in 3 minutes; see $log")
        }
        if (process.exitValue() != 0 || !cache.isFile) {
            throw GradleException("AOT training wrote no cache (exit ${process.exitValue()}); see $log")
        }
    }
}

tasks.withType<AbstractJLinkTask>().configureEach {
    val method = javaClass.methods.firstOrNull { it.name.startsWith("getStripNativeCommands") }
    @Suppress("UNCHECKED_CAST")
    (method?.invoke(this) as? Property<Boolean>)?.set(false)
    freeArgs.add("--generate-cds-archive")
}