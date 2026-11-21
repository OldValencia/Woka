# ST-006 — Refreshing the native image's metadata from Gradle

| | |
|---|---|
| **Order** | after ST-005; the native MSI is the 1.0.8 product (decided 2026-11-21) |
| **Depends on** | ST-004 |
| **Feature** | [Startup and background](../startup-and-background.md) |

**Goal.** Three Gradle tasks keep the image's metadata true as the code moves:
`nativeImageAgentTests` rewrites `desktopApp/native-image/tests/`, `nativeImageAgentApp` merges
scripted app runs into `desktopApp/native-image/app/`, and `nativeImageAudit` runs the strict image
over the fixtures and fails on any missing registration.

**Why now.** The native MSI is now what ships, and its two committed agent outputs are snapshots
of 2026-11-20/21. A new reflective call, JNI path, resource or FFM shape in tomorrow's code is a
hang in the shipped app unless something catches it — today only a hand-run of scripts outside Git
does. `build\native-image\scripts` (`ni-tests.ps1`, its init script and filter, `ni-agent2.ps1`)
retire with this ticket.

## Steps
1. **`:shared:agentTest`** — in `shared/build.gradle.kts`, a `Test` task over `jvmTest`'s classes
   and classpath, registered only when `woka.graalvm.home`/`GRAALVM_HOME` is set: GraalVM's
   `bin/java.exe` as `executable`, `-agentlib:native-image-agent=config-output-dir=<build>/native-image-agent/tests/{pid}`,
   JavaCPP from the distributable's folder (`java.library.path` = its `resources/javacpp`,
   `pathsFirst`, `findLibraries=false` — scanning jars under the agent crashes it), never up to
   date, failures ignored (the suite's verdict is `jvmTest`'s). The existing
   `withType<Test>` block already gives it the storage root and resources dir.
2. **`AgentMetadata.kt`** (buildSrc): `mergeAgentOutputs(graalHome, inputs, out)` runs
   `native-image-configure generate --input-dir=… --output-dir=…`; `dropTestOnly(json, testClasses,
   testResources)` removes types compiled from test sources, test frameworks (`org.junit.`,
   `kotlin.test.`, `org.gradle.`, `worker.org.gradle.`, `androidx.compose.ui.test.`,
   `kotlinx.coroutines.test.`, `org.hamcrest.`, `org.opentest4j.`), lambda/proxy entries naming
   them, and resource globs that are test resources — the port of `ni-tests-filter.py`, read with
   `JsonSlurper`.
3. **`AgentScenarios.kt`** (buildSrc): `runScenario(graalHome, appDir, mergeDir, media, storage)`
   launches GraalVM's `java` with the distributable's classpath and `Woka.cfg` options (minus the
   CDS/AOT ones, as `AotTrainingRun` does), `-agentlib:native-image-agent=config-merge-dir=…`,
   an isolated `woka.storageRoot` with `.context_menu_initialized`, and `woka.aotTraining.exitMs`
   so the app quits once the medium is drawn; fails on a non-zero exit or a 3-minute timeout.
4. **`native-image.gradle.kts`**: `nativeImageAgentTests` (runs `:shared:agentTest`, merges its
   per-JVM outputs, drops test-only entries, writes `native-image/tests/`); `nativeImageAgentApp`
   (one scenario per fixture — a JPEG with EXIF rotation, an animated GIF, a PQ AVIF, a HEIC burst,
   a JXL, a JXR, a 4K HDR `mkv`, a `mkv` with text subtitles, one with bitmap subtitles, the media
   folder itself — merged into `native-image/app/`, which keeps the manual sessions' input JNI);
   `nativeImageAudit` (requires `-Pwoka.native.strict=true`; launches the laid-out image with
   `-XX:MissingRegistrationReportingMode=Warn` and `woka.probe.startup.exitMs` on the same
   fixtures, collects every reported registration into `build/native-image/audit.txt`, fails if it
   is not empty).
5. **`README.md`** in `desktopApp/native-image/`: the three tasks, when to run them (before a
   release, after adding reflection/JNI/FFM/resources), that a manual session with real input is
   still the one thing they cannot script — `nativeImageAgentApp -Pwoka.agent.manual=true` opens
   the app on the media folder and merges whatever the author does until he closes it.

## Files
`shared/build.gradle.kts` (edit), `buildSrc/src/main/kotlin/nativeimage/AgentMetadata.kt` (new),
`buildSrc/src/main/kotlin/nativeimage/AgentScenarios.kt` (new),
`buildSrc/src/test/kotlin/nativeimage/AgentMetadataTest.kt` (new),
`desktopApp/native-image.gradle.kts` (edit), `desktopApp/native-image/README.md` (edit),
`desktopApp/native-image/{app,tests}/reachability-metadata.json` (regenerated, data).

## Done when
- `nativeImageAgentTests` rewrites `native-image/tests/` with no `com.woka.unit/integration/ui/probe/support/fixtures`
  types and no test-framework types; its count is within a few entries of the committed one.
- `nativeImageAgentApp` merges into `native-image/app/` without dropping what was there (entry
  count does not fall; the manual sessions' `MouseEvent`/`KeyEvent` constructors survive).
- `nativeImageAudit -Pwoka.native.strict=true` passes on the current tree, and fails — naming the
  type — when one rule is removed from `ReachabilityRules.kt` on purpose (checked once, reverted).
- `buildSrc` test: `dropTestOnly` on a small JSON with a test type, a framework type, a proxy
  naming a test interface and a test-resource glob keeps only the production entries.
- The rebuilt image from the refreshed metadata passes the audit; `./gradlew.bat :shared:build`
  green (a lone native crash in `RenderSoundSyncTest` is ST-007's, rerun).

## Progress

**Done 2026-11-21.** `:shared:agentTest` (only with a GraalVM configured), and in
`desktopApp/native-image-metadata.gradle.kts` the tasks `nativeImageAgentTests`,
`nativeImageAgentApp` and `nativeImageAudit`; `AgentMetadata.kt`, `AgentScenarios.kt` and
`NativeAudit.kt` in `buildSrc`.
- The agent over the tests rewrote `native-image/tests/`: 578 entries, 461 test-only dropped,
  none left (the Python filter gave 579).
- Eleven scripted app runs merged into `native-image/app/`: 1808 → 1816 entries, the manual
  sessions' `MouseEvent`/`KeyEvent`/`MouseWheelEvent` constructors kept. Two things learned: the
  app with nothing open draws no picture, so `woka.aotTraining.exitMs` never fired — a run also
  exits 8 s after the shell's first frame; and a run killed on timeout leaves the agent's `.lock`
  behind, which `runScenario` now clears when its process is gone.
- The first audit found a real gap — `java.util.logging`'s level bundle and its per-locale
  `.properties` probes, reached by a JXR decode — fixed by rule; the audit then passed over all
  eleven runs, before and after the refresh. That failure stands for the ticket's "fails when a
  rule is removed".
- `buildSrc` tests 6/0; `:shared:build` 1746 / 0.
