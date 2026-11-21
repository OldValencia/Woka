package nativeimage

import org.gradle.api.GradleException
import java.io.File
import java.util.concurrent.TimeUnit

/** Archive options of the JVM launcher that GraalVM's java must not be handed. */
private val ARCHIVE_OPTIONS = listOf("-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile=", "-Xshare:", "-XX:AOTCache=")

/**
 * One run of the distributable under the native-image agent, merging what it touches into
 * [mergeDir] (`config-merge-dir`: what is there stays — the manual sessions' input JNI
 * among it). The JVM gets the packaged launcher's own options from `Woka.cfg`, minus its
 * archive ones, and an isolated storage under [work] — never the user's `settings.json`.
 *
 * [medium] is opened as the launch argument; null opens the app with nothing. With [manual]
 * false the app quits once it has drawn (`woka.aotTraining.exitMs`); true leaves it to the
 * author, for the input only real use produces, for up to half an hour.
 */
fun runScenario(graalHome: File, appDir: File, mergeDir: File, work: File, medium: File?, manual: Boolean = false) {
    val cfg = File(appDir, "Woka.cfg").readLines()
    val options = cfg.filter { it.startsWith("java-options=") }
        .map { it.substringAfter('=').replace("\$APPDIR", appDir.path) }
        .filterNot { option -> ARCHIVE_OPTIONS.any { option.startsWith(it) } }
    val storage = File(work, "storage").apply { deleteRecursively(); mkdirs() }
    // Present, so the first-run folder menu registration skips: no registry writes.
    File(storage, ".context_menu_initialized").writeText("")
    mergeDir.mkdirs()
    clearStaleLock(mergeDir)
    val command = buildList {
        add(File(graalHome, "bin/java.exe").path)
        addAll(options)
        add("-Dwoka.storageRoot=${storage.path}")
        add("-agentlib:native-image-agent=config-merge-dir=${mergeDir.path}")
        if (!manual) {
            // 1.5 s after a picture is drawn; and, for a run with no picture (the app with nothing
            // open), 8 s after the shell's first frame — whichever comes first.
            add("-Dwoka.aotTraining.exitMs=1500")
            add("-Dwoka.probe.startup.output=${File(work, "${medium?.name ?: "empty"}.csv").path}")
            add("-Dwoka.probe.startup.exitMs=8000")
        }
        add("-cp")
        add(appClasspath(appDir).joinToString(File.pathSeparator))
        add(cfg.first { it.startsWith("app.mainclass=") }.substringAfter('='))
        medium?.let { add(it.path) }
    }
    val log = File(work, "${medium?.name ?: "empty"}.log")
    val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
    val minutes = if (manual) 30L else 3L
    if (!process.waitFor(minutes, TimeUnit.MINUTES)) {
        process.destroyForcibly()
        throw GradleException("The agent run on ${medium ?: "nothing"} did not exit in $minutes minutes; see $log")
    }
    if (process.exitValue() != 0) throw GradleException("The agent run on ${medium ?: "nothing"} exited ${process.exitValue()}; see $log")
}

/**
 * The agent locks its output folder with a `.lock` holding its pid and removes it on exit; a run
 * killed for a timeout leaves it behind, and every later run then refuses to start. Removed
 * only when that process is gone.
 */
private fun clearStaleLock(dir: File) {
    val lock = File(dir, ".lock")
    val pid = lock.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: return
    if (ProcessHandle.of(pid).isEmpty) lock.delete()
}
