# Iris Project Guidelines

## 1. Context & Architecture
- **App:** Iris (Compose Multiplatform Desktop media viewer and editor).
- **Targets:** Desktop (JVM) ONLY. No Android, no iOS.
- **Arch:** MVVM (Model-View-ViewModel).
- **DI:** Koin (for dependency injection). No direct instantiations.
- **Data:** Local JSON files for settings (`user.home/Documents/Iris/settings.json`) via `kotlinx.serialization`. No heavy databases like Room.
- **Vision:** See `ai/docs/general_idea.md`.

## 2. Core Mandates (STRICT)
- **Code:** Max 130-150 lines/file. NO code cramming (one statement/lambda per line). **PROACTIVE REFACTORING REQUIRED**: Before editing ANY file, check its size. If it exceeds 130-150 lines, you MUST stop and proactively offer the user to split/refactor the file into smaller components before making any feature additions.
- **NO FQNs:** NO fully qualified names of ANY kind in Kotlin (e.g., NO `androidx.compose.ui.text.style.TextAlign.Left`). YOU MUST IMPORT THEM. NO EXCEPTIONS. Do NOT use inline FQNs as a shortcut to avoid writing import chunks.
- **Scope:** ZERO unprompted changes/refactoring (except for the file size rule above). Fix ONLY what is asked. No autonomy.
- **Builds:** ALWAYS run background build after edits, WAIT for completion. Never report success on failure.
  - Shared: `./gradlew :shared:build` | Desktop run: `./gradlew :desktopApp:run`
- **Tools:** Native tools ONLY (`grep_search`, `view_file`). NO shell commands for file reading. Read files before editing.
- **Compose:** NO `androidx.compose.*` in ViewModels. UI observes VM. `StateFlow` only.
- **Design:** Apple HIG style. NO Material Design. Minimalist, clean lines. 1.dp borders, 8-12.dp corners, `#F2F2F7` (or dark equivalent) background, clean cards. Smooth animations.

## 3. Execution & Interaction
- **Workflow:** Plan -> Summarize -> Wait for explicit permission -> Code -> Build -> Wait for result -> Report -> STOP.
- **Self-Check (Anti-FQN):** After making ANY edits to a `.kt` file, you MUST mechanically use the `grep_search` tool to search for `androidx.`, `java.`, or `io.iris.` in the file you just edited. If you find inline FQNs that you added, you MUST fix them (move to imports) BEFORE telling the user you are done.
- **Communication:** NO FILLER, zero intros/apologies. Dry, direct, technical, active voice.
- **Constraints:** Do NOT modify code without explicit command ("Apply", "Go ahead"). STOP after action/question. If unclear, ASK, don't guess.

## 4. Compose State & Focus
- **State Sync:** Use immutable `data class` for states. NEVER mutate lists/objects directly; always `.copy()`.
- **Desktop APIs:** For things like Directory Pickers, rely on `jvmMain` expect/actual using AWT `FileDialog` or LWJGL tools since this is a pure Desktop app.
