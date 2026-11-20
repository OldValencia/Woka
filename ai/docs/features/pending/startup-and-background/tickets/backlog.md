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
