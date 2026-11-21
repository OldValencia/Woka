// The native image (ST-004), Windows only: applied from build.gradle.kts. The JVM build, its
// distributable and its MSI do not depend on anything here.
//
// Every task action copies what it needs into locals inside `register { }`: an action that
// reads the script's own top-level vals holds the script, which the configuration cache
// cannot store.
import nativeimage.graalvmHome
import nativeimage.nativeImageArguments
import nativeimage.runNativeImage
import nativeimage.writeResourceScript
import nativeimage.writeRules

val graalHome = providers.gradleProperty("woka.graalvm.home")
    .orElse(providers.environmentVariable("GRAALVM_HOME"))
val strict = providers.gradleProperty("woka.native.strict").map { it.toBoolean() }.orElse(false)
val builderMemory = providers.gradleProperty("woka.native.builderMemory").orElse("16g")
val appDir = layout.buildDirectory.dir("compose/binaries/main/app/Woka/app")
val rulesDir = layout.buildDirectory.dir("native-image/rules")
val imageDir = layout.buildDirectory.dir("native-image/image")
val workDir = layout.buildDirectory.dir("native-image/work")
val metadataDir = layout.projectDirectory.dir("native-image")
val kotlinSources = rootProject.file("shared/src/jvmMain/kotlin")
val appVersion = providers.gradleProperty("app.version").orElse("1.0.0")
val windowsIcon = layout.projectDirectory.file("src/main/resources/icons/woka.ico")

// The tasks that refresh and audit the metadata this build reads (ST-006).
apply(from = "native-image-metadata.gradle.kts")

// Reachability metadata by rule, regenerated whenever the distributable or the sources move.
tasks.register("nativeImageRules") {
    description = "Derives the native image's reachability rules from Woka's dependencies."
    dependsOn("createDistributable")
    inputs.dir(appDir)
    inputs.dir(kotlinSources)
    inputs.property("graalHome", graalHome.orElse(""))
    outputs.dir(rulesDir)
    val home = graalHome.orNull
    val app = appDir
    val rules = rulesDir
    val sources = kotlinSources
    doLast {
        val counts = writeRules(graalvmHome(home), app.get().asFile, sources, rules.get().asFile)
        logger.lifecycle("native image rules: $counts")
    }
}

// The executable and the JDK DLLs it loads; `-Pwoka.native.strict=true` makes every missing
// registration reportable at run time (`-XX:MissingRegistrationReportingMode=Warn`).
tasks.register("nativeImage") {
    description = "Builds Woka as a GraalVM native image inside Visual Studio's environment."
    dependsOn("nativeImageRules")
    inputs.dir(appDir)
    inputs.dir(rulesDir)
    inputs.dir(metadataDir)
    inputs.property("strict", strict)
    inputs.property("builderMemory", builderMemory)
    inputs.property("version", appVersion)
    inputs.file(windowsIcon)
    outputs.dir(imageDir)
    val home = graalHome.orNull
    val app = appDir
    val configs = listOf(metadataDir.dir("app"), metadataDir.dir("tests"))
    val rules = rulesDir
    val out = imageDir
    val work = workDir
    val strictBuild = strict
    val version = appVersion
    val icon = windowsIcon
    val memory = builderMemory
    doLast {
        val output = out.get().asFile.apply { mkdirs() }
        val scratch = work.get().asFile.apply { mkdirs() }
        // Woka's icon and version inside the executable (ST-005): an installer takes it as it is.
        val resources = writeResourceScript(scratch, icon.asFile, version.get())
        val dirs = configs.map { it.asFile } + rules.get().asFile
        val arguments = nativeImageArguments(app.get().asFile, dirs, output, strictBuild.get(), resources, memory.get())
        runNativeImage(graalvmHome(home), arguments, scratch, resources)
    }
}

// The folder `NativeImageOptions` expects to find itself in — jpackage's `app\` without the
// jars: the exe and its DLLs, jawt again under bin\ (skiko looks there through java.home), the
// JDK data AWT reads from lib\, skiko's DLL and ICU data, and the app's resources.
tasks.register<Sync>("nativeAppImage") {
    description = "Lays out the native image as a folder Woka.exe runs from."
    dependsOn("nativeImage")
    into(layout.buildDirectory.dir("compose/binaries/main/native-image/Woka"))
    from(imageDir) { include("*.exe", "*.dll") }
    from(imageDir) {
        include("jawt.dll")
        into("bin")
    }
    from(graalHome.map { File(it, "lib") }) {
        include("fontconfig.bfc", "psfontj2d.properties", "tzdb.dat", "tzmappings")
        into("lib")
    }
    from(appDir) { include("skiko-windows-x64.dll", "icudtl.dat") }
    from(appDir.map { it.dir("resources") }) { into("resources") }
    // What jpackage checks an app image by, so `packageMsi` can install this folder (ST-005).
    from(appDir) {
        include(".jpackage.xml")
        into("app")
    }
    // GraalVM's terms travel with the image: its license, notice and the JDK modules' notices.
    from(graalHome.map { File(it) }) {
        include("LICENSE.txt", "NOTICE.txt", "legal/**")
        into("licenses/graalvm")
    }
}
