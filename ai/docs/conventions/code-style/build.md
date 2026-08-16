# Build files and dependencies

`gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts` (root, `:shared`,
`:desktopApp`), `gradle.properties`, the wrapper.

## Version catalog

- Every dependency and plugin goes through `libs.versions.toml`. No version literal in a
  `build.gradle.kts`, ever.
- One `[versions]` entry per *release train*, not per artifact: `javacv` and `ffmpeg` are separate
  because Bytedeco versions them separately, `kotlin` covers the compose-compiler plugin because that
  plugin ships with the compiler.
- Alias style: `camelCase` for versions and plugins, `dash-separated` for libraries mirroring the
  artifact (`kotlinx-serialization-json`, `compose-uiTestJUnit4`).

## Choosing a version

- Prefer the **stable** of the line already in use. A `beta`/`alpha` pinned where a stable of the same
  line exists is debt with no upside.
- A pre-release is acceptable only when the whole line is pre-release and the dependency is needed —
  and then the reason is a comment in the catalog.
- Check the authoritative source: `https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml`.
  The `search.maven.org` index lags by months and has reported versions older than the ones this
  project already uses.
- Bump in **groups**, one group per commit, so a regression is attributable. Never one commit that
  moves six libraries.

## Risk classes

| Class | Examples | What it needs |
|---|---|---|
| Mechanical | serialisation, DI, lifecycle patch releases | `:shared:build` green |
| Compiler | Kotlin, Compose plugin, compose-compiler | `:shared:build` + the app launched |
| Behavioural | JavaCV/FFmpeg, Coil, Skia-affecting Compose bumps | the affected paths exercised by hand, and the result dated in the ticket |

A behavioural bump is its own ticket with its own verification. It never rides along with a feature.

## Hygiene

- A dependency nobody imports is deleted. Grep before adding one, and grep again before assuming an
  old one is still used.
- Prefer a small amount of owned code over a dependency for anything the project can express in a
  file — this codebase already owns its components, its icons and its typography.
- `implementation`, not `api`, unless a type genuinely crosses the module boundary.
- The wrapper is updated with `./gradlew.bat wrapper --gradle-version <v> --distribution-type bin`, so
  the checksum is regenerated. Never hand-edit `distributionUrl`.

## JVM settings

`desktopApp`'s `jvmArgs` are load-bearing, not defaults: the young-generation cap is what paces frame
allocation. Read the comment there before touching heap flags, and re-run a long video after any
change.

## Verification

```powershell
./gradlew.bat :shared:compileKotlinJvm -q   # fast check after edits
./gradlew.bat :shared:build                 # + tests
./gradlew.bat :desktopApp:run               # the app itself
```

Run through the PowerShell tool with `run_in_background: true` and **wait** for the result. A failed
build is reported as a failed build.
