# Woka — Working Mandates (Claude Code)

Imported by the root `CLAUDE.md` via `@./.claude/AGENTS.md` — that import is what loads this file;
it is not picked up automatically by its location. Project map, doc index and commands live in
`CLAUDE.md`; this file is *how you work*.

---

## 1. Context

- **App:** Woka — Compose Multiplatform Desktop media viewer/editor.
- **Targets:** Desktop (JVM) **only**. No Android, no iOS, no web.
- **Arch:** MVVM. **DI:** Koin. **Data:** local JSON via `kotlinx.serialization`. No databases.
- **Vision:** `ai/docs/general_idea.md`. **Plan of record:** `ai/docs/roadmap.md`.

## 2. Core mandates (STRICT)

**File size — 130–150 lines max.**
Before editing ANY `.kt` file, check its length. If it already exceeds ~150 lines, **stop and offer a
split** into smaller components before adding anything. Do not cram code (one statement / one lambda
per line, no chained one-liners to shrink the count).

**NO fully qualified names.** Ever. Not `androidx.compose.ui.text.style.TextAlign.Left`, not
`java.io.File`, not `com.woka.*`. **Import them.** Writing an inline FQN to skip an import block is
forbidden — no exceptions.

**Zero unprompted changes.** Fix only what was asked. No opportunistic refactors, no "while I was
there" cleanups, no renames, no reformatting of untouched code. The file-size rule above is the only
thing you may raise unasked — and you *raise* it, you don't act on it.

**Builds are mandatory.** After edits, run the build in the background and **wait** for it.
Never claim success on a failing build.

**Read before you edit.** Use `Read` on the file first. Use `Grep`/`Glob` to locate code — not shell
`cat`/`findstr`/`Select-String` for reading.

**Compose discipline.** No `androidx.compose.*` imports in ViewModels. ViewModel exposes `StateFlow`;
UI observes. State is immutable `data class` — never mutate a list/object in place, always `.copy()`.

**Design.** Apple HIG. Not Material. Minimalist, clean lines, 1.dp borders, 8–12.dp corner radii,
`#F2F2F7` (or its dark equivalent) surfaces, clean cards, smooth animations.
Reference: `ai/mockups/design/FINAL_design_mockup.html`.

## 3. Workflow

1. **Plan.** For anything non-trivial, enter plan mode, present the plan, get explicit approval
   (`ExitPlanMode`). For a persistent feature, the plan is a file in `ai/docs/features/pending/` —
   see the `plan-creation` skill.
2. **Code.** Only after approval ("Apply", "Go ahead", plan accepted). Small, targeted edits.
3. **Verify.** Build + the anti-FQN self-check below. Wait for results.
4. **Report.** State what changed and the build outcome. Then **stop.**

If the request is ambiguous, **ask** — do not guess and do not build a second interpretation "just
in case".

## 4. Mandatory self-check (anti-FQN)

After editing any `.kt` file, mechanically verify — do not eyeball it:

```
Grep(pattern: "(androidx\.|java\.|javax\.|kotlinx\.|org\.bytedeco\.|com\.woka\.)[A-Za-z]",
     path: "<edited file>", output_mode: "content", -n: true)
```

Every hit outside the `import`/`package` block is a violation and must be converted to an import.

**You MUST NOT report the edit as complete until this check has actually been run, its output read,
and every match cleaned up.**

## 5. Tooling notes (Windows)

- Gradle: `./gradlew.bat <task>` via the PowerShell tool, `run_in_background: true`.
  Fast check: `:shared:compileKotlinJvm -q`. Full: `:shared:build`. Run: `:desktopApp:run`.
- Prefer `Read` / `Grep` / `Glob` / `Edit` over shell equivalents — they are faster and produce
  clickable paths.
- Desktop-only APIs (folder pickers, native dialogs) go through `jvmMain` `expect`/`actual` using
  AWT `FileDialog` — see `ui/components/dialogs/Dialogs.kt`.

## 6. Communication

Dry, direct, technical, active voice. No filler, no intros, no apologies, no restating the request
back. Report facts and stop.
