# DV5-102 — P5 во всех потребителях кадров

| | |
|---|---|
| Order | 3 |
| Depends on | DV5-001, DV5-101 |
| Feature | [Dolby Vision P5](../dolby-vision-p5.md) |

**Goal.** Подключить проверенное восстановление P5 до RGB conversion/resize во всех readers.
**Why now.** 2026-11-13 пользователь поручил перейти к реализации; CPU корректность проверена.
GPU/4K60 остаётся отдельной оптимизацией, не блокирует корректную поддержку профиля.

## Steps
1. Перенести проверенные LUT/SIMD kernels из tests в native implementation, сохранив независимый
   double/pow oracle. Добавить checked create/prepare/rows/destroy ABI: metadata текущего AVFrame,
   version/format/strides/flags validation, invalid prepare сбрасывает предыдущее состояние.
2. Добавить FFM binding и converter с bounded reusable buffers и общим ограниченным пулом workers.
   Native не удерживает FFmpeg pointers между кадрами; все workers завершаются до следующего grab.
3. FreshFrameGrabber для P5 открывает RAW decoder и выдаёт RGBA/BGR/RAW по прежнему контракту.
   JavaCV COLOR → RAW нельзя переключать на живом буфере: release освобождает image_ptr иначе.
   После header detection закрыть первый контекст и открыть RAW; публичные requested settings сохраняются.
4. HdrInfo распознаёт P5 по configuration; покадровые source/L1 peaks читаются из того же AVFrame.
   Теги output — RGB/BT2020/PQ/full, side data копируются с очисткой, не накапливаются.
5. Guard проверяет доступность native converter вместо безусловного запрета; metadata failures
   остаются явными. Viewer CUDA для P5 не должен обходить общий путь. Обновить poster cache version.
6. Native negative/math tests, integration: sequential/seek/RAW/BGR/resize, отсутствие metadata,
   poster/hover/player/photo/render, совместимые SDR/HDR/DV профили; build и anti-FQN.

## Files
- `natives/dovi/woka_p5*.{c,h}`, `CMakeLists.txt`, existing native tests/build/README.
- New `engine/decode/Dovi*.kt`, `P5GrabberState.kt`; edit `FreshFrameGrabber.kt`.
- New `engine/probe/DoviFrameMetadata.kt`; edit `DoviSupport.kt`, `HdrPeak.kt`,
  `track/VideoPlayerVideoTrack.kt`, `decode/VideoAccelerationPolicy.kt`.
- `core/data/WokaStorage.kt`, `jvmTest/unit/DoviSupportTest.kt`, `integration/DoviGuardTest.kt`
  and new integration/native boundary tests as needed.

Больше восьми файлов необходимо для одного видимого поведения: все readers, RAW geometry,
экспорт и capability guard должны переключиться вместе. Отдельные math/native foundation уже проверены.

## Done when
P5 воспроизводится и экспортируется с проверенным цветом; seek не использует старый RPU;
broken/unsupported metadata не дают YUV fallback; ресурсы освобождаются, tests/build проходят.
4K60 не заявляется: измеренный CPU предел DV5-101 остаётся явно документированным.

## Execution
**Status: выполнен на Windows 2026-11-13.** Общая интеграция и регрессии завершены;
производительность 4K60 остаётся DV5-104, длительная память/macOS/Linux — DV5-103.

2026-11-13: начата интеграция по указанию пользователя, GPU research отложен.

### Checkpoint — 2026-11-13
Реализован production native converter (LUT/AVX2 + scalar MMR fallback), FFM binding,
общий пул 8 workers, RAW reopen JavaCV с сохранением public BGR/RGBA/RAW настроек.
Покадровые RPU/source/L1 идут через HdrInfo; экспорт фото читает пик выбранного кадра,
рендер — каждой сцены. Remux P5 требует `strict=unofficial`, иначе movenc теряет dvcC/dvvC:
это поймано integration test и исправлено. Кэш переведён на v8, Done проверяет первый RPU.

Focused tests прошли: reference pixels, sequential/seek/restart, RAW turn, BGR/RGBA resize,
missing RPU rejection/recovery, poster/hover/player/Done, photo/SDR encode/P5 remux,
4K changing RPU/backward seek/EOF, worker failure/interruption joins.
Native CTest: 3/3 (63,897 math, 305 metadata, 1,491 new ABI boundary/state checks).
Первый общий прогон: 1,686 tests, 5 failures — старые ожидания запрета P5 и cache v7.
Эти assertions обновлены; повторный прогон ещё нужен. Дополнительно исправлена передача
RPU от выхода PictureGraph (bwdif задерживает кадр), добавлен regression test; ещё не запущен.
Anti-FQN и wc прошли до последних тестовых/filter правок; повторить в конце.

**Ручная проверка автора:** цвета правильные, grid и все превью рисуются правильно.
**Открыто:** playback лагает; автор сообщил как FYI. CPU harness DV5-101 не достигал 4K60.
После регрессии замерить production FreshFrameGrabber path и стоимость resize; число потоков
не менять без замера. GPU/4K60 — DV5-104. Длительная память и macOS/Linux ещё не проверены.

### Production throughput — 2026-11-13
`./gradlew.bat :shared:jvmTest --tests com.woka.probe.DoviProductionThroughputProbe
-I build/dv-p5-reference/init.gradle --console=plain` — success.
13 configurations, 30 warmup + 120 measured frames each. Input/source provenance unchanged
from DV5-001. Bundled DLL + production FreshFrameGrabber, shared 8 colour workers,
ByteArray + Skia Bitmap install/close; UI/audio excluded. Result file:
`shared/build/dv-p5/production-throughput.txt`. Each output size has matching checksum
at all decoder thread counts.

| Source → output | Decode threads | Grab (decode + colour + resize) | Bitmap | FPS | P99 |
|---|---:|---:|---:|---:|---:|
| 4K → 4K | 2 | 19.772 ms | 6.437 ms | 38.16 | 55.991 ms |
| 4K → 4K | 4 | 13.056 ms | 6.620 ms | 50.82 | 26.413 ms |
| 4K → 4K | 8 | 12.448 ms | 6.526 ms | 52.70 | 20.282 ms |
| 4K → 1080p | 2 | 28.983 ms | 1.611 ms | 32.69 | 57.455 ms |
| 4K → 1080p | 4 | 21.761 ms | 1.648 ms | 42.72 | 24.698 ms |
| 4K → 1080p | 8 | 21.644 ms | 1.657 ms | 42.92 | 24.700 ms |
| 4K → 720p | 4 | 19.248 ms | 0.849 ms | 49.76 | 24.133 ms |
| 4K → 480×270 | 4 | 18.023 ms | 0.082 ms | 55.23 | 27.590 ms |
| 1080p → 1080p | 4 | 3.145 ms | 1.610 ms | 210.32 | 5.960 ms |

Увеличение decoder threads не устраняет лаги 4K; production оставляет прежние 4 threads.
Native colour + resize + bitmap копии требуют дальнейшей оптимизации (DV5-104).

### Final verification — 2026-11-13
- `./gradlew.bat :shared:build :desktopApp:compileKotlin :shared:jvmTest
  --tests com.woka.unit.* --tests com.woka.integration.* --tests com.woka.ui.* --console=plain`
  — **BUILD SUCCESSFUL**, 1,687 tests, zero failures/errors/skips, 1m44s.
- Старые ожидания запрета P5 обновлены: Done сохраняет recipe, файл декодируется как P5,
  Revert возвращает исходный SHA-256. Cache migration тестирует v7 → v8.
- Delayed bwdif output сохраняет исходный RPU при продвижении decoder к другой сцене;
  `FrameDelivery.colourOf` использует AVFrame выхода фильтра, включая seek/lane hand-off.
- Native CTest 3/3; packaged Windows DLL обновлена после завершения test JVM.
  SHA-256 `bee75656439b0c7487079fe9550192463b765e7d5cd082a205ebe80cb8214b3a`.
  После копирования DLL повторно прошли 5 colour/export integration tests (6s).
- Anti-FQN: все matches только imports/package; все изменённые/новые Kotlin ≤144 строк.
- Существующие SettingsStore shutdown-hook сообщения о удалённых test roots/занятом settings.json
  остаются в stderr; тестовых ошибок нет. Production settings не менялись.
- Полный длительный memory soak, macOS/Linux runtime и 4K60 не заявляются.

Точка продолжения: DV5-104 — устранить наблюдаемые автором лаги. Не менять decode threads
вслепую: 4 → 8 почти не влияет на throughput. Использовать production probe и существующие
reference/oracle, измерять colour/resize/copy отдельно; изменение цвета не допускается.
