# Startup and background — backlog

### ST-002 — Compact idle
Жизненный цикл visible/closing/idle/reopening/full-exit с сохранением editor/save gate.
Освобождение окна, composition, media owners и caches; settings flush без выхода процесса.
IPC-запросы во время переходов не терять; повторное закрытие после Cancel работает.
Сравнить скорость/RAM с сохранённым скрытым окном; активные audio/PiP/save не называть idle.
Depends on: ST-001.

### ST-003 — Launcher и упаковка
Нативный forwarder посылает запрос живому экземпляру до загрузки JVM; при отсутствии запускает её.
Определить полный выход и discoverability фонового режима, затем mockup нового UI по conventions.
AOT-кэш на Windows теперь обучается при упаковке и ставится с MSI (ST-001, 2026-11-19);
открыто: обучение в CI и кэш для macOS/Linux.
Автозапуск в ОС не включать без отдельного требования; остаточный JVM footprint измерить.
Depends on: ST-001, ST-002.

### ST-006 — Refreshing the image's metadata from Gradle
The two agent outputs ST-004 commits go stale as the code moves. Gradle tasks rerun them: the agent
over the `:shared` test suite on GraalVM's java (JavaCPP from the distributable's folder; test-only
types filtered out), and the agent over a scripted app run, each writing back into
`desktopApp/native-image/`. A strict build plus a scripted pass over the fixtures reports what is
still missing. Replaces `ni-tests.ps1`, `ni-agent2.ps1` and the filter script.
Depends on: ST-004.

### ST-007 — An intermittent native crash in video render
`:shared:jvmTest` died twice in a row on 2026-11-21 (and once on 2026-11-20 20:05) with
`EXCEPTION_ACCESS_VIOLATION` at `avutil-60.dll+0x4fe03`, a call through a null pointer, inside
`avcodec_send_packet` from `FFmpegFrameGrabber.grabFrame` in the render's `recordClips`
(`RenderSoundSyncTest`, through `FreshFrameGrabber` / `DoviFrameGrabber`). The render tests pass
alone and together; only the full suite reaches it, about 320 tests in, and not every time — so
some earlier test leaves FFmpeg state behind. The same path is `Render video…` in the app, where it
would take the process down. The three `hs_err_pid*.log` files are in `shared/` (ignored by Git).
Find the order that reproduces it, then the state.

