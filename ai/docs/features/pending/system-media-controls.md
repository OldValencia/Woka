# Plan: System Media Controls (Step 13 of `video-player-enhancements.md`, §4)

Hardware media keys control Woka. ~~And the OS shows a Now Playing entry for it.~~

> **Scope cut, and it is the current state of this plan.** The Now Playing panel is **out**, on
> every platform. Windows SMTC was built to completion — session, title, poster frame, all visible
> in the flyout — and it would not accept a button handler; details in *Why Now Playing was
> dropped*, below. A panel that displays but does not respond is worse than no panel, so the whole
> half went, and the WinRT backend with it. What ships is the media keys, through AWT, while a Woka
> window has focus. **Phases 2 and 3 below are kept as a record of a dead end, not as work to do.**

## Decisions taken up front (do not re-litigate)

- ~~**Scope is keys *and* the Now Playing panel**~~ — **keys only**, per the cut above. The original
  reasoning is worth keeping because it is what made the panel look cheap: mpv does not appear in
  the Windows flyout either, which is why the third-party `MPVMediaControl` plugin exists to bolt
  SMTC on from outside; and **with SMTC registered, Windows itself routes the media keys**, which
  would have dissolved the "who owns the keys" problem rather than adding to it. That second
  argument is exactly what failed to materialise.
- **Windows working is what makes this step Done.** The macOS backend is written in the same pass
  but cannot be run from this machine; it stays marked unverified until there is a Mac.
- **The session exists only while a video is open in the viewer** — windowed or fullscreen. Outside
  the viewer Woka registers nothing with the OS.
- **Hand-rolled FFM on both platforms, not a library.** See below. Now moot on Windows, still the
  route on macOS.

## Measured facts this plan is built on

- **No cross-platform JVM library exists.** `JavaMediaTransportControls` (JMTC) is the only JVM SMTC
  wrapper: Windows + Linux, macOS is `TODO` in its own README, 20 stars, last code push
  2024-08-15. It works by shipping a prebuilt `SMTCAdapter.dll` (x86 / x86-64 only — no arm64) and
  pulls JNA and dbus-java in behind it.
- **JNativeHook is out.** Its native layer, libuiohook, does not detect macOS media keys
  (`kwhat/libuiohook#28`) and reports keyCode 0 for them on Linux (`kwhat/jnativehook#182`).
- **The JDK here is 25**, so the finalized FFM API is available: OS APIs are callable straight from
  Kotlin with no JNI, no JNA and **no native toolchain** — which is the only reason the macOS half
  can be written from a Windows machine at all.
- **The platforms are mirror images.** On Windows the keys are cheap (`RegisterHotKey`) and the panel
  is expensive (WinRT is COM: vtable dispatch plus a callback object with its own vtable). On macOS
  it is reversed — `MPRemoteCommandCenter` gives keys *and* Now Playing in one go and needs no
  Accessibility permission, while getting only the keys would mean a `CGEventTap` that does.

Since macOS must be hand-rolled regardless, JMTC would buy one platform at the price of a second
mechanism, a prebuilt binary from a small stale repo, and two dependency trees. Phase 2 is therefore
a bounded spike, with JMTC named as the fallback for the Windows backend alone.

## Phase 0: Measure what AWT already delivers — ✅ done, and it changed the plan

**AWT delivers them.** `keyCode` and `extendedKeyCode` are both 0 — Java never defined virtual key
codes for media keys — but the Windows platform code arrives in `rawCode`:

| `rawCode` | Key |
|---|---|
| 179 (`0xB3`) | Play/Pause |
| 176 (`0xB0`) | Next track |
| 177 (`0xB1`) | Previous track |

So the focused-window case needs **no native code at all**, and it is exactly the scope decided
above: a Toolkit `AWTEventListener` only ever sees events dispatched to Woka's own windows, so keys
are picked up while one of them has focus and are left alone otherwise. It also covers the
fullscreen player for free, that being a second `Window` of the same app.

Shipped as `system/AwtMediaKeyControls.kt`, a plain `SystemMediaControls` backend, matched on
`rawCode` parsed out of `KeyEvent.paramString()` — the field is package-private with no accessor,
and reflection would demand `--add-opens java.desktop/java.awt.event=ALL-UNNAMED` at every launch.
A 250 ms same-command guard absorbs a doubled key press, which would otherwise toggle play/pause
twice and look like a dead key.

**What this does to Phase 2:** SMTC is no longer what makes the keys work — it is now only the Now
Playing panel plus keys while Woka is *not* focused. It also becomes a **replacement** for this
backend, not an addition: with both live, a focused press would arrive twice.

*Original phase text, for the record:*

Compose defines `Key.MediaPlayPause` / `MediaNext` / `MediaPrevious`. If AWT maps the hardware keys
on Windows, the focused-window case costs five lines and the native work only has to cover the
unfocused case. Settle it by measurement, not by assumption.

- New `system/MediaKeyProbe.kt`, marked `TEMPORARY (§4 Step 13)`: an `AWTEventListener` on
  `KEY_EVENT_MASK` printing `keyCode` / `extendedKeyCode` / `paramString()` for every key press.
- Press Play/Pause, Next, Prev with the viewer focused; record the result in this file; delete the
  probe and grep to prove it is gone.

**Files:** new `system/MediaKeyProbe.kt`

## Phase 1: The seam — everything platform-independent

Built and verified against a logging backend, so the native code slots into a contract already
proven.

- New `system/SystemMediaControls.kt`: `MediaCommand { PlayPause, Play, Pause, Stop, Next, Previous }`,
  `MediaSessionInfo(title, durationMs, hasNext, hasPrevious)`,
  `MediaPlaybackState(isPlaying, positionMs)`, and the backend interface
  (`enable` / `setSession` / `setPlayback` / `disable` / `dispose` + `onCommand`).
- New `system/SystemMediaSession.kt` — **a process-wide singleton, and it has to be.** Going
  fullscreen closes `MediaViewerScreen` and opens a separate `Window` over the *same*
  `VideoPlayerState` (`PipManager`), so a session owned by either composition would die on the
  handover. Ownership is a **token**: `attach` returns one, `detach(token)` is ignored unless it is
  still current, which makes the handover safe in either order.
- Commands arrive on an OS thread; `SystemMediaSession` marshals them onto the EDT with
  `EventQueue.invokeLater` before touching Compose state.
- New `system/SystemMediaControlsFactory.kt` (OS switch) and `system/LoggingSystemMediaControls.kt`
  (no-op + logging, the Phase 1 stand-in).
- New `ui/viewer/MediaViewerSystemControlsEffect.kt`, shaped like `MediaViewerPlaybackEffects`:
  attach while a video is open, push metadata when it changes, push playback state on every
  play/pause and **on a 1 s tick** for position (the panel does not need 60 Hz, and reading
  `currentPositionMs` in composition would recompose the screen at frame rate).
- Commands map onto what exists: `PlayPause`/`Play`/`Pause` → `togglePlayPause()`
  (`VideoPlayerControls.kt`); `Stop` → pause + `seek(0)`; `Next`/`Previous` → the adjacent
  **`MediaItem.Video`** in the folder, set through `MediaViewerState.currentId` +
  `animationDirection`, exactly as the arrow branch of `handleMediaViewerKeyEvent` does. Images are
  skipped for the reason `MediaViewerPlaylist` skips them.
- **Fullscreen reports `hasNext = hasPrevious = false`**: that window is handed a single
  `MediaItem` and has no folder listing, and its own UI offers no next/previous either.
- Setting `WokaSettings.systemMediaControls` (default `true`) + `setSystemMediaControls`, surfaced as
  a `SettingsToggleRow` in the global **Video Player** panel. Anything that registers with the OS
  gets an off switch.

**Files:** new `system/SystemMediaControls.kt`, `system/SystemMediaSession.kt`,
`system/SystemMediaControlsFactory.kt`, `system/LoggingSystemMediaControls.kt`,
`ui/viewer/MediaViewerSystemControlsEffect.kt`; edit `MediaViewerScreen.kt`,
`MediaViewerFullscreenWindow.kt`, `WokaSettings.kt`, `SettingsRepository.kt`,
`SettingsRepositoryImpl.kt`, `VideoPlayerSettingsPanel.kt`

## Phase 1 result — ✅ shipped and verified live

Media keys control Woka while one of its windows is focused. Verified on the real corpus: play/pause
toggles, next/previous walk the folder's videos, no double-toggle.

**The session churn the first run showed is explained, and it is not this feature's.**
`MediaViewerScreen` holds `remember(activeItem, state.activeItemPath) { PipManager.createOrGetState(...) }`,
and those two keys settle at *different times* — `activeItemPath` is written by
`MediaViewerActiveItemEffects` one frame later. So every file switch builds a `VideoPlayerState`
against the outgoing path, then immediately builds a second one. The logs proved it end to end: two
identity hashes for the same file, an `attach`/`detach` pair for the first, and — the part that
actually costs something — **two `[MediaTracks]` dumps, i.e. the container is opened and probed
twice on every navigation.** On 4K HEVC that is exactly the path Step 7 measured as the expensive
one.

The token handover absorbs this cleanly for the media session. The double open itself was a
pre-existing defect in the viewer's state wiring, found here and **fixed on request**:

- `MediaViewerScreen` now gates on `state.activeItemPath == activeItem.file.absolutePath` rather
  than on the path merely being non-null. The gate means "*this* item has cleared the PiP conflict
  check", so it flips exactly once per file instead of twice.
- `MediaViewerActiveItemEffects` assigns `activeItemPath` **before** `loadThumbnail` instead of
  after. With the tightened gate the player is created when the path lands, and awaiting a thumbnail
  decode first would have traded the double open for a delayed start.

**Now Playing does not show yet**, as expected: that is Phase 2, and nothing registers with the OS
until it exists.

## Phase 2 (abandoned): Windows SMTC spike (the risk, isolated)

The smallest thing that proves the mechanism. Nothing else is built until it runs.

- New `system/windows/WinRt.kt`: `RoInitialize`, `WindowsCreateString` / `WindowsDeleteString`,
  `RoGetActivationFactory`, and a `vtableCall(pointer, slot, descriptor)` helper. Downcall handles
  built once.
- New `system/windows/WindowsSmtc.kt`: a **message-only window** (`CreateWindowExW` with
  `HWND_MESSAGE`) so no HWND has to be dug out of AWT/JAWT, then
  `ISystemMediaTransportControlsInterop::GetForWindow` → `ISystemMediaTransportControls`;
  `IsEnabled`, `IsPlayEnabled`, `IsPauseEnabled`, `PlaybackStatus`.
- New `system/windows/WinRtDelegate.kt`: the `ButtonPressed` handler — a COM object assembled in
  memory, a four-slot vtable of FFM upcall stubs (`QueryInterface`, `AddRef`, `Release`, `Invoke`)
  handed to `add_ButtonPressed`. The single hardest piece, hence its own file and its own run.
- `--enable-native-access=ALL-UNNAMED` in `compose.desktop.application.jvmArgs`; without it JDK 25
  warns on every restricted call and a later JDK will refuse them.

**Success criterion:** the Windows 11 flyout shows Woka, and the hardware Play/Pause key logs a
`MediaCommand`.

**Decision point:** if this does not land within a bounded attempt, swap *only* the Windows backend
for JMTC behind the Phase 1 interface — no other phase changes. Record which way it went, and why.

**Files:** new `system/windows/WinRt.kt`, `system/windows/WindowsSmtc.kt`,
`system/windows/WinRtDelegate.kt`; edit `desktopApp/build.gradle.kts`

## Phase 3 (abandoned): Windows metadata and the remaining buttons

- `get_DisplayUpdater` → `put_Type(MediaPlaybackType.Video)`, `get_VideoProperties` → `put_Title`,
  then `Update()`.
- `IsNextEnabled` / `IsPreviousEnabled` from `MediaSessionInfo`; `IsStopEnabled` on; `PlaybackStatus`
  follows `isPlaying`.
- `UpdateTimelineProperties` (position + duration) so the flyout's scrubber is honest — only if the
  Phase 2 plumbing makes it cheap, since it needs a second activation factory.
- Release every interface pointer in `disable()`, `RoUninitialize` on shutdown.

**Files:** `system/windows/WindowsSmtc.kt` (+ `WindowsSmtcMetadata.kt` if it passes 150)

## Why Now Playing was dropped — ❌ read this before trying SMTC again

**The outbound half worked completely.** Hand-rolled WinRT over FFM was not the problem: the vtable
ordinals taken from the IDL were right first time, no crash, no JMTC fallback needed. Windows 11's
flyout showed Woka with the file's title and its poster frame, and the play/pause icon tracked the
player.

**`add_ButtonPressed` refused, every time, with `0x80004021` — `CO_E_NOT_SUPPORTED`.** Nothing
inbound ever arrived: not a flyout click, not a hardware key. Registration was retried on each
`enable()` and failed identically.

What was measured, in order, before giving up:

1. **Instrumented the whole chain** — registration HRESULT, delegate `Invoke`, `get_Button`, command
   mapping, session dispatch, and the call into the player. Only the first ever printed. The failure
   is at registration; nothing downstream was ever reached.
2. **Instrumented the delegate's own `IUnknown`** — every `QueryInterface` IID and every `AddRef`.
   **Silence.** WinRT refuses *before touching the callback object at all*, so the delegate — the
   piece flagged up front as the hardest and least certain — is exonerated. Its permissive
   `QueryInterface`, the parameterized `ITypedEventHandler` IID, agility, marshalling: none of it is
   implicated, because none of it is ever consulted.
3. **Rebuilt the apartment and the message loop.** The session had been running on a bare worker
   thread in an MTA with no pump, while `GetForWindow` ties the controls to a window — and a window
   whose thread never pumps is one Windows cannot deliver to. Switched to `RO_INIT_SINGLETHREADED`
   with a real `PeekMessage` / `TranslateMessage` / `DispatchMessage` loop, tasks drained between
   iterations. **Same HRESULT.** So the apartment/pump theory is refuted too.

What was **not** tried, and is the only remaining lead if this is ever revisited: hosting the session
on **the application's real window** instead of a hidden `STATIC` one created for the purpose. That
means obtaining Woka's own `HWND`, which without JNI/JAWT means enumerating the process's top-level
windows and matching one — the reason it was ranked last. A second lead, cheaper but uglier, is
JMTC's prebuilt `SMTCAdapter.dll`, which the *Measured facts* section already argues against.

**The decision.** Keys through SMTC never worked, so SMTC's one real engineering argument — that
Windows would route the keys for us — never paid out. The AWT path had already been measured working
in Phase 0 and shipped in Phase 1. Rather than keep a panel that displays and does not respond, the
whole Now Playing half was cut and `system/windows/` deleted: eight files, ~850 lines, uncommitted.
`SystemMediaControlsFactory` returns `AwtMediaKeyControls()` on Windows unconditionally, and
`--enable-native-access=ALL-UNNAMED` came back out of `desktopApp/build.gradle.kts` with the last
FFM call.

Also reverted with it: `MediaSessionInfo.artworkPath` and `videoArtworkPath`, which existed only to
feed the panel. `thumbnailCacheFile()` stays in `ThumbnailCacheFile.kt` — extracted from
`ThumbnailLoader` during this work, and the loader reads better for it.

**What `MediaSessionInfo` still carries and nothing consumes:** `title` and `durationMs`. Left in
place because the seam is the platform contract and macOS may yet want them; delete on sight if
Phase 4 does not.

## Phase 4: macOS — MPRemoteCommandCenter, keys only

Not written. **Verified when a Mac exists**, and unaffected by the Windows outcome above: this is a
different API on a different OS, and on macOS it is the *only* route to the keys at all — they
arrive as `NSSystemDefined` events that AWT never sees.

- New `system/mac/ObjC.kt`: FFM over `libobjc` — `objc_getClass`, `sel_registerName`, one
  `objc_msgSend` downcall handle **per signature** (arm64 has no single variadic form), and a helper
  that builds a class with `objc_allocateClassPair` + `class_addMethod` pointing at upcall stubs,
  which is how a Kotlin lambda becomes a command target.
- New `system/mac/MacMediaKeys.kt`: `MPRemoteCommandCenter.sharedCommandCenter` →
  `play` / `pause` / `togglePlayPause` / `stop` / `nextTrack` / `previousTrack` commands, each
  `addTarget:action:`; `enabled` driven by `hasNext` / `hasPrevious`.
- ~~`MPNowPlayingInfoCenter`~~ — cut with the rest of Now Playing. Note the consequence before
  reinstating it: Control Centre may decline to route commands to an app that publishes no
  `nowPlayingInfo`. If the keys turn out not to fire without it, that is a *reason to bring the
  panel back on macOS specifically* — not a reason to reopen SMTC.
- `nativeDistributions.macOS { bundleID = "com.woka" }` — remote commands want a real `.app`, so on
  macOS this is verified through `createDistributable`, **not** `gradlew run`.

**Files:** new `system/mac/ObjC.kt`, `system/mac/MacMediaKeys.kt`; edit
`system/SystemMediaControlsFactory.kt`, `desktopApp/build.gradle.kts`

## Verification

Windows — this list is what makes the step Done, and it is ✅ met as of the scope cut:

1. The hardware Play/Pause key toggles playback while a Woka window has focus.
2. Next / Previous move to the adjacent **video** in the folder, and do nothing when there is none.
3. Leave the viewer: the keys go back to whatever else is playing (test against a browser tab).
4. Windowed ⇄ fullscreen keeps exactly one session — no duplicate, no drop.
5. Settings → Video Player → the toggle off means nothing is registered at all.
6. No OS panel entry appears anywhere, and no FFM warnings in the log.

macOS, when a Mac exists: 1–5 against the hardware keys, run from `createDistributable`.

macOS, when a Mac exists: the same list against Control Centre, run from `createDistributable`.

## Out of scope

- **The OS Now Playing panel, on every platform** — cut; see *Why Now Playing was dropped*. Artwork
  went with it.
- **Keys while Woka has no focus.** This is what the panel would have bought: without SMTC, Windows
  routes the keys to whoever is playing, and the AWT listener only sees events dispatched to Woka's
  own windows. Accepted, and it matches the ownership decision above — outside the viewer Woka
  registers nothing. `RegisterHotKey` would take the keys from Spotify process-wide and is not worth
  it.
- **Linux MPRIS** — not a target of this project.
- **Seek/scrub commands *from* the OS panel**, as opposed to a display-only timeline: it adds a
  command direction neither platform's UI leans on for video.
