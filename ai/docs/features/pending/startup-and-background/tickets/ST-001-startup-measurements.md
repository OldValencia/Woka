# ST-001 — Измерения запуска и AOT

**Feature:** [Startup and background](../startup-and-background.md)
**Goal:** выбрать настройки по first-frame и RAM измерениям, сохранив playback.
**Authorization:** пользователь 2026-11-19 поручил анализировать и попробовать JEP 483.

## Сделано

- Прочитаны launcher, prewarm, IPC, exit gate, DI и владельцы основных native caches.
- `:desktopApp:createDistributable` успешно собрал актуальный packaged app.
- Добавлен отдельный Java/PowerShell smoke без изменения production source/flags.
- Compile, AppCDS record/use и AOT record/create/use прошли. Малый Xms совместим с созданным
  AOT-кэшем в smoke. Медианы и ограничения записаны в feature document.

## Files текущего среза

- `desktopApp/probes/startup/AotBootstrap.java`: headless initKoin и representative class load.
- `desktopApp/probes/startup/probe.ps1`: изолированный storage, explicit packaged classpath,
  packaged java, training/cache assembly и пять чередующихся повторов режимов.
- `desktopApp/build.gradle.kts`, `StartupPrewarm.kt`, `SingleInstance.kt`, `main.kt`:
  прочитанные точки интеграции, сейчас не изменены.

## Повторение

Из root:

```powershell
.\gradlew.bat :desktopApp:createDistributable --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File desktopApp/probes/startup/probe.ps1
```

Нужны `javac` и `jar` JDK 25 в PATH; выполнять саму пробу скрипт будет на packaged runtime.
Выход: `build/startup-analysis/*.log`, `measurements.csv`, `bootstrap.aot`, `bootstrap.jsa`.
Скрипт не запускает окно и не меняет пользовательские настройки. Training специально
дорогой; его время не включать в repeated startup сравнение. Следующий запуск после обновления
приложения/JDK требует пересоздать cache. Эти cache-файлы в дистрибутив не включены.

## Дальше

1. Добавить opt-in first-draw instrumentation для актуального packaged Woka, фиксировать
   process start/main/AWT ready/window ready/first painted content. Отдельно file-open.
2. Обучить AppCDS/AOT на одинаковом реальном UI сценарии с изолированными settings.
   Проверить cache hit в production, не принимать silent fallback за AOT результат.
3. Сравнить текущий AppCDS, AOT, AOT+Xms32/64m. Не переносить выборку из headless smoke
   на первый кадр. Cold filesystem cache и warm cache отмечать отдельно.
4. Замерить память компактного prototype после disposal UI/медиа и повторное открытие;
   закрытие с unsaved edits/активным save должно пройти существующий gate.
5. По результатам включить только выигравшие flags, проверить 4K/8K playback/GC и regressions.

`main.kt` 144 строки, `ImageBitmapCache.kt` 147 строк по wc: новые обязанности вынести
в отдельные helpers, не переполнять файлы. Production reflection не вводить.

**Done when:** есть воспроизводимые first-frame/idle-memory результаты на актуальной сборке,
cache invalidation/fallback проверены; решение о default flags опирается на них.

## Пауза по просьбе пользователя — 2026-11-19

Пользователь сообщил об остатке 2% лимита и попросил сохранить работу, продолжить позже.
**Новые прогоны не запускать до возобновления.** Последнее уточнение: задержка 1–3 секунды
наблюдается в **установленном Woka.exe / двойном клике**, не в Gradle/IDE. Цель — первый
полезный кадр со скоростью современных desktop viewers; приоритет низкой RAM остаётся.

### Что добавлено после headless smoke

- `shared/src/jvmMain/kotlin/com/woka/core/system/StartupTiming.kt`: временная opt-in
  диагностика ST-001. Property `woka.probe.startup.output=<csv>` включает отметки относительно
  ProcessHandle.startInstant; `woka.probe.startup.exitMs` завершает только диагностический
  процесс после shell-draw. Без properties автоматического выхода/записи нет.
- Hooks: `desktopApp/.../main.kt` (main/ipc/di/window-bound/shell-draw),
  `StartupPrewarm.kt` (awt/skia), `shared/.../viewer/shell/MediaViewerFrame.kt`
  (image-draw/video-draw после drawContent). Это время CPU draw submission, **не GPU scan-out**.
- `desktopApp/probes/startup/first-draw.ps1`: создаёт JPEG 1920×1080 в build и изолированный
  storage, ставит `.context_menu_initialized`, чтобы не менять registry; запускает packaged
  **java.exe** с настоящим MainKt. Сравнивает JDK CDS, AppCDS и AOT, затем AOT+Xms32m;
  собирает стадии и peak private/working bytes. На timeout останавливает только свой PID.
- `:desktopApp:createDistributable` после hooks **прошёл** (18s). Основные production flags
  по-прежнему не изменены. Полная регрессия после hooks не запускалась; до них 1689 tests pass.

### Первый результат и проблема harness

Один запуск JDK CDS (без application archive), файловый кэш не очищался:
main 243.241 ms; ipc 334.272; awt 425.771; di 432.727; skia 440.125;
window-bound 965.232; **shell-draw 1056.139 ms**. `image-draw` отсутствует — нельзя считать
первый shell кадр первым кадром открываемого JPEG. Основной наблюдаемый интервал di→window
~532 ms, но причины ещё не профилированы. Не делать вывод о bottleneck по одному запуску.

Первый скрипт сообщил ошибку после завершения процесса при пустых stdout/stderr:
вероятная причина — null ExitCode у Start-Process после Refresh. **Исправлено в скрипте,
ещё не проверено:** сохранить `$process.Handle` сразу после запуска; добавить exit code в ошибку.
Также увеличено окно ожидания после shell-draw с 1200 до 4000 ms, чтобы не завершать процесс
до появления изображения. Training остаётся 5000 ms. Последний повтор пользователь прервал;
новых результатов/`results.csv` нет. Перед повтором проверить, не остался ли процесс пробы;
не останавливать пользовательский Woka или чужие Java-процессы.

Артефакты: `build/startup-first-draw/jdk-cds.csv`, `.out.log`, `.err.log` (логи пустые),
`media/sample.jpg`, `storage/settings.json`. Headless результаты остаются в
`build/startup-analysis/measurements.csv` и feature document.

### Установленная версия

Найдена `%LOCALAPPDATA%/Woka/app/Woka.cfg`: app-version **1.0.7**, AppCDS auto-create включён,
`woka.jsa` существует, 45,481,984 bytes, mtime 2026-11-10. Установку не меняли. Наличие
архива не доказывает cache hit — это надо проверить отдельно. Нынешняя свежая сборка 1.0.8
под `desktopApp/build/compose/binaries/main/app/Woka`; её java.exe probe не включает стоимость
native Woka.exe launcher. Требуется отдельно проверить EXE/Explorer путь и правильность media draw.

### Следующий конкретный шаг

1. Запустить исправленный first-draw harness, сначала мало повторов; дождаться image-draw.
   Если его снова нет — проверить fixture/route и окно, а не предполагать медленный decoder.
2. Убедиться в реальном AOT cache hit, получить AppCDS/AOT first shell **и media** draw.
3. Снять/сохранить кадр окна для контроля, при необходимости профиль interval di→window.
4. Проверить actual installed archive hit и native launcher отдельно; измерения текущей
   сборки не объявлять объяснением 1–3s установленной 1.0.7 без этой проверки.
5. Только затем выбирать исправление; убрать временную диагностику после расследования.

Команды:
```powershell
.\gradlew.bat :desktopApp:createDistributable --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File desktopApp/probes/startup/first-draw.ps1 -Rounds 3
```
GUI/script требуют escalated tool; соответствующий prefix уже одобрен. Обязательный
anti-FQN и wc выполнять после правок. `main.kt` сейчас **149 строк**, helpers выносить отдельно.
Startup/RAM изменения **не закоммичены/не запушены**; разрешение было только на DV P5.
`.claude/settings.local.json` не относится к работе. `next_steps.md` игнорируется Git.

## Возобновление 2026-11-19 вечером

Пользователь явно попросил продолжить; ограничение предыдущей паузы снято.
Исправление ExitCode проверено: harness завершился успешно и получил image-draw.
Первый запуск без application archive: shell 2336 ms / JPEG 3527 ms; обучение AppCDS/AOT
~8 s shell / ~10 s JPEG (не включать в runtime сравнение).

По пять чередующихся запусков generated Woka.exe, OS cache не очищался:

| Режим | Shell median, ms | JPEG median, ms |
|---|---:|---:|
| AppCDS | 468.500 | 772.816 |
| AppCDS + Xms32m | 468.785 | 780.775 |
| AOT | 419.497 | 678.341 |
| AOT + Xms32m | 417.693 | 684.676 |

Артефакты: `build/startup-first-draw/exe-results.csv`. В этой серии отсчёт **от дочерней JVM**,
не от родительского launcher. RAM 2/10 MiB в CSV ошибочна: jpackage EXE запускает ещё один EXE,
и первоначальный harness опрашивал только маленький родительский процесс. Не использовать эти
memory-колонки. В предыдущем java.exe smoke peak private 910/922/444 MiB для CDS/AOT/AOT-small;
Working Set 317/308/295 MiB. Это пик открытого JPEG, не фон и не retained memory.

AOT log подтверждает `Using AOT-linked classes: true`; есть warnings о трёх adapter stubs,
не мешающие запуску. Они сохранены в `aot-*.out.log`; bundled runtime Oracle 25.0.2.

Read-only проверка **установленной** 1.0.7 с её полным classpath, `-Xshare:on -version`:
dynamic regions успешно mapped, archive принимается. Runtime установленной версии —
**Temurin 25.0.5+8**, отличается от рабочей сборки. Лог
`build/startup-first-draw/installed-cds-validation.log`. Installed files не изменялись.

Harness дополнен: `-LauncherExe` временно меняет только generated cfg и восстанавливает bytes
в finally (SHA256 до/после первой серии совпал: 437CB81C43343CF7C575D6B59D84F8C4D23E9D8E61235FC9284497A0CEFAD5B9).
`-SkipTraining` использует существующий cache, `-Capture` делает PrintWindow только окна пробы.
StartupTiming теперь пишет PID дочерней JVM и умеет считать от переданного launch epoch;
измеряются source-size, still-color, preview/full и FFmpeg log init. Это TEMPORARY ST-001.
Сборка после новых hooks прошла (44s); anti-FQN: только imports/package.
Текущая уточняющая серия — `build/startup-native-v2`, 3 повторения, новый cache после rebuild.

Уточнённая серия `startup-native-v2` завершилась: launcher→JPEG median **813.6 ms CDS /
711.0 ms AOT / 714.2 ms AOT+Xms32m** (3 запуска). Median peak private **911 / 915 / 440 MiB**;
working set **314 / 301 / 291 MiB**. Это не compact idle. PrintWindow кадры показывают тестовый
JPEG, не placeholder. CSV и PNG сохранены. Чтение CSV для capture исправлено на FileShare.ReadWrite:
первая попытка споткнулась о одновременную запись, затем серия успешно завершилась.

Подробные стадии AOT: shell ~437 ms → source-size-start ~559 → FFmpeg init ready ~652 →
still-color ready ~698 → image ~711. Из ~274 ms после shell, ~92 ms — FFmpeg init на первом
обращении к MediaThumbnails, ~44 ms — первый color probe. Это основание эксперимента:
`MediaStartup.prewarm()` (FFmpeg logging + avformat version) в daemon worker одновременно с окном,
только для прямого запуска с существующим медиафайлом, только после primary IPC check.
Пока включается opt-in `woka.probe.startup.mediaPrewarm=true`. Серия `build/startup-prewarm`
сравнивает on/off на одной сборке через `-ComparePrewarm`; production default пока off.

### Принятое изменение: загрузка native media одновременно с окном

Серия `build/startup-prewarm/exe-results.csv`, одинаковая сборка, 3 чередующихся запуска:

| Режим | Launcher→shell median, ms | Launcher→JPEG median, ms |
|---|---:|---:|
| AppCDS, прежняя загрузка | 486.988 | 802.042 |
| AppCDS, media prewarm | 519.526 | 688.762 |
| AOT, прежняя загрузка | 437.434 | 702.925 |
| AOT, media prewarm | 463.580 | 611.502 |

Выигрыш до полезного кадра ~113 ms с AppCDS и ~91 ms с AOT; пустой shell появляется на
~26–33 ms позже из-за параллельной работы. Выбор сделан по JPEG, как попросил пользователь.
Peak memory практически одинаковая; prewarm не оставляет постоянно работающий поток.

`StartupPrewarm.media(args)` теперь включён по умолчанию только после успешного primary IPC
и только для существующего медиафайла. Проверка существования и native loading в daemon thread;
folder-only/secondary launch не запускают этот worker. `MediaStartup.prewarm()` загружает
FFmpeg logging и avformat, не открывая декодер или файл. Для воспроизводимого A/B можно отключить
`-Dwoka.startup.mediaPrewarm=false`; probe `-ComparePrewarm` делает это автоматически.

Убраны диагностические обёртки RealViewerThumbnails и отметки quietFfmpegLogs; механический поиск
подтвердил отсутствие. Остаются opt-in main/window/draw hooks ST-001 для дальнейшего AOT/RAM
исследования; без property они не пишут файлы и не завершают приложение.
Ни Xms, ни AOT cache flags в дистрибутив пока не включены. Это не обещание cold-filesystem
запуска за 612 ms: cache ОС не сбрасывался; обучение archive всё ещё дорогое (~9 s JPEG).

После финальных правок запущены `:shared:jvmTest --tests '*Test' :desktopApp:createDistributable`.
Результат записать ниже после завершения. Следующий шаг: проверить финальную packaged сборку,
затем AOT lifecycle без обучения на критическом пути и compact idle RAM/reopen.

### Финальная проверка и точка продолжения — 2026-11-19, 20:24 CET

- `:shared:jvmTest --tests '*Test' :desktopApp:createDistributable` — **BUILD SUCCESSFUL**, 2m 1s.
  XML: **1689 tests / 361 suites / 0 failures / 0 errors / 0 skipped**. В shutdown тестовой JVM
  есть SettingsStore FileNotFoundException для уже удалённых temp roots; тесты зелёные, к startup
  правкам это не относится, ничего в SettingsStore не менялось.
- Финальный production-default media prewarm, обычный AppCDS, настоящий Woka.exe:
  JPEG **691.823 / 691.777 / 742.363 ms**, median **691.823 ms**. Shell median 523.502 ms.
  Без application archive в этой серии JPEG 1326.873 ms; CDS training JPEG 8835.012 ms.
  Не выдавать обучающий запуск за обычный и не обещать устранение first-install задержки.
- `build/startup-final/exe-results.csv`, PNG и логи сохранены; финальный JPEG на PrintWindow
  просмотрен, картинка действительно видима. Основа измерения — CPU draw submission,
  не точное время появления пикселя на мониторе. Кэш файловой системы не очищался.
- Generated cfg восстановлен byte-for-byte, SHA256
  `D18256634D5DA68151D3479460DA83C6F0991ECC5DF1B3DED99BD51234E1B38C`.
- Для ручной проверки свежей сборки `build/startup-final/ui.jsa` скопирован в
  `desktopApp/build/compose/binaries/main/app/Woka/app/woka.jsa` (только build artifact).
  `-Xshare:on -version` с полным packaged classpath принял dynamic archive, лог
  `build/startup-final/distribution-cds-validation.log`. Это избавляет именно эту собранную
  копию от повторного долгого training; перенос в MSI/другой JDK не проверен.
- `main.kt` 150 строк; `StartupPrewarm.kt` 52; `MediaStartup.kt` 12; `StartupTiming.kt` 44;
  `MediaViewerFrame.kt` 126. Anti-FQN чист, git diff --check без ошибок.
- Установленная Woka не обновлялась, registry/user settings не менялись. Startup работа
  не коммичена и не запушена. `.claude/settings.local.json` не трогали.

Следующему проходу:
1. Не повторять headless smoke: реальный UI/EXE уже измерен. AOT+prewarm ~612 ms подтверждён
   экспериментом, но **AOT ещё не включён в packaged default**. Старые экспериментальные AOT
   caches после последней пересборки устарели; для повторения нужны новое record/create.
2. Главный незакрытый startup риск — дорогое обучение archive на первом запуске после установки/
   обновления. Разработать cache lifecycle вне критического пути; invalidation/fallback и
   установленные timestamps/новый runtime проверить. Не подменять это включением вечного фона.
3. Xms32m экономит private commitment; физическая RAM меняется гораздо меньше. Defaults heap
   пока прежние. Проверить 4K/8K/GC отдельно перед включением.
4. ST-002 compact idle/reopen ещё не реализован. Нужны disposal/join heavy owners, cache ownership,
   settings flush и сохранение exit gate; затем реальный RAM после закрытия.
5. Main/window/draw instrumentation ST-001 остаётся opt-in до завершения исследования, подробные
   native timing hooks уже убраны. Перед shipping убрать оставшийся temporary instrumentation.

Повтор финального замера:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File desktopApp/probes/startup/first-draw.ps1 -Output build/startup-final -Rounds 3 -LauncherExe -CdsOnly -Capture
```
Без rebuild можно добавить `-SkipTraining`. Для AOT убрать `-CdsOnly`; для on/off prewarm
добавить `-ComparePrewarm` и выбрать новый output. Не собирать приложение одновременно с probe,
поскольку тот временно меняет generated cfg и затем восстанавливает его.

## RAM: разбор и первые исправления — 2026-11-19, поздний вечер

Пользователь попросил резать RAM всеми средствами. Замеры — packaged java.exe, изолированный
storage, NMT; «физ.» = private working set (столбец «Память» Диспетчера задач).

**Состав чистого Welcome (376 МиБ private):** JVM 164 (heap 31, G1 tables 62, class metadata ~54,
JIT 11); вне JVM ~212 — GPU-драйвер D3D12 + Java2D D3D9 (~90–125), Skia/AWT/DWrite/OS. Java2D D3D
off: −34 private / −15 физ., но +70 ms до JPEG — не включено. `:desktopApp:run` в Диспетчере
группируется с Gradle и Kotlin daemon: в той группе Woka — только строка «Woka».

**Найденные дефекты и исправления (все тесты прошли: 1736, 0 failures):**
- `FrameRetention` не закрывал кадры, пока ничего не рисует (спиннер, другой экран, свёрнуто):
  `acks=0`, 886 кадров отброшены на GC, 4K открытие 5–21 GB private в зависимости от момента GC.
  Теперь при 16 неотрисованных кадрах `deliverPicture` отправляет последний bitmap вместо нового
  (`FrameRetention.isBacklogged`, `FramePacer.lastConverted`); время, EOF и cuts идут дальше.
  Первая версия — ожидание декодера — ломала headless playback (6 тестов), отвергнута.
- JavaCV открывает декодер с `thread_count(0)` (auto, 17 потоков). Постеры, still facts, cover art,
  info panel теперь 2 потока (`LIGHT_DECODE_THREADS`). Папка 16 тяжёлых видео со свежими
  постерами: пик 5.0/3.75 GB → 1.6/1.3 GB; время постеров не выросло.
- Heap/native ждали GC в простое: живых объектов ~15 MB, GC не запускался. Флаги `-Xms16m`,
  `G1PeriodicGCInterval=5000`, `ParallelGCThreads=4`, `ConcGCThreads=1`. До исправления
  retention малый Xms давал 12–20 GB на 4K — **не включать малый heap без этого исправления**.

| МиБ private/физ. | До | После |
|---|---:|---:|
| Welcome | 369/167 | 352/164 |
| Папка DV samples, простой | 735/482 | 420/224 |
| 4K AVC, игра (2 прогона) | 1.7–18 GB / 0.7–17 GB | 1503/679 · 1664/681 |
| 4K, пик открытия | 5–21 GB | 2.2–2.5 GB / 1.5–1.6 GB |
| После выхода из видео | 1086/360 | ~1048/337 |

**Открыто:** остаток после видео (~860 MB commit / ~190 MB физ. вне JVM, GC не освобождает —
предположительно GPU-кэш Skia и FFmpeg, не подтверждено); 26 живых кадров в игре (канал 8 +
`CLOSE_MARGIN_FRAMES` 16 ради fullscreen handover); `ImageBitmapCache` вытесняет без `close()`;
бюджет 256 MB не менялся; AOT cache lifecycle и JPEG без FFmpeg для старта.

### Остаток после видео — 2026-11-19

Heap dump после Esc (разбор путей до GC root скриптом по HPROF): **закрытый `VideoPlayerState`
оставался жив** через `startSubtitleDecoderLoop` — бесконечный цикл на долгоживущем
`PipManager.pipScope`, который `dispose()` не отменял. Вместе с плеером жили `FrameRetention`
(26 кадров) и полный канал. Цикл теперь дочерний к job декодера (`SupervisorJob(decoder)`):
после Esc живых плееров 0, физ. 348 → 251 МиБ, private 1058 → 960.

Оставшийся commit после видео: блоки 256+128+64+64 MiB появляются только с GPU-рендером
(с `skiko.renderApi=SOFTWARE` их нет) — пулы загрузки D3D12/Skia; физически ~50 MiB.
`DirectContext.setResourceCacheLimit` есть, но контекст доступен Compose только изнутри —
без reflection не достать, не трогали. Физический остаток над Welcome ~90 MiB: метаспейс и
JIT кода плеера, DLL FFmpeg.

### Запуск: JPEG без FFmpeg и AOT как поставляемый кэш — 2026-11-19

Замер: packaged java.exe из PowerShell (абсолют завышен на ~200 ms запуском процесса, сравнивать
только разницы), новые heap-флаги, изолированный storage, 5 запусков, медианы, обученный AppCDS.

- **JPEG больше не грузит FFmpeg.** `readStillColor` отвечает SDR для jpg/jpeg/jfif/jpe без
  демультиплексора (у JPEG нет поля для PQ/HLG); `quietFfmpegLogs` стал ленивым и вызывается в
  точках входа FFmpeg, а не из инициализатора `MediaThumbnails`; prewarm пропускает JPEG.
  Shell 763 → 733, JPEG 937 → 894 ms; модулей при открытом JPEG 110 (как Welcome), ранее +71 DLL.
- **AOT (JEP 483) на реальном UI:** JPEG 896 → 809 ms в папке сборки; в перенесённой копии
  дистрибутива 946 → 844 ms (−102). Обучение одним прогоном `-XX:AOTCacheOutput`, 66 MB.
- **Кэш переносим и не зависит от mtime jar.** Bundled Oracle 25.0.2: после смены mtime и размера
  jar классы всё равно `source: shared objects file`; после копирования всего дистрибутива в
  другую папку — `Longest common prefix substitution: yes`, 843/843 классов Woka из кэша.
  Значит, кэш можно собирать при упаковке и класть в установщик — в отличие от прежнего вывода
  про AppCDS. Отсутствующий или битый `-XX:AOTCache` даёт ошибку и запуск **вообще без CDS**,
  поэтому флаг допустим только вместе с гарантированно собранным файлом.
- Первая полная сборка после правки JPEG упала на `:shared:jvmTest` без упавших тестов и без
  hs_err; повторный прогон 1736/0. Причина не установлена.

### AOT-кэш в установщике — реализовано 2026-11-19

Решение пользователя: обучать при упаковке. `desktopApp/build.gradle.kts` (Windows):
`trainAotCache` запускает собранный `createDistributable` его же java и опциями из `Woka.cfg`
(без AppCDS и `-Xshare`, JVM запрещает их рядом с `AOTCacheOutput`), изолированный storage с
`.context_menu_initialized`, сгенерированный JPEG и `-Dwoka.aotTraining.exitMs=1500`;
`AotTraining` (core/system, постоянный) выходит через `exitProcess` после первой отрисовки
картинки — кэш пишется только при штатном выходе. Выход: `build/aot-training/woka.aot` (66 MB).
`aotAppImage` (Sync) копирует образ, кладёт кэш в `app/`, в `Woka.cfg` заменяет AppCDS-флаги
на `-XX:AOTCache=$APPDIR/woka.aot`; `packageMsi` пакует его через `appImage` (каталог
`aot-image/Woka`). `createDistributable` по-прежнему с самосоздаваемым AppCDS.

Проверено: настоящий `Woka.exe` из AOT-образа — 863/863 классов Woka из кэша; MSI распакован
`msiexec /a` в scratchpad — 855 из кэша, 2 из jar (не встретились при обучении); 153 file
associations переданы jpackage. Цепочка `trainAotCache → aotAppImage → packageMsi` ~1 мин.

**Не проверено:** обучение в GitHub Actions `windows-latest` (рабочий стол/GPU раннера) — первый
прогон CI покажет; при неудаче задача падает с путём к `training.log`. macOS/Linux без изменений.

Инцидент: пробный запуск `Woka.exe` с `JDK_JAVA_OPTIONS` (лаунчер jpackage их не читает) открыл
Woka на настоящем storage и записал `lastSession` = папку пробы; значение восстановлено на
`C:\Users\OldValencia\Videos`. Для проверок лаунчера опции дописывать в `Woka.cfg` образа.

### Старт окна: Java2D D3D9 → предзагрузка D3D12 — 2026-11-19

JFR (полный JDK 25.0.2 + свой AOT-кэш с `--add-modules=jdk.jfr`, выборки 1 ms; на Windows
реальная частота ниже, длительности брались по интервалам меток времени): критический путь —
AWT 120 ms (почти всё `D3DGraphicsDevice.initD3D`, окно его ждёт), `Window.pack` ~130 ms
(skiko `chooseAdapter` ~28, `createDirectXDevice` ~55), композиция + первый рендер ~180,
первый D3D flush. Java2D D3D9 Woka не нужен (всё рисует skiko D3D12), но он попутно грузил
драйвер NVIDIA — поэтому одно `-Dsun.java2d.d3d=false` было медленнее.

Решение: `-Dsun.java2d.d3d=false` + `D3d12Prewarm` (core/system/windows, FFM): параллельно
с окном создаёт и освобождает D3D12-устройство на адаптере по умолчанию. 5 чередующихся
запусков, каждая конфигурация со своим AOT-кэшем, мс от `main`:

| Конфигурация | AWT | Shell | JPEG |
|---|---:|---:|---:|
| Как было | +149 | +326 | +453 |
| Только d3d=false | +32 | +365 | +495 |
| d3d=false + D3D12, освободить | +36 | +214 | +346 |
| d3d=false + D3D12, держать | +37 | +214 | +344 |

PrintWindow: окно с JPEG рисуется правильно. Private с открытым JPEG 387 → 364 MiB, физ. 201
без изменений. Не проверено: ноутбуки с двумя GPU (если skiko выберет не адаптер по умолчанию,
предзагрузка просто не поможет), macOS/Linux не затронуты.

### Первый рендер: JPEG в первом кадре — 2026-11-19

Метки в эффекте просмотра показали: загрузка (size/color/preview) ~10 ms, но эффект стартовал
через ~120 ms после первого кадра — `activeItem` ждал листинг папки (ViewModel на Main →
IO → Default → Main). Затем, когда эффект стал стартовать раньше, превью декодировалось за
2 ms (+203 → +205), а продолжение ждало EDT до +304: EDT рисовал первый кадр (композиция,
D3D flush), и картинка попадала только во второй кадр.

1. `FolderScreen`: пока листинг пуст, просмотр получает сам открытый файл
   (`mediaItemOrNull`); `MediaItem.Image` — data class по файлу, приход листинга эффект не
   перезапускает.
2. `MediaStartup.prepareStill` (поток prewarm, после `initKoin` — HDR-режим и правки уже
   установлены) выполняет те же `MediaThumbnails.sourceSize/stillColor/loadPreview`;
   эффект забирает готовое через `takeStill` без приостановки. Полный декод — как раньше.
   Вызов `StartupPrewarm.media` в `main.kt` перенесён после `initKoin`.

Медианы 5 запусков, мс от `main`: shell +216, **image +215** (было +331 до правок, +453 в
начале этого прохода). PrintWindow: JPEG «Fit · 63%» (размер известен в первом кадре),
прямое открытие видео играет. Временные метки удалены.

### Память воспроизведения 4K — 2026-11-20

Отдельная папка с одним 4K AVC (без генерации постеров соседей), NMT + `QueryWorkingSetEx` по
каждой аллокации + HPROF. Резидентно ~575 MiB: GPU-пулы 512 commit / 64 резидентно; Java heap
~105 (422 commit до правки); 4–16 MiB ×18 — 4K Y-плоскости декодера; 1–4 MiB ×73 — U/V и
RGBA-кадры размера окна (1204×677); JavaCPP-буферов ≥1 MiB, видимых из Java, только два по 3.1.

1. **Без Java staging-массива:** `FramePacer.convert` — `allocPixels` + одна копия через FFM
   из буфера FFmpeg в пиксели bitmap. Heap при игре 422 → ~110 MiB commit, сборки за 20 s
   сотни → 40, CPU 7.7–8.7% (было 8.1–9.1).
2. **`CLOSE_MARGIN_FRAMES` 16 → 4.** Временный счётчик в `acknowledge`: 6 переключений F
   (fullscreen туда/обратно), свернуть/развернуть/maximize — максимальное отставание отрисовки
   от новейшего кадра **1**. `AmbientSampler` делает `Image.makeFromBitmap` (держит пиксели сам).

| 4K AVC, MiB | private | физ. | CPU |
|---|---:|---:|---:|
| До (сегодня, после retention) | ~1480 | ~571 | ~8% |
| После, окно | 1101 | 409 | 8.5% |
| После, fullscreen | 1076 | 391 | 4.9% |
| После выхода из видео | 918 | 238 | — |

Не трогали: потоки декодера (4 нужны для 4K60), GPU-пулы skiko (без reflection недоступны).

Полный прогон тестов поймал аварию JVM (`EXCEPTION_ACCESS_VIOLATION` в `jlong_disjoint_arraycopy`,
запись за блок): Skia выделяет `stride × (h − 1) + width × 4`, а копия шла `stride × h` —
переполнение на выравнивание последней строки. Копия теперь ровно `Pixmap.computeByteSize()`
с проверкой; повторный прогон 1736/0, без hs_err.

### Сетка папки: листание 742 скриншотов — 2026-11-20

Изолированный storage, `Pictures\Screenshots` (740 медиа), прокрутка колесом через
`PostMessage(WM_MOUSEWHEEL)` до конца, затем простой. МиБ private/физ.: старт 401/225 →
пик 784/423 → простой ~700/338. Heap dump: Coil `RealStrongMemoryCache` 494 записи,
76.6 MiB (+ weak 11.4), `maxSize` 80 530 636 = 5% от `-Xmx`; кэш заполнен. Остаток в простое
(+113 физ.) — в основном он. Миниатюры картинок Coil декодирует из исходного файла (дискового
кэша миниатюр для картинок нет), поэтому урезание кэша = повторные декоды при прокрутке назад.
Код не менялся.

### Native Image — первая проба (2026-11-20)

Oracle GraalVM 25.0.4 (архив Oracle, SHA256 сверен) в `%USERPROFILE%\.jdks\graalvm-extract`, без
установки. Метаданные — `native-image-agent` на четырёх сценариях (JPEG, видео, Welcome, сетка):
466 reflection, 160 JNI, 481 ресурс, 12 FFM downcall + 2 upcall. MSVC — VS 18 (cl 19.50); автопоиск
GraalVM знает только VS 2022, поэтому сборка внутри `vcvars64.bat` (+ системный PATH). Сборка
~65 s, 7.5 GB RSS. Запуск: нужен `-Djava.home=<папка образа>` с `bin\jawt.dll` (иначе skiko не
находит jawt). JPEG открывается и рисуется, выход штатный. Видео в образе не проверялось.

5 чередующихся запусков, от старта процесса, медианы (JVM — дистрибутив + свежий AOT-кэш):

| | main | di | окно | JPEG | пик физ. |
|---|---:|---:|---:|---:|---:|
| Native | 253 | 274 | 552 | 561 | 96 MiB |
| JVM+AOT | 340 | 400 | 527 | 557 | 183 MiB |

Образ 189 MB, из них 85 MB — DLL FFmpeg/LibRaw, вшитые конфигом JavaCPP; с
`-H:ExcludeResources=.*\.(dll|so|dylib)$` — 107 MB. Время до `main` от этого не изменилось
(~250 ms), hello-world в той же папке — 17 ms: причина в самом образе Woka, не установлена.

### FFmpeg: загрузка = загрузчик JavaCPP, а не DLL

Проба на java.exe дистрибутива: первый `Loader.load(avutil)` ~350 ms, каждая следующая
библиотека 8–14 ms. По логу JavaCPP — ~60 предзагрузок (45 UCRT `api-ms-win-crt-*`, MSVC
runtime, libomp), каждая с поиском по jar, 5–7 ms. DLL из распакованной папки +
`-Dorg.bytedeco.javacpp.pathsFirst=true -Dorg.bytedeco.javacpp.findLibraries=false`:
загрузка 460 → 137 ms, открытие + декод 4K-кадра 103 → 90 ms (4 пары запусков, кадр 3840×2160
получен). В приложении jar ~80, поиск дороже, чем в пробе с 5 jar. Код не менялся.

### JavaCPP из папки — реализовано 2026-11-20

`desktopApp/build.gradle.kts` (Windows): `prepareAppResources` распаковывает `.dll` из
`*-windows-x86_64.jar` (JavaCPP, FFmpeg, LibRaw — 68 файлов, 78 MB, без коллизий имён) в
`resources/javacpp`; лаунчер получает `JAVACPP_FROM_FOLDER` (`java.library.path` на эту папку,
`pathsFirst`, `findLibraries=false`). В Woka (5 чередующихся запусков, свой AOT-кэш на вариант):
прямое открытие видео — первый кадр 448 → 430 ms (FFmpeg и так грузился параллельно с окном);
папка с видео, свежий постер — первый постер 790 → 643 ms. MSI распакован `msiexec /a`:
4K-видео играет, `avcodec-62.dll` загружен из `app\resources\javacpp`. DLL остаются и внутри jar,
установщик тяжелее. Тесты 1736/0.

### Native Image: ~250 ms до `main` — не объяснено (2026-11-20)

Hello-world в той же папке — 17 ms до `main`. У Woka-образа ~250 ms и с DLL (189 MB), и без
(107 MB), и из папки проекта вместо Temp, и с `/DYNAMICBASE:NO` (`dumpbin`: relocations stripped)
— то есть не вшитые ресурсы, не ASLR-релокации, не расположение файла. Опрос `Process.Modules`
ненадёжен (метка `main` пришла раньше импортов). Попытки воспроизвести на синтетике не удались:
`--initialize-at-build-time` не положил массив в кучу образа, `-H:IncludeResources` не вшил
100 MB blob. Нужен профилировщик ОС (ETW/WPA или профилировщик VS). Видео в образе не проверено.
Образ в scratchpad; GraalVM в `%USERPROFILE%\.jdks\graalvm-extract`.

## ТОЧКА ПРОДОЛЖЕНИЯ — 2026-11-20 (конец сессии)

**Сделано и проверено автором руками** (всё не закоммичено, git не трогали): retention/backlog
кадров, 2 потока лёгким декодерам, GC-флаги, утечка плеера через субтитры, JPEG без FFmpeg,
AOT-кэш при упаковке, `sun.java2d.d3d=false` + `D3d12Prewarm`, превью в первом кадре, кадры
без staging-массива + margin 4, JavaCPP из папки. Тесты 1736/0 на последней сборке.

**Следующий шаг (решение автора — доделывать Native Image):** автор ставит Windows Performance
Toolkit (Windows ADK). Снять ETW-трейс запуска `ni\out-nodll\woka.exe` (`wpr -start CPU`,
запуск, `wpr -stop`), найти ~250 ms до `main`. Затем: видео/DV в образе, сборка образа как
Gradle-задача, упаковка (jawt в `bin\`, `-Djava.home`), сравнение с JVM+AOT через EXE.
Не повторять: релокации/ASLR, вшитые DLL, Temp/AV, опрос модулей — исключены выше.

**Окружение (не открывать заново):** GraalVM 25.0.4 в `%USERPROFILE%\.jdks\graalvm-extract`;
сборка образа — внутри `vcvars64.bat` VS 18, PATH сессии без System32 (добавлять вручную),
`cmd` через `$env:ComSpec`, аргументы через `@argfile`, `-H:ExcludeResources=.*\.(dll|so|dylib)$`;
конфиг агента — `scratchpad\ni\config` (сессионный scratch, пересоздать скриптом агента:
4 сценария через `-agentlib:native-image-agent=config-merge-dir`). Лаунчер jpackage игнорирует
`JDK_JAVA_OPTIONS` — для проб дописывать опции в `Woka.cfg` образа, иначе пишется настоящий
`settings.json`. В процессы автора внедряется Windhawk (моды) — учитывать в трейсах.

### До / после (база — ветка на старте ST-001 ≈ поведение 1.0.7)

| Метрика | Было | Стало |
|---|---:|---:|
| EXE→JPEG, тёплый кэш ОС | 802 ms (AppCDS) | 612 ms AOT (замер до d3d/first-frame; итог через EXE не перемерян) |
| От `main` до JPEG (java.exe) | +453 | **+215** (JPEG в первом кадре) |
| Первый запуск после установки/обновления | ~8.8 s (обучение AppCDS) | без обучения (AOT в MSI) |
| Welcome, private/физ. MiB | 376 / 176 | 352 / 164 |
| Папка 16 тяжёлых видео: пик / простой | 5000/3750 · 735/482 | 1685/1377 · 420/224 |
| 4K: пик открытия | 5–21 GB | 2.2–2.5 GB / 1.5 |
| 4K: игра | 1.7–18 GB / 0.7–17 GB | 1101 / **409** |
| После закрытия видео | 1086 / 360 | 918 / **238** |
| Первый постер видео в папке | 790 ms | 643 ms |
| Native Image (эксперимент): пик физ. при JPEG | 183 (JVM) | 96 |
