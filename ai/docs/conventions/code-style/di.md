# Dependency injection (Koin)

`app/di/Koin.kt` (common), `app/di/PlatformModule.kt` (jvm), and one module per feature that needs one
(`feature/editor/di/EditorModule.kt`).

## Rules

- **Never instantiate a dependency inline.** No `SettingsRepositoryImpl()` at a call site, no
  `object` singleton standing in for a service. If a type has collaborators, Koin builds it.
- `single` for anything with state that must be shared or is expensive to build: repositories, the
  router, registries, caches.
- `factory` for per-screen and per-session objects: ViewModels, editor sessions. Parameterised where
  the object is keyed on something (`factory { (path: String) -> FolderViewModel(path, get(), get()) }`).
- Interface in `commonMain`, implementation bound in the platform module:
  `single<SettingsRepository> { SettingsRepositoryImpl() }`.
- A `commonMain` module cannot name a JVM symbol. Anything JVM-only is declared in
  `PlatformModule.kt` or in a JVM feature module composed into it.

## Composition

`initKoin(launchArgs)` starts the container and applies the startup route before the first
composition — `desktopApp` does not carry Koin on its compile classpath and cannot name the container
itself. Keep it that way: `main()` passes arguments, nothing else.

New feature module → declared in its own file, composed into `platformModule` (or listed in
`initKoin`), never appended to `appModule` from the outside.

## Plugin-style registries

Where a feature collects implementations (editor tools), the module is **the single place an
implementation is declared**, and the registry is built from `getAll<T>()` so joining is one line and
nothing else. A registry that also hardcodes its members has two sources of truth.

## Composables and DI

A composable resolves a ViewModel through the existing helper at the screen boundary and passes plain
state downward. Deep components never reach into Koin — a control that resolves its own dependency
cannot be previewed, tested or reused.

## Tests

`jvmTest/support/KoinOnce.kt` owns container setup for tests. Extend it; never start a second
container in a test, and never leave one running between classes.
