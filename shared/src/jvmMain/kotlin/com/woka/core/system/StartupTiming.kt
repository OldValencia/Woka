package com.woka.core.system

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** TEMPORARY (ST-001): opt-in draw-submission timing, not monitor scan-out timing. */
object StartupTiming {
    private val output = System.getProperty("woka.probe.startup.output")?.let(::File)
    private val seen = mutableSetOf<String>()
    private val startNs = System.nanoTime()
    private val processAgeMs = if (output == null) 0 else {
        val born = ProcessHandle.current().info().startInstant().orElse(Instant.now())
        File("${output.path}.process").writeText("${ProcessHandle.current().pid()},${born.toEpochMilli()}")
        val origin = System.getProperty("woka.probe.startup.launchEpochMs")?.toLongOrNull()
            ?: born.toEpochMilli()
        Instant.now().toEpochMilli() - origin
    }

    @Synchronized
    fun mark(stage: String) {
        val file = output ?: return
        if (!seen.add(stage)) return
        val elapsed = processAgeMs + (System.nanoTime() - startNs) / 1e6
        file.appendText(String.format(Locale.ROOT, "%s,%.3f%n", stage, elapsed))
        if (stage == "shell-draw") {
            val delay = System.getProperty("woka.probe.startup.exitMs")?.toLongOrNull() ?: return
            thread(name = "Woka-Startup-Probe-Exit", isDaemon = true) {
                Thread.sleep(delay)
                exitProcess(0)
            }
        }
    }

    fun shellModifier(): Modifier = if (output == null) Modifier else Modifier.drawWithContent {
        drawContent()
        mark("shell-draw")
    }

}
