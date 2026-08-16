# Woka — Working Mandates

Loaded by the `@./ai/docs/conventions/mandates.md` import in `CLAUDE.md`, not by its location.
`CLAUDE.md` is the project map; this file is *how you work*.

The mandates below are the rules that **gate** a change — few, strict, checked mechanically. The
idioms that make new code read like the existing code live in `code-style/`: open its `README.md`,
which routes you to the one file for what you are about to write. Where a mandate and a rule file
disagree, this file wins.

## Mandates (strict)

- **File size 130–150 lines.** Check before editing any `.kt`. Already over ~150 → **stop and offer a
  split** before adding anything. Never cram (one statement/lambda per line; no chained one-liners to
  shrink the count). Measure with `wc -l`.
- **No fully qualified names, ever** — not `androidx.compose.ui.text.style.TextAlign.Left`, not
  `java.io.File`, not `com.woka.*`. Import them. An inline FQN to avoid writing an import is
  forbidden, no exceptions.
- **Zero unprompted changes.** Fix only what was asked — no opportunistic refactors, renames, or
  reformatting of untouched code. The file-size rule is the only thing you raise unasked, and you
  *raise* it, you don't act on it.
- **Build after edits and wait for the result.** Never claim success on a failing build.
- **Read before you edit.** `Read` the file first; `Grep`/`Glob` to locate code — not shell
  `cat`/`findstr`/`Select-String`.
- **Compose.** No `androidx.compose.*` in ViewModels. State is an immutable `data class` — never
  mutate a list or object in place, always `.copy()`.
- **Design: Apple HIG, never Material.** Minimalist, 1.dp borders, 8–12.dp radii, `#F2F2F7` (or its
  dark equivalent) surfaces, clean cards, smooth animations.
  Ref: `ai/mockups/design/FINAL_design_mockup.html`.
- **Desktop-only APIs** (folder pickers, native dialogs) go through `jvmMain` `expect`/`actual` with
  AWT `FileDialog` — see `ui/components/dialogs/Dialogs.kt`.

## Workflow

Plan (non-trivial → plan mode, explicit approval; persistent feature → `ai/docs/features/pending/`
per `ai/docs/conventions/plans.md`) → code only once approved → build **and** the anti-FQN check,
waiting for both → report what changed and the build outcome → **stop**.
Ambiguous request → ask. Never guess, never build a second interpretation "just in case".

## Anti-FQN self-check — mandatory

After editing any `.kt`, verify mechanically, never by eye:

```
Grep(pattern: "(androidx\.|java\.|javax\.|kotlinx\.|org\.bytedeco\.|org\.jetbrains\.|com\.woka\.)[A-Za-z]",
     path: "<edited file>", output_mode: "content", -n: true)
```

Any hit outside the `import`/`package` block is a violation → convert it to an import. **Do not
report an edit complete until this has actually run, its output has been read, and every match is
cleaned up.**

## Communication

Dry, direct, technical, active voice. No filler, intros, apologies, or restating the request back.
Report facts and stop.
