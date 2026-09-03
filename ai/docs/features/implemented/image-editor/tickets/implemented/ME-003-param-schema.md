# ME-003 — Parameter schema

| | |
|---|---|
| **Order** | 4 |
| **Depends on** | ME-002 |
| **Feature** | [Image Editor](../../image-editor.md) |

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

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **490 tests, 0 failures, 0 errors** — 484 before, plus the six
cases in `ParamSetTest`. All five files 21–110 lines.

**The split was planned, not discovered.** Step 3 said to split when `ParamSpec.kt` passes 150 lines;
at this comment density the two halves are 72 and 110, so one file was never going to fit. The seam
is the one the step names — scalars in `ParamSpec.kt`, choice/colour/action/object in
`ParamSpecChoice.kt` — chosen because the halves are read at different times: the scalars while
writing a tool, the rest while writing a row renderer.

**One decision the ticket left open: `ActionParam`'s type.** §3.4 gives it as
`ActionParam(key, label, icon)` and it carries no user-set value, but `ParamSpec<T>` requires a
`default: T`. It is `ParamSpec<Boolean>` meaning *this op exists*, not *the flag is on*; the
alternative, `ParamSpec<Unit>`, would need a row renderer that special-cases it. Two presses are two
ops because of the manifest's `Repeating` cardinality, not because of anything in the spec. If ME-005
finds a tool needing a press **count**, this is the one spec that changes.

Three smaller things settled while writing, each argued in the KDoc rather than here:

- `ChoiceParam` stores the enum constant's `name`, never its ordinal — an ordinal makes reordering an
  enum a silent rewrite of every saved document. A name that no longer resolves decodes to null and
  takes the default, which is what retiring an option looks like from an old document's side. There
  is a test for exactly that.
- `FloatParam`/`IntParam` clamp to their range on encode *and* decode, so a document written when the
  range was wider cannot hand a slider a value off its own track. A type mismatch still returns null —
  clamping is about range, not kind.
- `TextValue` exists with no scalar spec of its own; it is what `ChoiceParam` and `PreviewChoiceParam`
  encode to. A `TextParam` arrives when a tool needs free text.

**Added beyond the ticket:** `ParamSet.isSet(spec)`. The schema deliberately collapses *absent* and
*undecodable* into "read the default", and this is how a caller can still tell that a key was written —
degrading is not the same as forgetting. It is what the wrong-kind test asserts against.
