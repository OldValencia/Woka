package com.woka.support

/**
 * Watches for something a real background thread will do.
 *
 * `SettingsStore` debounces its writes by 150 ms on `Dispatchers.IO`, and the
 * decode loop hands frames over on threads of its own — neither is reachable from
 * `runTest`'s virtual clock, so the only honest option is to watch and give up
 * loudly.
 */
fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        Thread.sleep(10L)
    }
    return condition()
}

/**
 * As [waitFor], but a timeout is a failure naming what was awaited. Failing rather
 * than falling through is the point: a silent pass would say the write happened
 * when it never did.
 */
fun awaitTrue(description: String, timeoutMs: Long = 5_000L, condition: () -> Boolean) {
    if (!waitFor(timeoutMs, condition)) {
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $description")
    }
}
