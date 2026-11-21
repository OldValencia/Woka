# DV5-101 — Проверенное преобразование цвета

| | |
|---|---|
| Order | 2 |
| Depends on | DV5-001 |
| Feature | [Dolby Vision P5](../dolby-vision-p5.md) |

**Goal.** Сопоставить восстановление цвета из исходных плоскостей с независимым reference,
затем выбрать способ исполнения по измерениям.
**Why now.** Metadata и raw кадры доступны; production ещё заблокирован. Этот этап исследовательский.

## Steps

1. Добавить standalone C comparator в native research tests. Он читает записанные raw/metadata,
   делает bilinear upsampling с явным chroma siting, нормализацию кодов, polynomial/MMR reshaping,
   матрицы и PQ. Сравнивает с float reference на том же PTS; печатает mean/max/P99 и худший пиксель.
2. Проверить математические границы на синтетических входах: pivots, MMR orders, clipping,
   range, отрицательные значения после линейной матрицы. Не выводить MMR support из реальных
   сэмплов: в шести измеренных кадрах использовались только polynomial curves.
3. Отделить погрешность precision/chroma от ошибки формул; float32 файл reference не гарантирует
   float32 intermediate FBO в libplacebo. Не подгонять threshold после просмотра результата.
4. Проверить полный кадр/реальный draw size, throughput и bounded memory. Перед production
   описать выбранный CPU/native или GPU путь и договорённость о цвете для всех consumers.

## Files — ближайшая проверка

- `natives/dovi/tests/p5_compare.c` (new): standalone scalar comparator, не production.
- `natives/dovi/CMakeLists.txt` (edit): research executable в BUILD_TESTING.
- Существующие `shared/build/dv-p5/` и `build/dv-p5-reference/frames/`: входы DV5-001.

После численного сравнения указать точные файлы performance-прототипа, исходя из выбранного пути.
Code с новым алгоритмом не подключать к guard до DV5-102.

## Done when

Формулы и границы проверены, ошибка объяснена и укладывается в заранее обоснованный бюджет;
есть измерения для выбора исполнения и конкретный контракт выхода. Сборка и регрессии зелёные.

## Execution record

2026-11-12: начата скалярная проверка. Pinned reference: libplacebo v7.387.0;
нормализация raw/pivots — 2^BL_bit_depth, коэффициентов — 2^coef_log2_denom.
Источники: [mapping](https://raw.githubusercontent.com/haasn/libplacebo/v7.387.0/src/include/libplacebo/utils/libav_internal.h),
[порядок преобразований](https://raw.githubusercontent.com/haasn/libplacebo/v7.387.0/src/shaders/colorspace.c).
Нельзя смешивать constants из master с pinned reference без отдельного сравнения.

### Промежуточные результаты — 2026-11-12

`woka_p5_compare` собран отдельно от DLL. CTest `dovi_colour_math` проверяет identity,
MMR orders 1/2/3 по независимым числам, правую ветвь на pivot, clamp входа и PQ roundtrip.
Оба CTest (`dovi_colour_math`, `dovi_metadata_bounds`) прошли. Comparator читает один
кадр за раз; его malloc/sort и файловый I/O не представляют production performance.

Полный pixel scan против первоначального reference (`chromaXShift=0`, left chroma,
bilinear, sample normalization 1024), ошибки в PQ [0,1]:

| Файл / секунда | Mean | P99 | Max |
|---|---:|---:|---:|
| Mystery Box / 1 | 0.00024469 | 0.00078886 | 0.00686572 |
| Mystery Box / 5 | 0.00024016 | 0.00078621 | 0.00437046 |
| Mystery Box / 10 | 0.00018319 | 0.00072246 | 0.00329898 |
| Jellyfin / 1 | 0.00016153 | 0.00101492 | 0.00775360 |
| Jellyfin / 5 | 0.00018919 | 0.00101319 | 0.00916645 |
| Jellyfin / 10 | 0.00014800 | 0.00078059 | 0.00650524 |

Это наблюдения, **не принятая точность**. На каждый 4K кадр проверено 24,883,200 каналов,
1080p — 6,220,800. `build/dv-p5-reference/scalar-comparison.txt` хранит таблицу.

Инструментировали reference (`debug.log`): используется `rgba16hf`, а `tonemapping=clip`
не отключает дополнительную цепочку color mapping. Поэтому добавлен research hook
`natives/dovi/reference-premap.glsl` (MAIN → сохранённый PQ → OUTPUT), по
[документации libplacebo](https://libplacebo.org/custom-shaders/).
Захват на Jellyfin 5 с даёт mean=0.000167637, P99=0.000610012, max=0.00899717.
Остаток не объяснён; нельзя целиком списывать его на float16 без дополнительной проверки.

Проверен второй вариант HPE inverse из текущего master как диагностический контроль
(`hpe2020` последним аргументом comparator): mean=0.000163812, max=0.00899744.
Он не устраняет максимум. Худший пиксель (1143,599), G: input codes
109 / 579.5 / 422.375, CPU PQ=0.00985765, reference=0.000860214.
Следующий шаг — сравнить промежуточные значения именно этого пикселя и ошибку в линейном
свете; проверить sampling/float precision на синтетических входах. Две гипотезы проверены,
дальше нужна инструментация, а не подбор коэффициентов/допуска.

### Уточнённый reference и CPU-прототип — 2026-11-13

2026-11-13, диагностика: при неизменных коэффициентах и CPU sampler замена только
упаковки входа reference (`format=p010le` перед libplacebo) уменьшила максимум ошибки
Jellyfin / 5 с с 0.00899717 до 0.000494686. P010 хранит десять бит слева в 16-битном
слове, исходный yuv420p10le — справа; лог подтверждает R16 UNORM uploads. Это контроль
точности входного GPU sampling, а не изменение формул Dolby Vision. Следующая проверка:
побитная эквивалентность P010 и исходных кодов, затем все шесть кадров с этим входом.

До полного повторного сравнения зафиксирован бюджет для PQ reference с FP16 FBO:
max <= 1/1024, P99 <= 1/2048, mean <= 1/4096. Один FP16 ULP в диапазоне [0.5,1)
равен 1/2048; бюджет max оставляет ещё столько же на float32 arithmetic/sampling.
Это менее четверти шага будущего 8-битного PQ выхода. Дополнительно смотреть линейную
ошибку и тёмные пиксели, а не считать прохождение этого бюджета единственной проверкой.
Старые reference не перезаписывать; новый запуск `reference.ps1 -Premap -Output <new directory>`.

Полный повторный прогон завершён. `--check-p010` сравнил 3,110,400 компонентов Jellyfin / 5:
нулевые отличия после проверки сдвига каждого кода на 6 бит и interleave Cb/Cr. Декодированные
raw-входы остальных кадров также сохранены новым reference script; исходный reference сохранён.
Артефакты: `build/dv-p5-reference/premap-frames/` (manifest, hashes, logs, outputs),
`premap-comparison.txt`, `lut-comparison.txt`, `simd-comparison.txt`.

| Кадр | Scalar mean PQ | Scalar P99 PQ | Scalar max PQ | SIMD max против scalar |
|---|---:|---:|---:|---:|
| Mystery / 1 | 0.000149318 | 0.000470910 | 0.000509415 | 0.000000981 |
| Mystery / 5 | 0.000149195 | 0.000471172 | 0.000509415 | 0.000001026 |
| Mystery / 10 | 0.000133539 | 0.000467166 | 0.000504238 | 0.000001020 |
| Jellyfin / 1 | 0.000123300 | 0.000470939 | 0.000493870 | 0.000007252 |
| Jellyfin / 5 | 0.000140460 | 0.000470792 | 0.000494686 | 0.000007252 |
| Jellyfin / 10 | 0.000112017 | 0.000470145 | 0.000493444 | 0.000007252 |

Все шесть прошли заранее записанный бюджет reference. Дополнительный бюджет оптимизации
max <= 1/4096 PQ также прошёл для LUT и SIMD. Максимум линейной ошибки против FP16 reference:
4.764 нит (яркий Mystery), средняя 0.135–0.255 нит по шести кадрам. Это погрешность сравнения
с данным reference, не обещание абсолютной точности Dolby. Старый максимум в тенях исчез
при изменении только упаковки входа GPU. MAIN hook остаётся необходимым для исключения
display mapping; просто `tonemapping=clip` этого не обеспечивает.

Исследовательские файлы (не подключены к app DLL):
- `tests/p5_lut.h`: double matrices/reshaping, интерполированные PQ LUT; независимый
  scalar pow остаётся в `p5_compare.c` для сравнения. На шести кадрах max <= 0.000007252 PQ.
- `tests/p5_simd.c/.h`: AVX2, float matrices/PQ и таблицы polynomial curves на точных
  1/8-code позициях left-chroma bilinear. MMR этим SIMD ещё не покрыт; scalar поддерживает MMR.
- `tests/p5_benchmark.c`: bounded buffers, warmup, checksum, подготовка metadata включена;
  OpenMP только в research executable. Нет decoder/resize/bitmap/UI/FFM costs.

120 кадров одного raw-входа, Release, итоговый row SIMD (файл `simd-row-benchmark.txt`):

| Размер | 1 поток | 4 потока | 8 потоков |
|---|---:|---:|---:|
| 3840×2160 | 62.60 мс | 17.14 мс | 11.08 мс |
| 1920×1080 | 14.70 мс | 4.12 мс | 2.75 мс |

Скалярный LUT до SIMD: 4K/8 threads 47.47 мс, 1080p/8 threads 10.58 мс.
Промежуточный SIMD со скалярным packing: 22.22/5.70 мс; финальный row path векторизует
chroma sampling и RGBA packing. Его checksum немного отличается из-за float округления.
Отдельная полная проверка row path завершена на всех шести кадрах (`simd-row-comparison.txt`):
max отличие от scalar и quantized reference — 1 код RGBA8, alpha всегда 255;
float batch comparison тоже прошёл. Padded strides дополнительно проверены синтетически.
Подготовка PQ tables занимает ~3.2 мс один раз на context, размер таблиц ограничен.
`workspaceBytes` в старом benchmark не включает SIMD context; это исправлено в исходнике,
старые числа нельзя считать полным memory budget.

### Точка продолжения — 2026-11-13

P5 guard активен; app DLL по-прежнему configuration-only. Предпочтительный кандидат —
native CPU с AVX2 polynomial path и scalar MMR/fallback проверен по цвету,
но end-to-end throughput ниже цели 4K60 (см. ниже). Позже 2026-11-13 пользователь поручил
подключить этот путь: реализация и актуальный статус в DV5-102. Итоги прототипа:
1. Выполнено: 63,897 synthetic LUT/MMR/SIMD/row/stride checks, zero failures;
   worst synthetic PQ=0.000003079. CTest 2/2 passed. Добавлен baseline CPUID/XGETBV gate
   (AVX2/FMA/OSXSAVE), CMake SIMD ограничен MSVC x64. Полный row-vs-reference: 6/6 passed.
2. Выполнено: native research binding/probe для decode + convert + copy, реальные
   AVFrame strides, borrowed pointers и bounded workers/buffers. Замеры ниже.
3. GPU/4K60 перенесён в DV5-104. CPU подключён в DV5-102 по указанию пользователя,
   но его нельзя выдавать за достигнутый 4K60; автор подтвердил корректные цвета и лаги playback.

End-to-end probe добавлен: `tests/p5_probe.c`, отдельная `woka_p5_probe.dll` только внутри
BUILD_TESTING + MSVC x64 + OpenMP. FFM: `DoviColourAccess` / `DoviP5ThroughputProbe` (jvmTest).
Он проверяет AVFrame strides и libavutil version, читает исходные плоскости напрямую и использует bounded RGBA buffer,
читает metadata текущего кадра, завершает native workers до возврата. Это исследовательский
polynomial-only bridge; отказ -5 на MMR, не production capability. Проба: 30 warmup + 120 frames,
decode-only / 4 / 8 workers, software decoder 4 threads, ByteArray + Skia Bitmap copy/close.
Команда: `./gradlew.bat :shared:jvmTest --tests com.woka.probe.DoviP5ThroughputProbe
-I build/dv-p5-reference/init.gradle --console=plain`. Результат: `shared/build/dv-p5/throughput.txt`.

Финальный замер 2026-11-13 (30 warmup + 120 последовательных кадров, software HEVC decoder
4 threads, colour 8 workers; включает восстановление цвета, ByteArray и Skia Bitmap install/close):

| Источник → bitmap | Decode | Colour | Resize + copy | Всего | FPS | P99 |
|---|---:|---:|---:|---:|---:|---:|
| 4K → 4K | 1.276 мс | 11.596 мс | 6.221 мс | 19.094 мс | 52.37 | 27.282 мс |
| 4K → 1080p | 0.135 мс | 11.488 мс | 14.421 мс | 26.045 мс | 38.40 | 28.898 мс |
| 1080p → 1080p | 0.064 мс | 2.862 мс | 1.401 мс | 4.327 мс | 231.13 | 5.219 мс |

Decode-only: 4K 95.74 FPS, 1080p 476.48 FPS. Малое decodeMs при conversion НЕ означает
ускорение декодирования: FFmpeg frame threads работают, пока владелец обрабатывает прошлый кадр.
Именно totalMs/FPS описывают этот pipeline. UI/GPU draw, audio, длительная память и редакторские
lanes здесь не измерены. Resize использует существующий `ThreadedVideoConverter` (RGBA → RGBA,
SWS_BILINEAR), строго после P5 conversion; его 14.4 мс с copy нельзя скрывать kernel-FPS.
Удаление промежуточной raw-копии сохранило checksum и сэкономило ~1.3 мс colour на 4K;
полный 4K60 всё ещё не достигнут. Прямой strided reader проверен на padded planes.

Windows native build/CTest и `:shared:build` с выбранной throughput probe прошли.
Это не повторный прогон 1677 regression tests: production Kotlin не менялся, прежний полный
прогон описан в DV5-001. Новые Kotlin файлы 79 и 118 строк, anti-FQN: только imports/package.

JavaCV source 1.5.14 сохранён в `build/dv-p5-reference/FFmpegFrameGrabber.java`:
`processImage()` private. `grabFrame(doProcessing=false)` не устанавливает opaque AVFrame
на новом кадре, поэтому простой перехват без processing недостаточен. Потребуется RAW
processing с восстановлением запрошенных размеров/режима либо свой decoder adapter.
Настройки RAW нельзя незаметно навязать потребителям: PictureFilter проверяет imageMode,
а BGR/RGBA/RAW, seek-клоны и последние side data должны сохранить прежний контракт.

Следующий запуск читает этот ticket и DV5-001, использует уже готовые raw/metadata/reference.
Пример: `build/dovi-native/compile/woka_p5_compare.exe <raw> <metadata.csv> <reference>
1920 1080 1 0 [hpe2020]`. `step=1` — полный кадр; `step=4` только предварительная выборка.
Для premap добавить к фильтру из manifest
`:custom_shader_path=natives/dovi/reference-premap.glsl`, запускать из корня проекта.
`premap.log` содержит зарегистрированные hook passes; `debug.log` — сгенерированный shader.
Невалидный `no-fbo.gbrpf32le` не использовать; причина записана в DV5-001.
