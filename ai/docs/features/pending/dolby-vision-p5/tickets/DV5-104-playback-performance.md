# DV5-104 — Плавность 4K P5

| | |
|---|---|
| Order | 4 |
| Depends on | DV5-102 |
| Feature | [Dolby Vision P5](../dolby-vision-p5.md) |

**Goal.** Устранить лаги 4K, сохранив проверенные цвета и привязку RPU к кадру.
**Why now.** Автор подтвердил цвета/grid/previews, но playback лагает; 2026-11-18 поручил продолжить.
**Status.** Завершён на Windows. Build, 1,689 regression tests и три фазы playback probe
прошли. 2026-11-19 автор проверил файл руками: «всё ок работает файл» — лаги на проверенном
файле устранены, ручная приёмка принята. Платформы и длительная память остаются в DV5-103.

## Реализация — 2026-11-18

- D3D11 compute (`natives/dovi/woka_p5_gpu.cpp/.h/.hlsl`) восстанавливает полный RGBA8
  BT.2020 PQ кадр по текущему проверенному RPU, затем при необходимости уменьшает triangle
  фильтром. YUV до восстановления цвета не уменьшается; display mapping остаётся у Woka.
- `woka_p5_gpu` — необязательный синхронный C ABI: 0 success, 1 CPU fallback, отрицательные
  статусы ошибки аргументов/состояния. Вход заимствован до возврата; нельзя одновременно
  запускать rows. Буферы/device принадлежат converter, переиспользуются и закрываются с ним.
- Windows MSVC x64 / D3D feature level 11, AVX2-compatible polynomial LEFT frames, ширина
  кратна восьми; уменьшение максимум в 8 раз по каждой оси. Остальные layouts/MMR/platforms,
  отсутствующий GPU, device failure и старый DLL без символа используют проверенный CPU.
  Ошибка RPU по-прежнему явная; CPU fallback не означает обычный YUV fallback.
- `DoviGpuNative` / `DoviColourConverter` / `P5GrabberState` передают requested size в GPU;
  последующая смена geometry при fallback пересоздаёт swscale. RGBA/BGR/GRAY получают одну
  геометрию; RAW остаётся full resolution для PictureGraph.
- `VideoPlayerGrabber` отмечает playback grabbers и lanes. `DoviFrameGrabber` при reopen
  4K P5 ≥50 fps временно выбирает 8 decoder threads вместо стандартных 4, если CPU имеет
  ≥8 logical processors. Caller options восстанавливаются; preview/export не включают
  эту настройку. Количество lanes не менялось. CPU fallback остаётся работоспособным.
- HLSL встроен в DLL, компилируется один раз на converter с IEEE strictness; дополнительных
  FFmpeg DLL нет. Системные зависимости: D3D11/D3DCompiler, C++17 build tools на Windows.

## Точность

`DoviGpuProbe`: два авторских файла, PTS 1/5/10 секунд. **6/6 full-resolution кадров
побайтно совпали с проверенным CPU**. Независимый premapping oracle DV5-101 остаётся основанием
CPU colour math; новый код не меняет его LUT/AVX2 arithmetic. Шесть resize пар на каждый файл
также проверены: triangle отличается от целочисленного legacy swscale максимум на 4/255,
среднее ≤0.036/255. Это отличие фильтра/округления после восстановления, не ошибка P5 цвета.

`DoviGpuColourTest` сравнивает full frame с CPU и resized pixels с отдельным double triangle
oracle (half/odd/upscale, края, alpha; ≤1/255). Проверяет LEFT→CENTER CPU fallback без stale
scaled output. Native API test проверяет GPU strided output/padding, недопустимые размеры,
инвалидацию preparation и unsupported chroma; GPU status=0 на этой машине.

## Измерения

RTX 5080, авторские Mystery Box 3840×2160 59.94 и Jellyfin 1920×1080 60.
Held-frame native upload+colour+readback: 4K ~4.2 ms против CPU ~11.2 ms;
с resize до 1080p ~2.7 ms, до 480×270 ~2.2 ms. Это не FPS плеера.

Production throughput включает decode/colour/resize и per-frame ByteArray + Skia Bitmap
install/close, но не audio/UI. Длинный прогон: 900 кадров, 120 warmup, JVM приложения
`-Xmx1536m -XX:MaxNewSize=256m -XX:+TieredCompilation -XX:Tier4CompileThreshold=100000
-XX:CICompilerCount=2 -Xshare:auto`.

| Output | Decoder threads | Grab ms | Bitmap ms | FPS | p99 ms |
|---|---:|---:|---:|---:|---:|
| 4K → 4K | 4 | 9.354 | 6.629 | 62.57 | 47.157 |
| 4K → 4K | 8 | 6.015 | 6.240 | **81.60** | 15.682 |
| 4K → 1080p | 4 | 11.352 | 1.631 | 77.03 | 50.808 |
| 4K → 1080p | 8 | 6.429 | 1.726 | **122.62** | 42.499 |
| 1080p → 1080p | 4 | 2.141 | 1.436 | 279.52 | 8.786 |

CPU baseline 2026-11-13 (короче, 150 кадров/30 warmup): 50.82 FPS full4K и 42.72 FPS
4K→1080p при 4 threads. Первый короткий GPU прогон: 55.34/72.61 FPS при 4 threads,
64.61/120.72 при 8; не смешивать результаты разных режимов прогрева/JVM.

Отвергнутые варианты, production не меняли:
- Dynamic swscale вместо legacy: пиксели идентичны, времени не экономит (1080p ~9.1 ms).
- Scalar gathers SIMD: 11.186→13.274 ms; force-inline: 11.160 ms, выигрыша нет. Обе правки отменены.
- Staging array → Skia pixels через FFM: ~5.12–5.36 ms против installPixels ~5.11–5.13 ms.
  FramePacer, heap pressure и FrameRetention оставлены без изменений.

## Проверки и точка продолжения

- Native build и CTest **3/3**: 63,897 math, 305 metadata, **1,704 API checks**, 0 failures.
- Shared build + desktop compile и **1,689 unit/integration/UI tests** прошли: 361 suites,
  0 failures/errors/skips, включая GPU colour/resize/fallback и lifecycle с новой thread policy.
  После прерывания сессии 2026-11-19 XML проверены и build подтверждён повторным запуском
  (test task UP-TO-DATE, повторного исполнения тестов не было). Anti-FQN: 140 matches,
  все import/package; изменённые Kotlin-файлы ≤133 строк; `git diff --check` чист.
- `DoviPlaybackProbe`: настоящий player/audio/pacer/presenter без окна и Compose draw;
  4K→1080p→4K, backward seek, 3s settling + 10s наблюдения на фазу. Все три фазы ~59.9 FPS,
  max gap 22.1 ms, gaps >25 ms = 0; память процесса 2047/2502/2658 MiB.
  Это не длительный leak test и не UI FPS. Автор отдельно подтвердил ручное воспроизведение
  2026-11-19; отдельного подтверждения fullscreen/всего корпуса не запрашивали и не заявляем.
- Следующий шаг для всей функции: DV5-103 — macOS/Linux packaging/runtime и длительная память.
  GPU/4K performance исследование не повторять без новой регрессии.
- Packaged Windows DLL SHA-256:
  `09557385da1cfcbcf3a7578a03458f0804b44f92d8c8ceb0682f338ee3be2a26`.

## Воспроизведение

Из root: `powershell -File natives/dovi/build.ps1` (native build + CTest + package).
`./gradlew.bat :shared:build :desktopApp:compileKotlin :shared:jvmTest --tests com.woka.unit.* --tests com.woka.integration.* --tests com.woka.ui.* --console=plain`.

Опт-ин probes: `:shared:jvmTest --tests com.woka.probe.DoviGpuProbe` с Test system properties
`woka.probe.dv5=true`, `woka.probe.dv5.library=<absolute build DLL>`.
`DoviProductionThroughputProbe` поддерживает `woka.probe.dv5.widths=3840,1920`,
`threads=4,8`, `frames=900`, `warmup=120` под тем же префиксом; параметры JVM выше.
`DoviPlaybackProbe` использует ту же opt-in property и настройки JVM.
Локальные init scripts: `build/dv-p5-reference/init.gradle`, `build/dv-p5-performance/long.gradle`.

Артефакты: `shared/build/dv-p5/{scale-throughput,gpu-comparison,bitmap-throughput,
production-throughput,playback-cadence}.txt`. CPU baseline и первый короткий GPU прогон
сохранены в `build/dv-p5-performance/production-{cpu-20261113,gpu-first}.txt`.

## Done when

Проверенный путь выдерживает 4K59.94 с запасом до UI; playback/seek/preview/export сохраняют
цвет/RPU, build/regressions проходят; ручной playback автора завершает приёмку.
