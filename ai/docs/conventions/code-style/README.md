# Code Style — router

**Read this file, then exactly one row's file. Not the folder.** Each rule file is self-contained and
assumes you have read the four universal rules below and nothing else.

## Router

| About to write / edit | Open |
|---|---|
| a `@Composable` — screen, control, overlay, row, dialog | `composable.md` |
| a ViewModel, a session, a state holder, a `StateFlow` | `state.md` |
| a `data class` / `enum` / `sealed` that is stored, serialised or passed around | `model.md` |
| a `WokaSettings` field, a path, a cache key, anything on disk | `storage.md` |
| a decoder, a player, a Skia/AWT/JavaCV/ffmpeg path, threads, native memory | `engine.md` |
| an `expect`/`actual` pair, or deciding `commonMain` vs `jvmMain` | `engine.md` |
| a Koin module, a factory, wiring a dependency | `di.md` |
| a test, a harness, a fixture, a probe | `test.md` |
| a KDoc block or an inline comment (**any file**) | `comments.md` |
| a name you are unsure about (**any file**) | `naming.md` |
| `libs.versions.toml`, a Gradle file, a dependency | `build.md` |

Two rows apply to a single file often — a composable with a comment is `composable.md` +
`comments.md`. Read both; do not read the rest.

## The four universal rules

They are repeated in no other file. They apply to every `.kt` in the repo.

1. **130–150 lines per file.** Check before editing. Already over → stop and offer a split; never cram.
   Measure with `wc -l`, not PowerShell `Measure-Object -Line`.
2. **No fully qualified names, ever.** Not `java.io.File`, not `androidx.compose.ui.unit.dp`, not
   `com.woka.*`. Import it. Verified mechanically after every edit — see
   `ai/docs/conventions/mandates.md`.
3. **Zero unprompted changes.** Fix what was asked. No opportunistic refactor, rename or reformat of
   code you were not sent to.
4. **Immutability by default.** `val` over `var`, `data class` + `.copy()` over in-place mutation,
   a replaced list over a mutated one.

## What lives where

- **Mandates** (`ai/docs/conventions/mandates.md`) — the rules that gate a change, checked
  mechanically. Where a mandate and a rule file disagree, the mandate wins.
- **This folder** — how new code reads once it passes those gates.
- **`feature-docs.md`** — how documents, tickets and mockups are organised. Not about code.

An example here cites a real file. If the citation stops matching that file, the example is wrong —
fix the example, not the code.
