# Woka — Claude Code Entry Point

Minimalist, fast, Apple-HIG-styled **Desktop** media viewer and editor.
Kotlin Multiplatform / Compose for Desktop (JVM only).

Working mandates (how you edit, verify and report) are imported from here — read them as part of
this file: @./.claude/AGENTS.md

---

## 1. Where the knowledge lives (`ai/`)

The `ai/` folder is the project's design brain. **Read the relevant doc before planning or
implementing a feature** — do not invent product behaviour that contradicts it.

| Path | What it holds | When to read it |
| --- | --- | --- |
| `ai/docs/general_idea.md` | Product vision, core philosophy, "Linked Folders" paradigm, use cases, technical pillars | Before any feature/UX decision |
| `ai/docs/roadmap.md` | Phase-by-phase development plan, CLI routing (`Woka.exe [path]`), what ships when | Before starting new work; to know what phase we're in |
| `ai/docs/features/pending/` | Approved plans **not yet implemented** | Pick up work from here; new plans are written here |
| `ai/docs/features/implemented/` | Plans for features already shipped, with assets | Reference for how an existing subsystem was built |
| `ai/mockups/design/` | HTML design mockups (`FINAL_design_mockup.html`) | Source of truth for visual style |
| `ai/mockups/logos/` | Logo iterations (`v3`–`v5`, `final`) | Branding/icon work |

Rules for `ai/`:
- New feature plans go to `ai/docs/features/pending/<feature-name>.md` — format defined by the
  `plan-creation` skill (`.claude/skills/plan-creation/SKILL.md`).
- When a plan is fully implemented, **move** the file `pending/` → `implemented/` (do not copy).
- Update `ai/docs/roadmap.md` when a phase changes state.
- Screenshots/renders belong in the sibling `assets/` folder of the doc that uses them.

## 2. Where the rules live

| Path | Purpose |
| --- | --- |
| `CLAUDE.md` (this file) | Auto-loaded map: docs, repo layout, commands |
| `.claude/AGENTS.md` | Full working mandates (code style, workflow, Compose rules) — imported above |
| `.claude/skills/plan-creation/SKILL.md` | How to write a feature plan |
| `.claude/settings.local.json` | Local permission allowlist |

## 3. Repo layout

```
shared/                     ← all app code lives here
  src/commonMain/kotlin/com/woka/
    App.kt                  ← composable root
    navigation/Router.kt    ← screen routing (Home / Folder / Viewer)
    di/Koin.kt              ← DI module wiring
    data/                   ← SettingsRepository, LocalMediaRepository, WokaSettings
    ui/                     ← HomeScreen/HomeViewModel, FolderScreen/FolderViewModel
    ui/components/          ← reusable HIG widgets (dock, cards, top bar, overlays)
    ui/theme/Theme.kt       ← colors, shapes, typography
    ui/icons/Icons.kt       ← vector icons
    ui/viewer/              ← common viewer surface + player models
    utils/                  ← DesktopUtils, VideoThumbnail (expect)
  src/jvmMain/kotlin/com/woka/
    di/PlatformModule.kt    ← JVM-side DI (actual impls)
    data/SettingsRepositoryImpl.kt
    ui/viewer/              ← the real viewer: MediaViewer*, Subtitle*, Zoom*, Pip*, Hdr*
    ui/viewer/player/       ← JavaCV/FFmpeg decode, audio, seek, tracks, subtitles
    ui/components/dialogs/  ← AWT FileDialog wrappers
  src/commonTest, src/jvmTest
desktopApp/                 ← thin launcher module (main(), window, CLI args)
ai/                         ← docs & mockups (see §1)
logo/                       ← shipped app logo assets
```

Package root is `com.woka`. `expect`/`actual` split: declare in `commonMain`, implement in
`jvmMain` (e.g. `VideoThumbnail.kt` / `VideoThumbnail.jvm.kt`).

## 4. Stack

- **UI:** Compose Multiplatform (Desktop), Skia rendering
- **Arch:** MVVM — `ViewModel` exposes `StateFlow`, UI observes. No Compose types in ViewModels.
- **DI:** Koin. Resolve dependencies, never `new` them inline.
- **Media:** JavaCV / Bytedeco FFmpeg for video decode, audio, probing, subtitles
- **Persistence:** `kotlinx.serialization` JSON at
  `user.home/Documents/OldValencia/Woka/settings.json`. No Room, no SQL, no cloud.
- **Targets:** JVM Desktop only — no Android, no iOS, no web.

## 5. Commands

```powershell
./gradlew.bat :shared:compileKotlinJvm -q   # fastest correctness check after edits
./gradlew.bat :shared:build                 # full shared module build + tests
./gradlew.bat :desktopApp:run               # run the app
./gradlew.bat :desktopApp:hotRun --auto     # run with hot reload
```

Run builds via the PowerShell tool with `run_in_background: true`, then **wait for the result**
before reporting anything. A failed build is reported as a failed build.
