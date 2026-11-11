package com.woka.probe

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.util.Locale

/** TEMPORARY (8K investigation §7): bounded draw-submission timings, not GPU completion. */
internal class EightKProbeMetrics {
    private val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    private val gc = ManagementFactory.getGarbageCollectorMXBeans()
    private val intervals = DoubleArray(4096)
    private var count = 0
    private var previousId = -1L
    private var previousNs = 0L
    private var startNs = 0L
    private var startCpu = 0L
    private var startGc = 0L
    private var startCollections = 0L
    private var active = false

    @Synchronized
    fun start() {
        count = 0
        previousId = -1L
        previousNs = 0L
        startNs = System.nanoTime()
        startCpu = os.processCpuTime
        startGc = gc.sumOf { it.collectionTime }
        startCollections = gc.sumOf { it.collectionCount }
        active = true
    }

    @Synchronized
    fun drawn(id: Long) {
        if (!active || id <= 0 || id == previousId) return
        val now = System.nanoTime()
        if (previousNs != 0L && count < intervals.size) intervals[count++] = (now - previousNs) / 1e6
        previousId = id
        previousNs = now
    }

    @Synchronized
    fun finish(): String {
        active = false
        val elapsed = System.nanoTime() - startNs
        val samples = intervals.copyOf(count).sorted()
        val cpu = 100.0 * (os.processCpuTime - startCpu) / elapsed / os.availableProcessors
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val frames = if (previousNs == 0L) 0 else count + 1
        return "seconds=${number(elapsed / 1e9)} drawnFps=${number(frames * 1e9 / elapsed)} " +
            "gapP95Ms=${number(percentile(samples, .95))} gapMaxMs=${number(samples.lastOrNull() ?: 0.0)} " +
            "gapsOver25ms=${samples.count { it > 25 }} cpuPercent=${number(cpu)} " +
            "heapUsedMiB=${heap.used / 1048576} heapCommittedMiB=${heap.committed / 1048576} " +
            "gcMs=${gc.sumOf { it.collectionTime } - startGc} " +
            "collections=${gc.sumOf { it.collectionCount } - startCollections}"
    }
}

internal fun number(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

internal fun percentile(sorted: List<Double>, fraction: Double): Double =
    sorted.getOrNull(((sorted.size - 1) * fraction).toInt().coerceAtLeast(0)) ?: 0.0
