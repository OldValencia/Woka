# ST-004 — The native image built by Gradle

| | |
|---|---|
| **Order** | after ST-001; before ST-005 (the MSI packages what this builds) |
| **Depends on** | ST-001 |
| **Feature** | [Startup and background](../startup-and-background.md) |

**Goal.** `./gradlew.bat :desktopApp:nativeAppImage` builds the native image and lays out a folder
whose `Woka.exe` runs on a double click — the session scripts in `build\native-image\scripts` stop
being how the image is made.

**Why now.** Every image so far came from scripts that live outside Git, hard-code paths to one
machine and depend on Python. The MSI (ST-005), the memory comparison and a PGO trial all need an
image anyone can rebuild from the repository with one command.

## Steps

1. **`buildSrc`** (new): `buildSrc/build.gradle.kts` with `kotlin-dsl` and `mavenCentral()`;
   `junit` + `kotlin-test` for its tests. Sources under `buildSrc/src/main/kotlin/nativeimage/`.
2. **`ClassFiles.kt`**: `readClassHeader(bytes): ClassHeader?` (name, super, access flags — the
   constant-pool walk of `rules-meta.py`'s `class_header`); `jarClasses(jars): Map<String, ClassHeader>`;
   `jdkClasses(graalHome): Set<String>` through the `jrt:/` file system opened on GraalVM's home;
   `jdkSupers(graalHome)` from `jmods/java.base`, `java.desktop`, `java.datatransfer` (a jmod is a zip
   behind a 4-byte header).
3. **`NativeLibraryScan.kt`**: `jniClasses(dlls, known, supers): Set<String>` — string literals
   matching `(java|javax|sun|jdk|com/sun|org/jetbrains|org/bytedeco)/…` plus classes decoded from
   exported `Java_*` symbols (`_1` → `_`, `_0xxxx` → char, `__` ends the name, last token is the
   method), plus every `java.awt.event` class, plus every superclass of each. The DLL set is the
   distributable's skiko DLL and `resources\javacpp\jni*.dll`, and from GraalVM's `bin\` exactly
   `awt fontmanager freetype javaaccessbridge javajpeg jawt jsound lcms mlib_image` — what the
   image ships byte for byte, checked 2026-11-21; its `java.dll`/`jvm.dll` are its own and not scanned.
4. **`FfmDescriptors.kt`**: `ffmDescriptors(kotlinSources): Pair<Set<Shape>, Set<Shape>>` — every
   literal `FunctionDescriptor.of/ofVoid(…)` (as written and with a leading `void*`, for
   `WinRt.call`), `callChecked(…, listOf(…))` as `jint(void*, …)`, `status("…", …)` as `jint(…)`;
   spreads and struct layouts skipped; literals in a file containing `upcallStub` also as upcalls.
5. **`ReachabilityRules.kt`**: `writeRules(graalHome, appDir, sources, out)` — the JNI set (all
   declared members, `jniAccessible`), `unsafeAllocated` on concrete `org.bytedeco.javacpp.Pointer`
   subclasses, all methods of `Windows*/Basic*/Metal*UI` and `WindowsLookAndFeel.<init>`,
   `java.rmi.MarshalledObject`/`Remote`, `coalesceEvents` on jar classes under `java.awt.`/`javax.swing.`,
   field `label` on `BaseContinuationImpl` subclasses, all `VarHandleSegmentAs*` methods, the FFM
   shapes, resources `composeResources/**`, PDFBox/FontBox data, `java.desktop`'s Swing plaf files
   and its three bundles — one `reachability-metadata.json`. Output must equal
   `config-rules\reachability-metadata.json` from `rules-meta.py` up to ordering (845 JNI, 295
   pointers, 104 UI, 74 components, 1363 continuations, 14 VarHandles, 43 + 2 FFM).
6. **`NativeImageBuild.kt`**: `graalHome(project)` from Gradle property `woka.graalvm.home`, else
   `GRAALVM_HOME`, else a clear failure naming both; `vcvars()` through `vswhere -latest -prerelease`;
   `runNativeImage(...)` writes an `@argfile` (paths with doubled backslashes) and a `.cmd` that
   prepends System32 to `PATH`, calls `vcvars64.bat`, then `native-image.cmd @argfile`; non-zero exit
   fails the task with the log's tail. Arguments: the distributable's classpath from its `Woka.cfg`,
   `-o Woka`, `--no-fallback`, `-march=compatibility`, `-H:+UnlockExperimentalVMOptions`,
   `-H:ConfigurationFileDirectories=` the two committed sources and the generated rules,
   `-H:ExcludeResources=.*\.(dll|so|dylib)$`, `-H:+IncludeAllLocales`, `/SUBSYSTEM:WINDOWS` and
   `/ENTRY:mainCRTStartup` as linker options, `--enable-native-access=ALL-UNNAMED`,
   `-R:MaxHeapSize=1536m`, `-J-Xmx16g`; `--exact-reachability-metadata` when `woka.native.strict`.
7. **Committed metadata**: `desktopApp/native-image/app/reachability-metadata.json` and
   `desktopApp/native-image/tests/reachability-metadata.json`, copied from `build\native-image\config`
   and `config-tests`, with a `README.md` beside them saying what each is and that ST-006 refreshes them.
8. **`desktopApp/native-image.gradle.kts`** (new, applied from `build.gradle.kts`, Windows only):
   `nativeImageRules` (inputs: distributable, GraalVM home, `shared/src/jvmMain/kotlin`; output
   `build/native-image/rules`), `nativeImage` (depends on both; output `build/native-image/image`),
   `nativeAppImage` (`Sync` into `build/compose/binaries/main/native-image/Woka`: the image's
   `Woka.exe` and DLLs, `jawt.dll` again under `bin\`, GraalVM's `lib\fontconfig.bfc`,
   `psfontj2d.properties`, `tzdb.dat`, `tzmappings`, the distributable's skiko DLL, `icudtl.dat` and
   `resources\`). `NativeImageOptions` reads exactly this layout.

## Files
`buildSrc/build.gradle.kts` (new), `buildSrc/src/main/kotlin/nativeimage/ClassFiles.kt` (new),
`buildSrc/src/main/kotlin/nativeimage/NativeLibraryScan.kt` (new),
`buildSrc/src/main/kotlin/nativeimage/FfmDescriptors.kt` (new),
`buildSrc/src/main/kotlin/nativeimage/ReachabilityRules.kt` (new),
`buildSrc/src/main/kotlin/nativeimage/NativeImageBuild.kt` (new),
`buildSrc/src/test/kotlin/nativeimage/NativeImageRulesTest.kt` (new),
`desktopApp/native-image.gradle.kts` (new), `desktopApp/build.gradle.kts` (edit: one `apply`),
`desktopApp/native-image/{app,tests}/reachability-metadata.json` + `README.md` (new, data).

Twelve files against §4a's eight: the smallest slice the author can use is an image Gradle builds,
and it needs the rules generator, the build and the layout together. Each Kotlin file stays under
150 lines; the JSON is data.

## Done when
- `./gradlew.bat :desktopApp:nativeAppImage` on a clean `build\` produces
  `desktopApp/build/compose/binaries/main/native-image/Woka/Woka.exe`; double-clicked, it opens a
  JPEG and plays a video; `run-ni`-style launch with isolated storage writes no stderr.
- The generated rules match `rules-meta.py`'s counts listed in step 5.
- `-Pwoka.native.strict=true` builds; a launch with `-XX:MissingRegistrationReportingMode=Warn`
  over JPEG, video and an embedded subtitle prints nothing.
- Without `woka.graalvm.home`/`GRAALVM_HOME` the task fails with a message naming both; the JVM
  build, `createDistributable` and `packageMsi` are unchanged.
- `buildSrc` tests (`jvmTest`-equivalent `test` task): JNI symbol decoding (`Java_sun_awt_windows_WFileDialogPeer_initIDs`,
  an `_1` name, a `__`-overloaded one), FFM literal parsing (a `WinRt.call` literal, a `callChecked`,
  a `status`), class-header reading on a class from the JDK.
- `./gradlew.bat :shared:build` green; anti-FQN check clean on every new `.kt`.

## Progress

**2026-11-21.** Steps 1–5 done and checked: `:desktopApp:nativeImageRules` (in
`desktopApp/native-image.gradle.kts`, applied from `build.gradle.kts`) writes
`desktopApp/build/native-image/rules/reachability-metadata.json` **identical in content** to
`rules-meta.py`'s — 845 JNI, 2415 types, same resources, 43 + 2 FFM. Found on the way: the image's
`java.dll` and `jvm.dll` are its own, not GraalVM's `bin\` ones (which name 76 extra core classes),
so they are not scanned. Configuration cache: a task action must not touch the script's top-level
`val`s — copy them into locals inside `register { }`.

Steps 6–8 done the same day. `:desktopApp:nativeAppImage` built the image in 2 min 12 s from
the Gradle cache's distributable; from the laid-out folder, with isolated storage: JPEG 187 ms,
video 345 ms, no stderr. With `-Pwoka.native.strict=true` the argfile carries
`--exact-reachability-metadata`, and JPEG and video under `MissingRegistrationReportingMode=Warn`
printed nothing. Without a GraalVM path the task fails with "No GraalVM: set woka.graalvm.home …,
or GRAALVM_HOME". `buildSrc` tests 5/0. `buildSrc/settings.gradle.kts` added: without a root
project name the typesafe accessors warned about checkout-dependent caching.

**Done 2026-11-21.** The author ran the Gradle-built strict image over `D:\Movies` — embedded
subtitles "fast and great", everything else tried worked — and its gap report was empty.
`:shared:build` 1746 tests / 0 failures.
