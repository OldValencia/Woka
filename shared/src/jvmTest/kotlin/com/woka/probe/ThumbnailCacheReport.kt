package com.woka.probe

/**
 * What [ThumbnailCacheProbe] measured, formatted for pasting into
 * `the-thumbnail-cache-grows-forever.md` unedited. Split from the probe under the §2
 * file-size mandate, the same way [renderTable] was split from the capability probes.
 */
internal class Pass(
    val cold: Long,
    val warm: Long,
    val rebuilt: Int,
    val flat: Boolean,
    val tiles: Int,
    val entries: Int,
    val bytes: Long
)

internal fun renderThumbnailReport(
    tiles: Int,
    rows: List<Pair<String, Pass>>,
    syntheticEntries: Int,
    sweepMs: Long,
    encodings: List<List<String>>
): String = buildString {
    appendLine("Thumbnail cache probe — $tiles tiles per population")
    appendLine()
    appendLine(
        renderTable(
            listOf(
                "population", "cache absent", "cache present", "per tile absent",
                "per tile present", "rebuilt on the warm pass", "poster reads flat"
            ),
            rows.map { (name, pass) -> passRow(name, pass) }
        )
    )
    appendLine()
    appendLine("Rebuilt counts entries whose length no longer matches the poster planted")
    appendLine("before the warm pass: anything but zero means that row's `cache present`")
    appendLine("figure is a second build and not a cache read.")
    appendLine()
    val built = rows.sumOf { it.second.entries }
    val bytes = rows.sumOf { it.second.bytes }
    appendLine("Mean poster as written: **${bytes / built.coerceAtLeast(1)} bytes** over $built")
    appendLine("entries — a fixture fact only; the reported ~100 MB cannot be turned into an")
    appendLine("entry count from here. **The `cache present` column is pessimistic**: the poster")
    appendLine("planted to detect rebuilds is a 39 KB PNG, larger than anything written here.")
    appendLine()
    appendLine("Walking $syntheticEntries entries, summing length and mtime: **$sweepMs ms**.")
    appendLine("That is the whole cost of the `total size` policy's directory pass.")
    appendLine()
    appendLine("One poster of real photographic content, at each format Skia can write:")
    appendLine()
    append(renderTable(listOf("format", "on disk", "against PNG", "encode"), encodings))
}

private fun passRow(name: String, pass: Pass): List<String> = listOf(
    name,
    "**${pass.cold} ms**",
    "**${pass.warm} ms**",
    "${pass.cold / pass.tiles} ms",
    "${pass.warm / pass.tiles} ms",
    if (pass.rebuilt == 0) "none" else "**${pass.rebuilt} of ${pass.tiles}**",
    presenceCell(pass.flat)
)
