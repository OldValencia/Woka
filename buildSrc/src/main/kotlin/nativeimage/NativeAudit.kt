package nativeimage

import org.gradle.api.GradleException
import java.io.File
import java.util.concurrent.TimeUnit

/** What a strict image prints for a registration it was not built with. */
private val MISSING = Regex("""Missing[A-Za-z]*RegistrationError|Cannot reflectively|MissingResourceRegistration""")

/**
 * Opens each of [media] (null: the app with nothing) in the laid-out image [exe], built with
 * `--exact-reachability-metadata`, under `-XX:MissingRegistrationReportingMode=Warn` — so every
 * dynamic access the metadata lacks is printed, even where the code catches what it throws.
 * Each run quits on its own once drawn (`woka.probe.startup.exitMs`) on an isolated storage.
 * Returns the reporting lines, each prefixed with the medium it came from.
 */
fun auditImage(exe: File, media: List<File?>, work: File): List<String> {
    val storage = File(work, "storage").apply { deleteRecursively(); mkdirs() }
    File(storage, ".context_menu_initialized").writeText("")
    return media.flatMap { medium ->
        val name = medium?.name ?: "empty"
        val log = File(work, "$name.log")
        val command = listOfNotNull(
            exe.path,
            "-XX:MissingRegistrationReportingMode=Warn",
            "-Dwoka.storageRoot=${storage.path}",
            "-Dwoka.probe.startup.output=${File(work, "$name.csv").path}",
            "-Dwoka.probe.startup.exitMs=3000",
            medium?.path
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw GradleException("The image did not exit on $name; see $log")
        }
        // The first report of a run is preceded by a note naming the error class; it is not a gap.
        log.readLines().filter { MISSING.containsMatchIn(it) && !it.startsWith("Note:") }.map { "$name: $it" }
    }
}
