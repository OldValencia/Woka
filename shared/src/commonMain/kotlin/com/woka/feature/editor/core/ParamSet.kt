package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The values one `ToolOp` carries, keyed by [ParamSpec.key].
 *
 * A map rather than a per-tool data class, because §3.4's whole claim is that the
 * shell renders a panel for a tool it does not know — which it cannot do against
 * a type it would have to name. The typing is restored at the call site by [get],
 * so a tool reads `p[Brightness]` as a `Float` and only the wire sees the map.
 *
 * Absent and undecodable are deliberately the **same** outcome: the spec's
 * default. A document written before a parameter existed and one written when it
 * had a different type are the same situation from here — the tool has no usable
 * value and must still render — and giving them separate behaviour would mean
 * every call site handling a null it can do nothing about (§3.4).
 */
@Immutable
@Serializable
data class ParamSet(val values: Map<String, ParamValue> = emptyMap()) {

    /** The typed value, or [ParamSpec.default] when it is missing or unreadable. */
    operator fun <T> get(spec: ParamSpec<T>): T {
        val stored = values[spec.key] ?: return spec.default
        return spec.decode(stored) ?: spec.default
    }

    /** A copy with [spec] set to [value]; the receiver is untouched. */
    fun <T> with(spec: ParamSpec<T>, value: T): ParamSet =
        copy(values = values + (spec.key to spec.encode(value)))

    /** Whether [spec] was ever written here, as opposed to reading as its default. */
    fun isSet(spec: ParamSpec<*>): Boolean = values.containsKey(spec.key)
}
