# DV5-001 — Покадровые метаданные и эталон P5

| | |
|---|---|
| Order | 1 |
| Depends on | Реализованный P5 guard |
| Feature | [Dolby Vision P5](../dolby-vision-p5.md) |

**Goal.** Получить проверяемые metadata и независимые reference-кадры для реализации цвета.
**Why now.** Это исследовательский этап: пользователь пока не получит воспроизведение P5.
Без него нельзя проверить коэффициенты и выбрать безопасный production-путь.

## Steps

1. Добавить отдельные C header/source accessor к существующей библиотеке. Вход: borrowed
   pointer, byte size, runtime version; выход: caller-owned фиксированный record с ABI version.
   До доступа проверить основной размер, каждый offset/length без integer overflow, alignment,
   число и размер ext blocks. Возвращать отдельные ошибки для несовместимой версии,
   неполных данных и неподдержанного mapping. Старый configuration accessor сохранить.
2. Копировать header, три curves с pivots и polynomial/MMR coefficients, обе матрицы,
   offsets, source min/max PQ и L1 при наличии. Ограничить counts и orders, проверить
   рациональные знаменатели и конечность результатов. Для unsupported особенностей явно
   вернуть статус; не объявлять поддержку по одному profile number.
3. Добавить test-only FFM binding и opt-in probe `woka.probe.dv5=true`. Использовать
   `FreshFrameGrabber(..., allowUnsupportedColour=true)` в RAW с четырьмя decode threads.
   Вывести таблицу для обоих P5 на 1/5/10 с, фактический PTS, формат/stride/битность,
   CRC RPU и копию parsed metadata. Повторить sequential и seek; сохранить data под build.
4. Добавить integration-тесты границ bridge: null, short, wrong version, ошибочные offsets,
   counts/orders и denominators. Синтетические FFmpeg records создавать C-кодом по
   именованным полям; не дублировать смещения FFmpeg в Kotlin fixture.
5. Подготовить отдельный FFmpeg/libplacebo reference. Зафиксировать источник, версии,
   SHA-256 и доступные параметры; выполнить одинаковые PTS без tone mapping в BT.2020 PQ
   с высокой точностью. Отключить dithering/filmgrain и явно задать масштаб/chroma policy.
   Сохранить SDR reference только как иллюстрацию. Если инструмент/режим не работает,
   записать фактическую ошибку и определить следующий проверяемый вариант.
6. Собрать bridge, выполнить tests/build, anti-FQN и лимит строк. Сохранить команды,
   таблицу наблюдений и точку продолжения; по реальным coefficients уточнить DV5-101.

## Files

- `natives/dovi/woka_dovi_metadata.h` (new): собственный output ABI.
- `natives/dovi/woka_dovi_metadata.c` (new): bounds validation и копирование named fields.
- `natives/dovi/CMakeLists.txt` (edit): включить accessor в существующую библиотеку.
- `natives/dovi/README.md` (edit): ABI и повторение сборки.
- `shared/src/jvmTest/kotlin/com/woka/probe/DoviMetadataAccess.kt` (new): test-only binding.
- `shared/src/jvmTest/kotlin/com/woka/probe/DoviP5ReferenceProbe.kt` (new): таблица/артефакты.
- `natives/dovi/tests/metadata_test.c` (new): typed fixtures и границы accessor через CTest.
  Это заменяет первоначально предложенный JVM boundary test: FFmpeg structs создаёт C-компилятор,
  test-only exports не попадают в DLL. Реальный JVM вызов проверяется отдельной RAW probe.
- `natives/dovi/reference.ps1` (new): воспроизводимый reference runner.

Если отдельный Kotlin-файл превышает 150 строк, выделить связную часть до добавления кода.
Библиотеку пробы сначала собирать в build через существующий `-OutDir`, чтобы не заменять
ресурс приложения до успешной проверки. Изменение guard в этот ticket не входит.

## Done when

- Есть реальные результаты для обоих P5, с версией декодера и точными PTS.
- Reference действительно применяет RPU; есть контроль применения/отключения на том же кадре.
- Native boundary tests проходят; ошибки не могут вернуть частично заполненный успешный record.
- `./gradlew.bat :shared:build` успешен; исследовательскую пробу запускать отдельно с opt-in.
- В feature doc записан следующий шаг с оставшимися неопределённостями.

## Execution record

2026-11-12: план подтверждён, реализация и измерения выполнены. Production guard не снят.

### Реальные данные

Оба файла: raw `yuv420p10le`, BL=10, full range=1, coefficient denominator=2^23,
source PQ min/max=7/3079, residual disabled=1, NLQ=-1, extended mapping flags=0.
Линейные матрицы и offsets читаются; L1 доступен. На шести кадрах MMR не встречается,
его копирование проверено синтетически. В Mystery Box меняется число pivots первого компонента.

| Файл | PTS, µs | RPU CRC32 | Pivots Y/Cb/Cr | L1 min/max/avg PQ |
|---|---:|---:|---|---|
| Mystery Box | 1001000 | 804280018 | 2/2/2 | 12/3026/1716 |
| Mystery Box | 5005000 | 3981044494 | 9/2/2 | 12/3026/1716 |
| Mystery Box | 10010000 | 1599892629 | 9/2/2 | 12/3079/1632 |
| Jellyfin 1080p | 1000000 | 2214549401 | 2/2/2 | 0/2457/1419 |
| Jellyfin 1080p | 5000000 | 3646421349 | 2/2/2 | 0/2457/1419 |
| Jellyfin 1080p | 10000000 | 3165124528 | 2/2/2 | 0/2457/1419 |

Все шесть полных metadata CSV и RPU совпали между sequential и seek. Все шесть RAW
кадров встроенного FFmpeg совпали SHA-256 с отдельно декодированными BtbN кадрами.
RAW сэмплы, metadata CSV и RPU: `shared/build/dv-p5/`.

### Reference provenance

Пользователь выбрал готовую сборку BtbN через GitHub. Загружен asset
`ffmpeg-n8.1-latest-win64-gpl-shared-8.1.zip` из release `latest` от 2026-11-12;
репозиторий BtbN перенаправляет на `FFmpeg-Builds/FFmpeg-Builds`.
Архив SHA-256: `10013fce74c3396a5ff2fd7fc6681808283131d7c1aa734f4c41662ec8b0f8bf`;
совпал с опубликованным `checksums.sha256`. Версия бинарника:
`n8.1.3-35-gf1acb0a266-20261112`, libplacebo `v7.387.0 (API v387)`, Vulkan RTX 5080.
Это отдельный процесс; его библиотеки не загружаются в Woka. JVM probe использует
встроенный libavutil `3938918` (60.26.102), соответствующий accessor headers.

Source SHA-256:
- Mystery Box: `3e610d3b1b11e9b802da66d69bd97f6371a2b114ee464a7e8517fe31d706cc9f`.
- Jellyfin: `c0dedeeef7d11c404955e126f9544318cdeb6eab62423528cfde9b567e8ca1a4`.

Артефакты `build/dv-p5-reference/`: `release.json`, `checksums.sha256`, `reference.zip`,
`binaries.csv` (хеши exe и DLL), `version.txt`; под `frames/` — manifest с фильтрами,
PTS и SHA-256, 6 planar GBR float32 PQ кадров, 2 контрольных без DV, 6 RAW кадров и логи.
Размеры native. Dithering, filmgrain, peak detection выключены, bilinear задан явно.
На кадрах 5 с mean absolute PQ difference DV on/off: 0.078328 (4K), 0.071487 (1080p),
NaN/Inf нет. Это проверка влияния RPU, не погрешность будущей реализации.

**Уточнение из DV5-101:** первый reference с `tonemapping=clip` всё равно выполняет
дополнительный color mapping. Debug shader и лог показали intermediate `rgba16hf`.
Float32 файл не означает float32 точность всей цепочки. Для изоляции P5 добавлен
`reference-premap.glsl`: MAIN сохраняет RGB PQ, OUTPUT выдаёт сохранённый снимок.
Первая проба такого захвата: `premap.gbrpf32le`, `premap.log` (Jellyfin, 5 с).
Его численную точность ещё проверяет DV5-101; исходные 6 reference не выдавать за
математически точный oracle до display mapping.

`disable_fbos=true` проверен и отвергнут: libplacebo сообщает `Failed rendering image`
при planar output, хотя ffmpeg завершился с кодом 0 и создал файл. `no-fbo.*` —
невалидные диагностические артефакты. Runner теперь отклоняет и ошибки renderer в логе.

### Проверки и повторение

- C bridge собран MSVC `/W4 /WX`; configuration accessor сохранён. DLL в
  `build/dovi-native/compile`, packaged DLL не заменена.
- 305 native boundary checks, zero failures; CTest `dovi_metadata_bounds` проходит.
- RAW JVM probe: BUILD SUCCESSFUL (повтор с сохранением raw — 12 с).
- `:shared:build :shared:jvmTest --tests 'com.woka.unit.*' --tests 'com.woka.integration.*'
  --tests 'com.woka.ui.*' --console=plain`: BUILD SUCCESSFUL, 1m22s,
  1677 tests / 356 suites, zero failures/errors/skips. Исследовательские probes в этот запуск не входят.
- Anti-FQN: только imports/packages; Kotlin 43 и 97 строк. `bash -n build.sh` прошёл.
- macOS/Linux compilation/runtime не запускались. Известные SettingsStore сообщения
  о временных test roots остаются, suite зелёный.

Повтор: собрать `natives/dovi/build.ps1 -OutDir build/dv-p5-native` (скрипт проверяет hash
upstream archive); в Gradle init для `shared` Test задать `woka.probe.dv5=true` и
`woka.probe.dv5.library` абсолютным путём этой DLL. В текущей сессии готов
`build/dv-p5-reference/init.gradle`, использующий `build/dovi-native/compile/woka_dovi.dll`.

```powershell
./gradlew.bat :shared:jvmTest --tests com.woka.probe.DoviP5ReferenceProbe -I build/dv-p5-reference/init.gradle --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File natives/dovi/reference.ps1 -Ffmpeg <path-to-BtbN-ffmpeg.exe> -Output <workspace-reference-directory>
```

Следующий этап — DV5-101: объяснить погрешность reference, проверить scalar math и
выбрать production-путь по измерению. P5 playback ещё не реализован.
