package nativeimage

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File

/** Packages only a test run loads — their types are no part of the shipped image. */
private val TEST_FRAMEWORKS = listOf(
    "org.junit.", "junit.", "kotlin.test.", "org.gradle.", "worker.org.gradle.", "org.hamcrest.",
    "androidx.compose.ui.test.", "kotlinx.coroutines.test.", "org.opentest4j.", "org.apiguardian."
)

/**
 * One metadata set out of the agent's per-JVM folders ([inputs]), through GraalVM's own
 * `native-image-configure generate` — the tool that knows how two records of one type combine.
 */
fun mergeAgentOutputs(graalHome: File, inputs: List<File>, out: File) {
    if (inputs.isEmpty()) throw GradleException("The agent wrote nothing to merge")
    out.deleteRecursively()
    val tool = File(graalHome, "bin/native-image-configure.cmd").path
    val shell = System.getenv("ComSpec") ?: "cmd.exe"
    val command = listOf(shell, "/c", tool, "generate") + inputs.map { "--input-dir=${it.path}" } + "--output-dir=${out.path}"
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val log = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) throw GradleException("native-image-configure failed:\n$log")
}

/**
 * [json] without what only the test run reached: types compiled from the test sources
 * ([testClasses], a classes directory), test frameworks, proxies and lambdas naming either, and
 * resource globs that are test resources ([testResources]). Writes [out]; returns how many
 * entries each section kept and dropped.
 */
fun dropTestOnly(json: File, testClasses: File, testResources: File, out: File): Map<String, String> {
    val testTypes = testClasses.walkTopDown().filter { it.extension == "class" }
        .map { it.relativeTo(testClasses).path.removeSuffix(".class").replace(File.separatorChar, '.') }
        .toSet()
    val testFiles = testResources.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(testResources).path.replace(File.separatorChar, '/') }
        .toSet()
    val wokaTests = testTypes.filter { it.startsWith("com.woka.") }

    fun isTestType(name: String) =
        name in testTypes || name.substringBefore('$') in testTypes || TEST_FRAMEWORKS.any { name.startsWith(it) }

    fun mentionsTest(value: Any?): Boolean {
        val text = JsonOutput.toJson(value)
        return wokaTests.any { "\"$it\"" in text } || TEST_FRAMEWORKS.any { "\"$it" in text }
    }

    @Suppress("UNCHECKED_CAST")
    val metadata = JsonSlurper().parse(json) as MutableMap<String, Any?>
    val report = LinkedHashMap<String, String>()
    metadata.keys.toList().forEach { section ->
        val entries = metadata[section] as? List<*> ?: return@forEach
        val kept = entries.filterNot { entry ->
            val map = entry as? Map<*, *> ?: return@filterNot false
            when (val type = map["type"]) {
                is String -> isTestType(type)
                is Map<*, *> -> mentionsTest(type)
                else -> (map["glob"] as? String)?.let { it in testFiles } ?: false
            }
        }
        metadata[section] = kept
        report[section] = "${kept.size} kept, ${entries.size - kept.size} dropped"
    }
    out.parentFile.mkdirs()
    out.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)))
    return report
}
