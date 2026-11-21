# Ускорение запуска и экономный фоновый режим

## Цель и статус

**Последнее обсуждение и следующий этап исследования:** [варианты runtime и низкая RAM](runtime-options.md).
Учитывает одного разработчика, Windows/Linux/macOS, наблюдение ~460 МБ и варианты без второго UI.
Продолжить с профилирования памяти и проверки Native Image; архитектурная миграция не выбрана.

**Continuation point 2026-11-21:** the native image runs every feature the author tried, double
clicked, with metadata derived by rule and a clean strict-mode audit (committed `e805bd9`). Next is
**[ST-004](tickets/ST-004-native-image-gradle-build.md) — the image built by Gradle**, then
[ST-005](tickets/ST-005-native-image-msi.md) — its MSI; then
native memory against the JVM, a PGO trial, and the author's full pass before release. What the
work taught is in [Native Image — what is known](#native-image--what-is-known) below; the full log
is the end of [ST-001](tickets/ST-001-startup-measurements.md).

**Точка продолжения 2026-11-20:** конец [ST-001](tickets/ST-001-startup-measurements.md) — сделано, до/после, следующий шаг (ETW-трейс Native Image).

**Checkpoint 2026-11-19, продолжение:** первый JPEG измерен через собранный EXE с учётом
launcher. Параллельная загрузка FFmpeg включена для прямого открытия файла:
AppCDS **802→689 ms**, с AOT **612 ms** (медианы 3 запусков, файловый кэш тёплый).
AOT остаётся экспериментом; фоновая JVM и изменения heap defaults ещё не включены.
Подробности и проверки — в конце [ST-001](tickets/ST-001-startup-measurements.md).

Версия **1.0.8**, пользователь поручил анализ и эксперимент 2026-11-19. Приоритет —
минимальная RAM в фоне и быстрое повторное открытие; отдельно ускорить настоящий холодный
старт через AOT Class Loading & Linking (JEP 483). Автозапуск при входе в Windows не запрошен.
Render GIF пользователь перенёс в 1.0.9; эта работа его не возвращает в 1.0.8.

| Этап | Статус |
|---|---|
| [ST-001: измерения и AOT](tickets/ST-001-startup-measurements.md) | Native EXE→JPEG измерен; media prewarm включён, AOT подтверждён экспериментально |
| ST-002: минимальная RAM при закрытом окне | Анализ жизненного цикла; production не изменён |
| Native Image | Works end to end (2026-11-21): JPEG ~190 ms, video ~350 ms from launch, peak 178/269 MiB at a JPEG; metadata by rule, strict audit clean; built by session scripts, not Gradle yet |
| ST-003: launcher/IPC и упаковка | Backlog |
| [ST-004: the native image built by Gradle](tickets/ST-004-native-image-gradle-build.md) | **Done 2026-11-21** — `:desktopApp:nativeAppImage` |
| [ST-005: an MSI that installs the native image](tickets/ST-005-native-image-msi.md) | **Done 2026-11-21** — `packageMsi -Pwoka.native=true`; installed and checked by the author, 113 MB idle |
| ST-006: refreshing the image's metadata from Gradle | Backlog |

DV P5 предварительно закоммичен и отправлен по отдельному указанию пользователя:
`6374e3a Add Dolby Vision P5 colour and GPU playback`, 78 файлов, `origin/release/1.0.8`.
Новая работа запуска/RAM в этот коммит не входит. `next_steps.md` локально игнорируется Git.

## Что известно

- Ранее записанный cold start ~750 ms уже включает AppCDS. Предыдущий AWT/Skia prewarm
  уменьшил warm first frame ~600→525 ms; это замер 2026-11-01, не актуальный baseline.
- В bundled JDK 25.0.2 текущие CDS options оставляют `AOTClassLinking=false`. JEP 483 —
  дополнительная возможность; наличие JDK 25 само по себе её не включило.
- Текущий launcher задаёт Xmx1536m и MaxNewSize256m, но не Xms. На этой машине JVM выбирает
  InitialHeapSize=524288000 bytes (~500 MiB). Это committed/initial heap, не RSS и не
  утверждение, что весь этот объём физически занят. Periodic G1 GC по умолчанию отключён.
- Текущий second-instance запуск сначала поднимает новую JVM и запускает prewarm, потом
  посылает путь работающему экземпляру. Фоновая JVM сама по себе не устраняет этот расход.
- Закрытие сейчас проходит editor/save gate, останавливает IPC и завершает приложение.
  SingleInstance хранит ссылку на окно; для жизни без окна потребуется явное отвязывание
  окна и очередь запросов открытия, включая запросы во время closing/reopening.

## Эксперимент 2026-11-19

Свежий createDistributable, bundled Windows x64 Oracle JDK 25.0.2+10-LTS-69.
Только initKoin с изолированными settings и загрузка без инициализации нескольких UI/media
классов. Окно, AWT graphics pipeline, видео и первый draw не запускались. OS file cache
не очищался. По 5 последовательных запусков каждого режима, таблица — медианы.

| Режим | Bootstrap внутри main, ms | Committed heap, MiB | Used heap, MiB |
|---|---:|---:|---:|
| AppCDS | 83.668 | 501 | 13–14 |
| AOT class linking | 45.630 | 502 | 9 |
| AOT + Xms32m | 44.424 | 34 | 8 |

В production-фазе smoke `Using AOT-linked classes: true`; архив содержит 2510 linked
instance classes, из них 892 app classes. AOT файл 24,903,680 bytes, dynamic CDS 11,141,120.
Измерение доказывает совместимость и выигрыш этого bootstrap, **не** 2× ускорение всего Woka
и **не** idle RAM=34 MiB. Whole-process time включает shutdown и не служит first-frame metric.
Малый Xms пока не проверен на playback/GC jitter и не внесён в production.

## Память: что должно освобождаться

| Владелец | Найденное удержание | Требование к compact idle |
|---|---|---|
| Окно / Compose / Skia | Composition, графические ресурсы, ссылки на media | Dispose окна и composition; скрытие оставить только сравнительным вариантом |
| Плеер / дорожки / preview | Decoder buffers, GPU contexts, bitmaps; dispose сейчас сигналит cancel | Дождаться завершения владельцев и native finally; убрать ссылки на кадры |
| Общий image cache | До 256 MiB decoded pixels вне heap, глобальный lifetime | Остановить writers, убрать cache references после teardown UI; не закрывать ещё рисуемый Bitmap |
| Seek preview | До 512 cached cells (~62 MiB при 240×135) + exact frames | Освободить владельца и его кэш |
| Now Playing / PiP | Singleton может владеть player вне исходного screen | Явно согласованный lifecycle; продолжающийся звук/PiP — активная работа, не compact idle |
| Folder/watch/search | Подписки и состояние, WhileSubscribed grace 5s | Остановить наблюдения и работу миниатюр после закрытия |
| JVM / native libraries | Classes, JIT, threads, runtime/native allocations | Сохранить необходимый минимум; измерять отдельно от heap и содержимого кэшей |

Безопасный порядок: editor/save gate → запрет новых background decodes → dispose UI →
cancel и join ресурсов → убрать heavy references/cache → проверить GC/возврат памяти ОС.
Настройки должны сохраняться и при переходе в фон: shutdown hook тогда не срабатывает.
IPC остаётся жив и может разбудить оболочку. Не останавливать незавершённые save/export ради RAM.

## Выбор режима после измерений

1. **Compact JVM idle — основной кандидат:** сохраняем runtime/classes и лёгкое состояние,
   заново создаём окно. Это соответствует приоритету RAM, но reopen может быть медленнее hide/show.
2. **Скрытое окно — контрольный вариант:** измерить скорость и удержание памяти; не принимать
   по умолчанию, пока не доказан приемлемый idle footprint после видео/большого фото.
3. **Выход JVM + маленький нативный launcher:** вариант, если остаточная память JVM слишком
   велика. Минимальный фон, но открытие снова требует startup; AOT полезен и здесь.

Конкретные MB/ms бюджета пока не назначены: сначала измерить варианты. Ориентир «до 100 ms»
из обсуждения — гипотеза для скрытого окна, не результат и не обещание compact idle.

## Проверка перед изменением defaults

Сравнить cold first draw, warm process start, same-process reopen и Explorer→first draw.
Измерить median/p95 и RAM через 0/5/30/120 секунд после закрытия: Private Bytes, Working Set,
heap used/committed, native/graphics по доступным counters. Пройти пустой shell, большую
папку, фото, 4K P5, многократные open/close. Не путать trimming working set с освобождением.
Xms и GC менять по одному; проверять 4K/8K playback, seek и крупные изображения.

## Native Image — what is known

Learned 2026-11-20…21 building Woka with Oracle GraalVM 25.0.4 on Windows; the full log with
numbers is ST-001. Each line below cost a failure to learn — do not relearn them.

**Toolchain.** GraalVM's own Visual Studio detection knows VS 2022 only; on VS 18 the build runs
inside `vcvars64.bat`, with System32 prepended to `PATH` and the arguments in an `@argfile` (the
classpath is too long for a command line). A build takes ~80–100 s and up to 16 GB of builder heap.

**Linking.** Link as a GUI program (`/SUBSYSTEM:WINDOWS`, `/ENTRY:mainCRTStartup`): a console EXE
waits ~250–300 ms for Windows Terminal to create a console before `main`, which ETW showed and no
amount of image tuning would have fixed. The same delay inflates any console `java.exe` timing, so
startup is measured only through GUI launchers. **Never `-march=native`**: on this CPU it
miscompiled Compose's `Matrix.invert()`, and hover and clicks on lifted cards broke;
`-march=compatibility` is required anyway for a distributable and cost nothing measurable.

**Options.** The JVM gets its options from `Woka.cfg`; the image gets none. The heap cap is baked
at build time (`-R:MaxHeapSize`); everything else is a system property first read after `main`, so
the image sets them itself as the first thing `main` does, from its own folder — `java.home` (skiko
finds `bin\jawt.dll` there, AWT `lib\fontconfig.bfc`), the resources and JavaCPP paths, the
Java2D and Swing switches. Build-time `-D` is not used: the paths are only known at run time.
G1 flags and CDS have no counterpart (Serial GC on Windows, no class loading to cache).

**Folder.** The image is laid out like jpackage's `app\` without the jars: `woka.exe` and the JDK
DLLs, `bin\jawt.dll`, `lib\` (fontconfig.bfc, psfontj2d.properties, tzdb.dat, tzmappings), the
skiko DLL and `icudtl.dat`, `resources\` with `javacpp\`. Nothing is read from the build tree.

**Metadata — by rule, not by run.** The tracing agent records only what executed while it watched,
and every miss surfaces at the moment of the call — in this app as a hung window, because the
error dialog Compose raises needs Swing UI classes that were missed too. The image therefore takes
three sources: the agent over app runs, the agent over the whole test suite (run on GraalVM's java,
with JavaCPP loading from the folder — scanning jars under the agent crashes it), and rules derived
from the dependencies themselves:
- JNI: every class named as a string literal or by an exported `Java_*` symbol in the DLLs the
  image loads, **and every superclass** — JNI resolves a member through the class it is asked on,
  the image registers it on the declaring one (drag and drop failed on exactly that);
- JavaCPP: every concrete `Pointer` subclass may be allocated by JNI (`unsafeAllocated`);
- Swing: all `*UI` classes' methods, its icons and resource bundles; the clipboard's reflective
  `java.rmi.MarshalledObject`; `coalesceEvents` on app components; `label` on Kotlin continuations;
- FFM: descriptors parsed from the sources, plus the agents for the dynamic ones; the
  `VarHandleSegmentAs*` accessors memory segments use;
- resources loaded by path (Compose, PDFBox), and all locales (`SimpleDateFormat` in a region
  the build machine is not in hung the Info panel).

**Audit.** Build with `--exact-reachability-metadata`, run with
`-XX:MissingRegistrationReportingMode=Warn`: every missing registration is printed, including those
the code catches and turns into a generic error. A clean log over a manual pass is the bar. Before
blaming the image for wrong behaviour, reproduce it on the JVM — two "native" bugs in one evening
were product bugs.

**License.** Oracle GraalVM is under the GraalVM Free Terms and Conditions: the image may be
redistributed only free of charge. Woka stays free (2026-11-20); the installer carries GraalVM's
LICENSE, NOTICE and the JDK `legal/` notices. A paid Woka would mean GraalVM Community Edition.

## Источники

- [JDK 25 java command, AOT cache](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html#ahead-of-time-cache):
  AOTMode включает AOTClassLinking; cache привязан к приложению/JDK/OS/CPU, несовместимый
  cache должен иметь обычный fallback. Старый dynamic CDS и новый AOT сравнивать отдельными режимами.
- [JDK 25 changes](https://docs.oracle.com/en/java/javase/25/migrate/significant-changes-jdk-25.html):
  JEP 514/515 развивают AOT tooling/profiling. Это не Graal Native Image и не снимок живого UI.

## Decision log

| Дата | Вопрос | Решение | Основание |
|---|---|---|---|
| 2026-11-19 | Приоритет idle? | Минимальная RAM, compact JVM проверить первой | Прямое указание пользователя |
| 2026-11-19 | Пробовать JEP 483? | Да, сначала отдельный opt-in smoke | Работает на bundled JDK; AOT classes реально включены |
| 2026-11-19 | Менять production flags по smoke? | Пока нет | Нет first-draw и playback/GC измерений |
| 2026-11-20 | Where do `Woka.cfg`'s options come from in the image? | Heap at build time; the rest set by `main` from the image's folder | Paths exist only at run time; one mechanism for paths and switches |
| 2026-11-20 | Oracle GraalVM or Community? | Oracle, Woka stays free | GFTC allows free redistribution only; donations unlock nothing |
| 2026-11-21 | How is image metadata made complete? | Rules from the dependencies + agent over tests and app runs + strict audit | Agent output alone missed four classes of call, each found by hand as a hang |

## Новые ограничения и варианты — обсуждение 2026-11-19

Пользователь уточнил: один разработчик, нужны Windows/Linux/macOS и общий Kotlin-код;
предпочитает одно приложение без отдельного native viewer. Сообщил о ~460 MB в открытой
Woka без самостоятельно открытого файла. Это пользовательское наблюдение, распределение
памяти ещё не измерено. В read-only проверке текущего процесса Woka уже не было.
Миграция UI/runtime и добавление новой зависимости пока **не выбраны**; ниже кандидаты.

- Сначала измерить настоящий Welcome с чистым storage и отдельно восстановленную папку:
  `startupStack` без args может вернуть последнюю/первую linked folder, то есть запустить
  работу с медиа без двойного клика. `ImageBitmapCache` имеет **лимит**, а не заранее
  выделенный блок 256 MiB; `retainedBytes` начинается с нуля.
- Не объяснять 460 MB одним Xms: в предыдущей JPEG серии Xms32 сократил peak private
  ~915→440 MiB, но working set лишь ~301→291 MiB. Для диагноза нужны heap used/committed,
  VM/native allocations, карты памяти процесса и graphics/cache ownership.
- OpenJ9 — кандидат на сравнительный JVM тест, не доказанная совместимая замена:
  проверить Java 25, FFM, Skiko/JavaCV и все целевые OS/архитектуры. У него есть
  [idle GC tuning](https://eclipse.dev/openj9/docs/xxidletuninggconidle/), но это не обещание
  уменьшения всей памяти Woka до нескольких MB или старта за 100 ms.
- Более существенный кандидат с сохранением Kotlin/Compose — **GraalVM Native Image**,
  отдельно от JEP 483. [Nucleus](https://nucleusframework.dev/en/docs/performance/graalvm/)
  документирует сборку Compose в native image и per-platform toolchains.
  Его [Tao backend](https://nucleusframework.dev/en/docs/concepts/backends/) заменяет
  AWT window host; это сторонняя зависимость, её стабильность и стоимость сопровождения
  надо оценивать. Native Image и замена window backend — два отдельных эксперимента.
- Для сохранения AWT есть [Liberica NIK](https://docs.bell-sw.com/liberica-nik/25.0.4b1-25.0.4b10/how-to/using-nik-with-desktop-applications/):
  vendor описывает native AWT/Swing на Windows/Linux/macOS. Это не проверка совместимости Woka.
- [GraalVM 25 FFM](https://www.graalvm.org/jdk25/reference-manual/native-image/native-code-interoperability/ffm-api/)
  поддерживает foreign memory, down/upcalls Windows/x64, Linux/x64, Linux/AArch64 и macOS/AArch64;
  descriptors должны регистрироваться при native build. Не обещать Intel macOS/Windows ARM
  по этой таблице. Наши Dovi/JXL/JXR/RAW bridges и JavaCPP/JNI требуют отдельного smoke.

Предлагаемый порядок: разобрать RAM текущей сборки → маленький native-image compatibility
probe на реальных Compose/Skia + FFmpeg + FFM → при подтверждённой пользе проверить Tao host.
Без новой UI реализации для каждой OS и без немедленной миграции проекта. Чужие показатели
старта/RAM не считать результатом Woka; 100–300 ms и низкая RAM пока цели для проверки.
