package nativeimage

import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the agent saw during the test suite, minus what only a test run reaches (ST-006). */
class AgentMetadataTest {

    @Test
    fun `only production entries survive`() {
        val dir = Files.createTempDirectory("agent").toFile()
        try {
            val classes = File(dir, "classes/com/woka/unit").apply { mkdirs() }
            File(classes, "SomeTest.class").writeText("")
            File(classes, "Helper\$Inner.class").writeText("")
            val resources = File(dir, "resources/media").apply { mkdirs() }
            File(resources, "clip.mkv").writeText("")
            val json = File(dir, "in.json").apply {
                writeText(
                    """
                    {"reflection": [
                      {"type": "com.woka.core.data.MediaItem"},
                      {"type": "com.woka.unit.SomeTest"},
                      {"type": "com.woka.unit.Helper${'$'}Inner"},
                      {"type": "org.junit.runner.Description"},
                      {"type": {"proxy": ["com.woka.unit.SomeTest"]}},
                      {"type": {"proxy": ["java.lang.Runnable"]}}
                    ], "resources": [
                      {"glob": "media/clip.mkv"},
                      {"glob": "composeResources/x.svg"}
                    ]}
                    """.trimIndent()
                )
            }
            val out = File(dir, "out.json")
            dropTestOnly(json, File(dir, "classes"), File(dir, "resources"), out)

            @Suppress("UNCHECKED_CAST")
            val kept = JsonSlurper().parse(out) as Map<String, List<Map<String, Any>>>
            assertEquals(
                listOf("com.woka.core.data.MediaItem", mapOf("proxy" to listOf("java.lang.Runnable"))),
                kept.getValue("reflection").map { it["type"] }
            )
            assertEquals(listOf("composeResources/x.svg"), kept.getValue("resources").map { it["glob"] })
        } finally {
            dir.deleteRecursively()
        }
    }
}
