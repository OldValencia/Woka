# Native image metadata

What GraalVM's `native-image` is told Woka reaches at run time — reflection, JNI, resources,
FFM calls — beyond what it can see by itself. Read by `:desktopApp:nativeImage` (ST-004).

| Folder | What it holds | Where it comes from |
|---|---|---|
| `app/` | what the app touched while the tracing agent watched it: six scripted scenarios and manual sessions with real input | `native-image-agent` over app runs |
| `tests/` | what the whole `:shared` test suite touched, test-only types removed | `native-image-agent` over `:shared:jvmTest` on GraalVM's java |
| *(generated)* `desktopApp/build/native-image/rules/` | everything the dependencies can reach, derived from them — JNI from the DLLs, JavaCPP pointers, Swing UI, FFM descriptors from the sources | `:desktopApp:nativeImageRules`, every build |

The two committed folders are snapshots and go stale as the code moves; ST-006 adds the Gradle
tasks that refresh them. Until then they were made by `build\native-image\scripts` (outside Git).

**A gap shows up as a hang or a silently failing feature, never at build time.** Build with
`-Pwoka.native.strict=true` and run the image with `-XX:MissingRegistrationReportingMode=Warn`:
every missing registration is printed. A clean log over a manual pass is the bar. Why the rules
exist and what each covers: `ai/docs/features/pending/startup-and-background/startup-and-background.md`,
section "Native Image — what is known".
