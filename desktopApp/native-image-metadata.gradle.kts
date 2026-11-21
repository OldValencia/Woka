// Keeping the native image's metadata true as the code moves (ST-006). Applied from
// native-image.gradle.kts; none of these tasks is part of a build — run them before a release
// and after adding reflection, JNI, FFM calls or resources loaded by path.
//
// As there, every task action copies what it needs into locals inside `register { }`.
import nativeimage.auditImage
import nativeimage.dropTestOnly
import nativeimage.graalvmHome
import nativeimage.mergeAgentOutputs
import nativeimage.runScenario

val graalHome = providers.gradleProperty("woka.graalvm.home")
    .orElse(providers.environmentVariable("GRAALVM_HOME"))
val strict = providers.gradleProperty("woka.native.strict").map { it.toBoolean() }.orElse(false)
val manual = providers.gradleProperty("woka.agent.manual").map { it.toBoolean() }.orElse(false)
val appDir = layout.buildDirectory.dir("compose/binaries/main/app/Woka/app")
val metadataDir = layout.projectDirectory.dir("native-image")
val sharedBuild = project(":shared").layout.buildDirectory
val fixtureDir = rootProject.file("shared/src/jvmTest/resources/media")

/**
 * The media the scripted runs open: one of each decoder path Woka owns — EXIF rotation, an
 * animation, PQ HDR, a HEIC burst, the bundled JXL and JXR natives, a 4K HDR film, text and
 * bitmap subtitles — then the fixture folder itself (the grid and its posters), and the app
 * with nothing open first.
 */
val scenarioMedia: List<File?> = listOf<File?>(null) + listOf(
    "exif_rot90.jpg", "anim.gif", "hdr_pq.avif", "burst.heic", "jxl-lossy.jxl", "jxr-rgb24_gradient.jxr",
    "WokaSample.S01E01.2160p.HDR.TEST.mkv", "textsubs.mkv", "WokaSample.BitmapSubs.mkv"
).map { File(fixtureDir, it) } + fixtureDir

tasks.register("nativeImageAgentTests") {
    description = "Rewrites native-image/tests from the agent over the :shared test suite."
    dependsOn(":shared:agentTest")
    val home = graalHome.orNull
    val agentOut = sharedBuild.dir("native-image-agent/tests")
    val merged = layout.buildDirectory.dir("native-image/agent-tests")
    val testClasses = sharedBuild.dir("classes/kotlin/jvm/test")
    val testResources = sharedBuild.dir("processedResources/jvm/test")
    val target = metadataDir.file("tests/reachability-metadata.json")
    doLast {
        val inputs = agentOut.get().asFile.listFiles { f -> f.isDirectory }.orEmpty().toList()
        mergeAgentOutputs(graalvmHome(home), inputs, merged.get().asFile)
        val merge = File(merged.get().asFile, "reachability-metadata.json")
        val report = dropTestOnly(merge, testClasses.get().asFile, testResources.get().asFile, target.asFile)
        logger.lifecycle("agent over the tests: $report")
    }
}

// Merges into what is there: the manual sessions' input JNI stays. `-Pwoka.agent.manual=true`
// opens the app on the fixture folder and records whatever the author does until he closes it.
tasks.register("nativeImageAgentApp") {
    description = "Merges scripted app runs under the agent into native-image/app."
    dependsOn("createDistributable")
    val home = graalHome.orNull
    val app = appDir
    val target = metadataDir.dir("app")
    val work = layout.buildDirectory.dir("native-image/agent-app")
    val media = scenarioMedia
    val byHand = manual
    val folder = fixtureDir
    doLast {
        val graal = graalvmHome(home)
        val scratch = work.get().asFile.apply { mkdirs() }
        val runs = if (byHand.get()) listOf(folder) else media
        runs.forEach { runScenario(graal, app.get().asFile, target.asFile, scratch, it, manual = byHand.get()) }
        logger.lifecycle("agent over ${runs.size} app runs merged into ${target.asFile}")
    }
}

tasks.register("nativeImageAudit") {
    description = "Runs the strict image over the fixtures and fails on any missing registration."
    dependsOn("nativeAppImage")
    val strictBuild = strict
    val exe = layout.buildDirectory.file("compose/binaries/main/native-image/Woka/Woka.exe")
    val work = layout.buildDirectory.dir("native-image/audit")
    val media = scenarioMedia
    doLast {
        if (!strictBuild.get()) {
            throw GradleException("nativeImageAudit needs -Pwoka.native.strict=true: only a strict image reports what it lacks")
        }
        val scratch = work.get().asFile.apply { deleteRecursively(); mkdirs() }
        val lines = auditImage(exe.get().asFile, media, scratch)
        val report = File(scratch, "audit.txt").apply { writeText(lines.joinToString("\n")) }
        if (lines.isNotEmpty()) {
            throw GradleException("${lines.size} missing registrations over ${media.size} runs; see $report\n" + lines.take(10).joinToString("\n"))
        }
        logger.lifecycle("native image audit clean over ${media.size} runs")
    }
}
