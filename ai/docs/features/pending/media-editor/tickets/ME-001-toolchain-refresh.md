# ME-001 — Toolchain and dependency refresh

| | |
|---|---|
| **Order** | 1 |
| **Depends on** | nothing |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Bring the build to current stable versions and delete what is not used, before eight new
packages land on top of it.

**Why now.** Every version bump after this ticket has to be regression-tested against editor code as
well as viewer code. Doing it on a tree with no editor in it isolates the blame.

## Steps

1. `gradle/libs.versions.toml` — bump, one commit-sized group at a time so a failure is attributable:
   - `androidx-lifecycle` `2.11.0-beta01` → `2.11.0` — the stable of the same line; a beta is pinned
     here for no reason;
   - `koin` `4.0.0` → `4.2.2`;
   - `kotlinx-serialization` `1.7.3` → `1.11.0` (the editor's document schema rides on it);
   - `coil3` `3.0.4` → `3.5.0`;
   - `kotlin` `2.4.0` → `2.4.10` — `composeCompiler` follows `kotlin` and needs no separate change.
2. Delete the `filekit` version, both `filekit-*` library entries, and
   `implementation(libs.filekit.compose)` from `shared/build.gradle.kts`. Nothing imports
   `io.github.vinceglb` anywhere in `shared/src` — folder picking goes through the AWT `FileDialog` in
   `core/ui/dialogs/Dialogs.kt`. Grep to prove it before deleting, and again after.
3. Leave `javacv` / `ffmpeg-platform` alone — they move in ME-013, immediately after, with the manual
   video pass re-run. A decode-behaviour change does not ride along with five mechanical bumps.
4. Do **not** bump `material3`. The whole line is pre-release (newest `1.12.0-alpha03`) and the
   dependency is deleted outright in ME-014.
5. `gradle/wrapper/gradle-wrapper.properties` — `9.6.1` → `9.7.0` via
   `./gradlew.bat wrapper --gradle-version 9.7.0 --distribution-type bin`, so the checksum is
   regenerated rather than hand-edited.

Versions were read from `repo1.maven.org` on 2026-08-16. `search.maven.org` lags by months — it
reported `kotlin-stdlib 2.2.0` while this project was already on 2.4.0. Re-check the metadata, not the
search index.

## Files

`gradle/libs.versions.toml`, `shared/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`

## Done when

- `./gradlew.bat :shared:build` green, with the same test count as before the bump;
- `./gradlew.bat :desktopApp:run` opens, a folder lists, a video plays;
- no `filekit` string left in the repo outside `ai/docs/features/implemented/`.
