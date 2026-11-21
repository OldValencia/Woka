# Native image metadata

What GraalVM's `native-image` is told Woka reaches at run time — reflection, JNI, resources,
FFM calls — beyond what it can see by itself. Read by `:desktopApp:nativeImage`.

| Folder | What it holds | Refreshed by |
|---|---|---|
| `app/` | what the app touched under the tracing agent: scripted runs over the fixtures, plus manual sessions with real input (mouse, keys, dialogs) | `:desktopApp:nativeImageAgentApp` — **merges**, so the manual entries stay |
| `tests/` | what the whole `:shared` test suite touched, test-only types removed | `:desktopApp:nativeImageAgentTests` — **rewrites** |
| *(generated)* `desktopApp/build/native-image/rules/` | everything the dependencies can reach, derived from them — JNI from the DLLs, JavaCPP pointers, Swing UI, FFM descriptors parsed from the sources | `:desktopApp:nativeImageRules`, on every build |

All three need GraalVM: `woka.graalvm.home` in `~/.gradle/gradle.properties`, or `GRAALVM_HOME`.

## When to run what

- **Before a release, and after adding reflection, JNI, an FFM call or a resource loaded by
  path:** `nativeImageAgentTests`, then `nativeImageAgentApp`, then the audit below.
- **The audit:** `.\gradlew.bat :desktopApp:nativeImageAudit "-Pwoka.native.strict=true"` builds
  the image with `--exact-reachability-metadata`, opens each fixture in it under
  `-XX:MissingRegistrationReportingMode=Warn`, and fails naming every registration still missing
  (report: `desktopApp/build/native-image/audit/audit.txt`). It found a `java.util.logging`
  bundle the first time it ran.
- **A manual session** — the one thing a script cannot do is use the app:
  `nativeImageAgentApp "-Pwoka.agent.manual=true"` opens it on the fixture folder and merges
  whatever you do until you close it. Worth repeating when input handling or dialogs change.

**A gap shows up as a hang or a silently failing feature, never at build time.** Why the rules
exist and what each covers: `ai/docs/features/implemented/startup-and-background/startup-and-background.md`,
section "Native Image — what is known".
