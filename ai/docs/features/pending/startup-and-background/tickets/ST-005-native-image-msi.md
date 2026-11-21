# ST-005 — An MSI that installs the native image

| | |
|---|---|
| **Order** | after ST-004 |
| **Depends on** | ST-004 |
| **Feature** | [Startup and background](../startup-and-background.md) |

**Goal.** `./gradlew.bat :desktopApp:packageMsi -Pwoka.native=true` produces an MSI that installs
the native image as Woka — same product, shortcuts, file associations and icon as the JVM MSI —
with GraalVM's license notices inside.

**Why now.** The image only reaches a user, the author's own full pass included, through an
installer; and GraalVM's terms require its notices to travel with it.

**Settled 2026-11-21 by experiment:** jpackage `--app-image` accepts the image folder when it holds
`app\.jpackage.xml`; an MSI built that way with the bundled WiX 3.11, extracted with `msiexec /a`,
opened a JPEG and played a video. No hand-written WiX.

## Steps
1. **Icon and version in `Woka.exe`.** `NativeImageBuild.kt` writes `woka.rc` — `IDI_ICON1 ICON`
   pointing at `desktopApp/src/main/resources/icons/woka.ico` and a `VERSIONINFO` with
   `app.version`, product `Woka`, company `OldValencia` — compiles it with `rc.exe` inside the same
   `vcvars64` shell, and passes the `.res` to the linker (`-H:NativeLinkerOption=<res>`). Today the
   exe has no icon, so a shortcut to it would show the generic one.
2. **`app\.jpackage.xml`** in `nativeAppImage`, copied from the distributable's `app\` — the file
   jpackage checks an app image by.
3. **License notices** in `nativeAppImage`: GraalVM's `LICENSE.txt` and `NOTICE.txt`, and from its
   `legal\` the folders of the modules whose code the image carries (`java.base`, `java.desktop`,
   `java.datatransfer`, `java.xml`, `jdk.unsupported`, …), under `licenses\graalvm\` — beside the
   existing `resources\licenses\`.
4. **`packageMsi` takes the native image when asked**: in `native-image.gradle.kts`, with
   `woka.native=true`, `packageMsi` depends on `nativeAppImage` and its `appImage` is that folder
   instead of `aotAppImage`'s. Without the property nothing changes. The upgrade UUID stays the same:
   the native MSI replaces an installed JVM Woka and the other way round.

## Files
`buildSrc/src/main/kotlin/nativeimage/NativeImageBuild.kt` (edit),
`buildSrc/src/main/kotlin/nativeimage/NativeResources.kt` (new — the `.rc` writer and `rc.exe` call,
if `NativeImageBuild.kt` would pass 150 lines), `desktopApp/native-image.gradle.kts` (edit).

## Done when
- `packageMsi -Pwoka.native=true` produces `Woka-<version>.msi`; `msiexec /a` into a folder gives
  `Woka.exe` with Woka's icon in Explorer, version `app.version` in its properties,
  `licenses\graalvm\LICENSE.txt` present, and the extracted app opens a JPEG and plays a video.
- Installed per user on the author's machine: the Start-menu shortcut and a double-clicked `.jpg`
  open the native Woka; uninstall removes it. (By the author; reported back.)
- `packageMsi` without the property still builds the JVM MSI with its AOT cache, byte-for-byte the
  same inputs as before.
- `./gradlew.bat :shared:build` green.

## Progress

**2026-11-21.** Steps 1–4 in. Deviation from the plan: the `packageMsi` switch sits in
`desktopApp/build.gradle.kts`, beside the AOT wiring it replaces — a script plugin applied before
that `afterEvaluate` would have been overridden by it, and moving the AOT wiring was a larger change.
`NativeResources.kt` holds the `.rc` writer; `runNativeImage` compiles it with `rc.exe` in the same
`vcvars64` shell.

`packageMsi -Pwoka.native=true` built `Woka-1.0.8.msi` (318 MB) in 3 min 3 s. Extracted with
`msiexec /a`: `Woka.exe` carries Woka's icon and version 1.0.8 / OldValencia, `licenses\graalvm\`
holds LICENSE, NOTICE and `legal\`; with isolated storage a JPEG drew at 310 ms and a video at
440–462 ms warm (2.2 s on the first launch after extraction), no stderr. `--dry-run` without the
property still runs `trainAotCache` → `aotAppImage` → `packageMsi`.

**Done 2026-11-21.** The author installed the MSI, used the app ("everything works perfectly"),
and uninstalled it; observed 113 MB of RAM idle with the window open and nothing loaded, and almost
no load on a heavy file. `buildSrc` tests 5/0; `:shared:build` 1746 / 0 on the third run — the two
before died in a native FFmpeg crash in `RenderSoundSyncTest`, unrelated to this ticket and seen
before it (`hs_err` of 2026-11-20 20:05); recorded in the backlog.
