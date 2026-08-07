# TC-SYS — OS integration

Everything that leaves the process: the CLI entry point, hardware keys, the system media session,
native dialogs, the clipboard.

---

### TC-SYS-01 — opening a file from the shell

**Area:** CLI routing (`--args="[path]"`)
**Preconditions:** A folder of images that is **not** currently linked.
**Steps:**
1. Run `./gradlew.bat :desktopApp:run --args="D:\path\to\photo.jpg"` from a terminal.
2. Press `X` (close) in the viewer.
3. Accept the offer to pin the folder.
4. Repeat step 1 with a video path, then with the folder itself, then with a path that does not
   exist, then with an unquoted path containing spaces.
**Expected:** the app opens straight into the viewer on that file, not on Home. `X` leads to Folder
View for the *parent* directory, with the offer to pin it. Accepting adds it; the folder is there on
the next launch. A folder argument opens Folder View; a path that is not there opens Home.
**Why this is not automated:** process launch with arguments, and the routing decision it drives.
The table itself *is* asserted — `StartupRouteTest` covers every row of it — but nothing in-process
can check that `main` is handed the arguments and that the window comes up on the right screen.

**Defect found 2026-08-07 (dbad022), fixed:** there was no CLI routing at all. `main()` took no
`args` and `Router` hard-coded `[Screen.Home]`, so every launch opened Home whatever was passed. Now
`initKoin(launchArgs)` resolves `startupStack(...)` before the first composition, and a launched
folder carries `offerPin`, which is what puts the "Pin … to Home?" offer in the dock once the viewer
is closed.

<details>
<summary>The original run's log — the build succeeded and the app opened Home</summary>

```log
09:54:00: Executing ':desktopApp:run --args="C:\Users\OldValencia\Pictures\Screenshots\Screenshot 2026-01-04 215250.png"'…

Calculating task graph as no cached configuration is available for tasks: :desktopApp:run --args=C:\Users\OldValencia\Pictures\Screenshots\Screenshot 2026-01-04 215250.png
Type-safe project accessors is an incubating feature.
> Task :desktopApp:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:convertXmlValueResourcesForJvmMain NO-SOURCE
> Task :shared:generateComposeResClass UP-TO-DATE
> Task :shared:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :desktopApp:generateComposeResClass UP-TO-DATE
> Task :desktopApp:prepareAppResources NO-SOURCE
> Task :desktopApp:convertXmlValueResourcesForMain NO-SOURCE
> Task :shared:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :shared:kmpPartiallyResolvedDependenciesChecker
> Task :desktopApp:copyNonXmlValueResourcesForMain NO-SOURCE
> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:copyNonXmlValueResourcesForJvmMain NO-SOURCE

> Task :shared:checkJvmMainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-jvm:3.0.4
    \--- org.jetbrains.skiko:skiko:0.8.4 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :shared:dependencies  --configuration jvmRuntimeClasspath`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :shared:prepareComposeResourcesTaskForJvmMain NO-SOURCE
> Task :desktopApp:prepareComposeResourcesTaskForMain NO-SOURCE
> Task :shared:generateResourceAccessorsForJvmMain NO-SOURCE
> Task :shared:copyNonXmlValueResourcesForCommonMain UP-TO-DATE
> Task :desktopApp:assembleMainResources UP-TO-DATE
> Task :desktopApp:generateResourceAccessorsForMain NO-SOURCE
> Task :desktopApp:generateActualResourceCollectorsForMain UP-TO-DATE
> Task :desktopApp:processResources UP-TO-DATE
> Task :shared:prepareComposeResourcesTaskForCommonMain UP-TO-DATE
> Task :shared:generateResourceAccessorsForCommonMain UP-TO-DATE
> Task :shared:assembleJvmMainResources UP-TO-DATE
> Task :shared:generateActualResourceCollectorsForJvmMain UP-TO-DATE
> Task :shared:jvmProcessResources UP-TO-DATE
> Task :shared:processJvmMainResources SKIPPED
> Task :shared:compileKotlinJvm UP-TO-DATE
> Task :shared:compileJvmMainJava NO-SOURCE
> Task :shared:jvmMainClasses UP-TO-DATE
> Task :shared:jvmJar UP-TO-DATE
> Task :desktopApp:compileKotlin UP-TO-DATE
> Task :desktopApp:compileJava NO-SOURCE
> Task :desktopApp:classes UP-TO-DATE
> Task :desktopApp:jar UP-TO-DATE
> Task :desktopApp:run

[Incubating] Problems report is available at: file:///D:/Projects/Woka/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.6.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 7s
19 actionable tasks: 3 executed, 16 up-to-date
Configuration cache entry stored.
09:54:07: Execution finished ':desktopApp:run --args="C:\Users\OldValencia\Pictures\Screenshots\Screenshot 2026-01-04 215250.png"'.
```

</details>

---

### TC-SYS-02 — hardware media keys, on and off

**Area:** OS media session (§4), `systemMediaControls`
**Preconditions:** a keyboard with media keys, or a headset with them. A video playing.
**Steps:**
1. With `systemMediaControls` on, press play/pause, next and previous.
2. Start playback in another app (a browser tab). Press play/pause.
3. Turn `systemMediaControls` off in Settings and press the keys again.
**Expected:** with the setting on, the keys control Woka while Woka is the active session, and the OS
routes them to whichever app is playing — Woka never steals them from the other app. With it off,
Woka ignores them entirely and the other app keeps them.
**Why this is not automated:** the OS decides the routing; that decision is the thing under test.

---

### TC-SYS-03 — the Now Playing panel

**Retired 2026-08-07:** the OS Now Playing session was built and then dropped. SMTC registered and
showed the title and the poster frame, but refused every attempt to register its `ButtonPressed`
handler with `CO_E_NOT_SUPPORTED` — a panel that displays and does not respond is worse than no
panel. `SystemMediaControlsFactory`'s KDoc and `implemented/system-media-controls.md` record what was
tried. The id is kept and never reused; TC-SYS-02 covers what survived, the focus-bound media keys.

**Area:** OS media session (§4)
**Preconditions:** a video with a recognisable name, playing. See
`ai/docs/features/implemented/system-media-controls.md`.
**Steps:**
1. Open the Windows volume/media flyout while playing.
2. Pause from the flyout, then resume from it.
3. Switch to another file and check the panel again.
4. Close the viewer.
**Expected:** title and artwork appear and match the file. State follows Woka both ways. Switching
files updates the panel. Closing the viewer removes the session rather than leaving a stale one.
**Why this is not automated:** the panel is an OS surface rendered outside the app.

---

### TC-SYS-04 — the native folder picker

**Area:** linked folders, AWT `FileDialog`
**Preconditions:** Home screen.
**Steps:**
1. Add a linked folder through the picker.
2. Cancel the picker without choosing.
3. Pick a path with non-ASCII characters and spaces.
4. Pick a network or removable path if one is available.
**Expected:** a native Windows dialog, not a Compose one. Cancel adds nothing. The non-ASCII path is
stored and re-read correctly and its folder card shows the right name.
**Why this is not automated:** a modal OS dialog; the suite must not open one.

---

### TC-SYS-05 — fullscreen

**Area:** viewer, window
**Preconditions:** a video playing, window not maximised, zoomed past Fit.
**Steps:**
1. Enter fullscreen.
2. Change the zoom while fullscreen.
3. Leave fullscreen with the same control, re-enter, then leave with `Esc`.
**Expected:** fullscreen covers the screen with no title bar and no taskbar. Zoom and pan survive
both transitions rather than resetting. `Esc` leaves fullscreen and does *not* also close the viewer
— one key, one effect.
**Why this is not automated:** an OS window-state change, and the `Esc` case is about an event not
reaching a second handler.

---

### TC-SYS-06 — copy to clipboard, and what the file actions mean

**Area:** viewer and grid, clipboard
**Preconditions:** an image open in the viewer. An Explorer window and a text editor open. The
viewer's top toolbar no longer carries a Copy button — the copy is taken from the media's right-click
menu or with `Ctrl+C`. This is the **main** viewer: the fullscreen/PiP window has neither the
right-click file actions nor Info and Edit in its toolbar (TC-PIP-06, TC-PIP-07).
**Steps:**
1. Copy the image from the viewer: right-click the picture → Copy, and again with `Ctrl+C`.
2. Paste into Explorer in another folder.
3. Copy the File Info text from the info dialog.
4. Paste into the text editor.
5. In Folder View, right-click a file → **Copy**, then paste into Explorer.
6. Right-click the same file → **Duplicate**, choose a folder, and check the file arrives there.
7. Right-click it → **Move**, choose a folder, and check the original is gone.
**Expected:** every image paste produces a real file in the target folder. The info paste produces
the full plaintext block — name, path, size, date, streams — not a truncated or single-line version.
In the grid, Copy means the clipboard and only the clipboard, Duplicate is the one that asks where
to put a copy, and Move still moves.
**Why this is not automated:** the system clipboard is shared machine state; a test writing to it
would clobber whatever the person running it had copied.

**Changed 2026-08-07:** the grid's context menu used to call copy-into-a-chosen-folder "Copy", which
is not what Copy means anywhere else on the desktop. Copy is now the clipboard, and the old action
is Duplicate. The transferable moved to `core/utils/copyFilesToClipboard`, so the grid and the viewer
share one implementation. The select-mode dock pills are untouched and keep their own meanings.