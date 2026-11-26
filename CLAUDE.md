# Woka — Claude Code Entry Point

Compose Multiplatform **Desktop** (JVM only) media viewer/editor. Apple HIG, minimalist.
Working mandates — workflow, verification, the rules that gate a change: @./ai/docs/conventions/mandates.md

## Hard rules

**Git: only on an explicit instruction.** No commit/add/push/branch/checkout/reset/stash/rm/mv/tag on
your own initiative. An approved plan that *mentions* a commit is not permission to commit — it
describes what the user will do. Finish the work, say what's ready and what a commit would contain,
stop. A commit never implies a push; a branch ahead of `origin` is normal, not something to flag.
Read-only `status`/`log`/`diff` only when the task needs it. Unsure → ask.

**Don't guess.** Unknown cause, ambiguous requirement, or unclear whether an action is wanted → add
logging, ask, or both. By the second unconfirmed hypothesis stop theorising: instrument the suspects,
hand over the log lines, state which reading means what. Mark diagnostics `TEMPORARY (§ref)`; remove
them once the cause is known and grep to prove it. Check your own test fixtures before blaming code.

## `ai/` — design source of truth

Read the relevant doc before planning or implementing; never invent behaviour that contradicts it.

- `docs/general_idea.md` — vision, use cases · `docs/roadmap.md` — phases, CLI routing (`Woka.exe [path]`)
- `docs/conventions/` — how to work here: `mandates.md` (gates) · `code-style/README.md` (**router** —
  open it, then the one file for what you are writing) · `layout.md` (package map) ·
  `feature-docs.md` · `plans.md` · `tickets.md` · `commits.md` (no attribution trailer)
- `docs/features/pending/` — approved, unshipped; **work comes from here** · `implemented/` — shipped
- `mockups/design/woka.html` — visual truth, a scene per screen, `#section/scene` links ·
  `legacy/` — earlier mockups · `mockups/logos/` — branding

Small feature → `pending/<name>.md`. One outliving a sitting → `pending/<feature>/<feature>.md` plus
`tickets/` (one file per specified ticket + `backlog.md`) — see `feature-docs.md`, `tickets.md`.
Fully shipped → **move** to `implemented/`. Phase state change → update `roadmap.md`.

## Layout — package root `com.woka`

**Feature-first.** `app/` wires (DI, navigation, shell), `core/` is shared (data, system, ui, utils),
`feature/<name>/` owns one thing; `desktopApp/` is the launcher. A file's package is its directory;
nothing lives outside these roots. `expect` in `commonMain`, `actual` in `jvmMain`, **same package**.
Full map: `ai/docs/conventions/layout.md` — open it before adding a file or package.

## Stack

Compose Desktop + Skia · MVVM: ViewModels expose `StateFlow`, no `androidx.compose.*` inside them ·
Koin DI, never instantiate inline · JavaCV/FFmpeg for video/audio/probing · `kotlinx.serialization`
JSON at `user.home/Documents/OldValencia/Woka/settings.json` — no DB, no cloud · JVM desktop only.

## Commands

```powershell
./gradlew.bat :shared:compileKotlinJvm -q   # fast check after edits
./gradlew.bat :shared:build                 # + tests
./gradlew.bat :desktopApp:run               # hotRun --auto for hot reload
```

Run via the PowerShell tool with `run_in_background: true`, then **wait** for the result. A failed
build is reported as a failed build. Measure file length with `wc -l` — PowerShell `Measure-Object
-Line` under-reports.
