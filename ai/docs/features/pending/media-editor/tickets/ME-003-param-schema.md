# ME-003 — Parameter schema

| | |
|---|---|
| **Order** | 4 |
| **Depends on** | ME-002 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** `ParamSpec`, `ParamValue`, `ParamSet` — typed at the call site, serialisable on the wire.

**Why now.** Every later type — `ToolOp`, `EditDocument`, the inspector rows — is defined in terms of
it. Getting the value representation wrong here is a rewrite of everything above it.

## Steps

1. `ParamValue.kt` (commonMain): `@Serializable sealed interface ParamValue` with `FloatValue`,
   `IntValue`, `BoolValue`, `TextValue`, `ColorValue(argb: Long)`, `JsonValue(element: JsonElement)`.
   `ColorValue` is a `Long` and never `androidx.compose.ui.graphics.Color` — a serialised model does
   not carry a Compose type (`code-style/model.md`).
2. `ParamSpec.kt` (commonMain): `sealed interface ParamSpec<T> { val key: String; val default: T;
   fun encode(value: T): ParamValue; fun decode(value: ParamValue): T? }`, with `FloatParam`,
   `IntParam`, `BoolParam`, `ChoiceParam<E>`, `PreviewChoiceParam<E>`, `ColorParam`, `ActionParam`,
   `ObjectParam<T>`. `decode` returns null on a type mismatch rather than throwing: a document written
   by an older schema must degrade, not crash (§3.4, "never silently loses work").
3. Split when the file passes 150 lines — the natural seam is `ParamSpec.kt` (the interface and the
   scalar specs) / `ParamSpecChoice.kt` (choice, colour, object).
4. `ParamSet.kt` (commonMain): `@Serializable data class ParamSet(val values: Map<String, ParamValue>
   = emptyMap())` with `operator fun <T> get(spec: ParamSpec<T>): T` — falling back to `spec.default`
   when the key is absent **or** undecodable — and `fun <T> with(spec: ParamSpec<T>, value: T):
   ParamSet` returning a `.copy()`.
5. `EditorJson.kt` (commonMain): the `Json` instance `ObjectParam` needs, `internal`, with
   `ignoreUnknownKeys = true`. Do not reach for the one `SettingsStore` owns — settings and documents
   are different schemas with different tolerance for change.

## Files

`feature/editor/core/ParamValue.kt`, `core/ParamSpec.kt`, `core/ParamSet.kt`, `core/EditorJson.kt`
(all commonMain, new)

## Done when

`jvmTest/unit/ParamSetTest.kt` covers:

- typed get returns the stored value, and the call site sees `Float`, not `ParamValue`;
- an absent key returns the spec's default;
- a `FloatValue` read through an `IntParam` returns the default instead of throwing;
- `with()` does not mutate the receiver;
- a `ParamSet` holding one of every `ParamValue` round-trips through JSON unchanged.
