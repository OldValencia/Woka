package com.woka.core.data

/**
 * What the app should believe about a still's colour, whatever the file claims (ME-119).
 *
 * **This overrides detection, not output.** Woka draws into an sRGB Skia window and there
 * is no HDR swapchain anywhere in the stack, so [ForceHdr] cannot mean "send HDR to the
 * monitor" and does not pretend to. It means *treat this file as PQ*, which is a useful
 * thing to say about a file whose tags are wrong — the corpus holds three that carry a
 * perfect PQ description over ordinary SDR pixels.
 */
enum class HdrViewMode {
    /** Tone map when the file's transfer says HDR. The default, and what shipped before. */
    Auto,

    /** Tone map as PQ / BT.2020 even when the tags say SDR. */
    ForceHdr,

    /**
     * Never tone map.
     *
     * Also the app's answer to *"is this really HDR?"* — a genuine HDR picture loses its
     * highlights here, and a tags-only fake looks exactly as it did.
     */
    ForceSdr
}

/**
 * The current [HdrViewMode], for the readers that cannot resolve a setting.
 *
 * The same shape `AppliedStillEdits` uses and for the same reason: `displayable` and
 * `WokaStorage` are plain functions on the decode path, called from places that hold no
 * Koin scope, so the seam is a holder DI fills once rather than a parameter threaded
 * through every caller.
 *
 * **A supplier rather than a value**, so nothing has to observe the settings flow and
 * push. It reads `settings.value` at the moment it is asked, which cannot be stale.
 *
 * Uninstalled it answers [HdrViewMode.Auto] — the default, so a still decoded before the
 * graph is built behaves exactly as it always did rather than briefly wrong.
 */
object HdrView {

    private var supplier: (() -> HdrViewMode)? = null

    fun install(source: () -> HdrViewMode) {
        supplier = source
    }

    val mode: HdrViewMode
        get() = supplier?.invoke() ?: HdrViewMode.Auto

    /**
     * What the mode adds to a cache key, and **empty for [HdrViewMode.Auto]**.
     *
     * The mode belongs in the key rather than in an invalidation step: a key that carries
     * it means switching misses, switching back *hits*, and no code has to remember to
     * clear anything. Empty on the default keeps every existing key byte-identical, so
     * nobody's thumbnails are rebuilt for a switch they never touch.
     */
    val cacheSuffix: String
        get() = when (mode) {
            HdrViewMode.Auto -> ""
            HdrViewMode.ForceHdr -> "|hdr"
            HdrViewMode.ForceSdr -> "|sdr"
        }
}
