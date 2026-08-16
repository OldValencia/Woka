# Settings, paths and caches

Anything that survives a restart: `WokaSettings`, `SettingsRepository`/`Store`, `WokaStorage`,
`ImageBitmapCache`, `.thumbnails`, Coil's disk cache.

## Paths

- **Every path Woka writes goes through `WokaStorage`.** Nothing assembles one from `user.home`, and
  nothing hardcodes `Documents/OldValencia/Woka` a second time. The root is read through a getter, not
  captured once, so `woka.storageRoot` can point the whole layer at a temp directory in tests.
- Woka writes inside its own root. It does **not** create dotfiles inside the user's linked folders
  without an explicit decision recorded in the feature's decision log.
- A derived path is a function on `WokaStorage`, next to the others, with a KDoc naming its key.

## Cache keys

A key encodes everything that invalidates it:

```
thumbnailFile: absolutePath | length | lastModified | frameIndex   → SHA-256, truncated to 64 bits
```

- Never key on a file *name* — two `clip.mp4` in different folders then share one thumbnail.
- Never key on something that changes for a reason unrelated to the content (a save changes `length`
  and `mtime`; a backup keyed that way would be minted fresh on every edit).
- Frame 0 must hash exactly as the frameless key did, so existing files stay valid.
- When the key's shape changes, bump the schema marker (`.keys-v2`) and drop the directory once —
  regenerating a thumbnail is cheaper than migrating it, and Woka has no users to stay compatible with.

## Settings fields

- Default reproduces today's behaviour; comment says **why** that default, in the user's terms ("Off
  by default: silently starting a file somewhere in the middle is a surprise until the user has asked
  for it").
- Adding a field touches four places and all four in the same change: `WokaSettings`, the
  `SettingsRepository` interface + impl, the pure transform in `SettingsMutations.kt` if it is more
  than a `copy`, and `app/SettingsProjection.kt`.
- Non-trivial transforms are **pure functions** in `SettingsMutations.kt`, so the repository stays a
  list of one-liners and the logic is unit-testable without disk.
- Records keyed by absolute path (`watchRecords`, `trackPrefsPerFile`) must be swept when the folder is
  unlinked or the file deleted — a prefix match is what makes that answerable, which is why they are
  keyed by path and not by a content hash.

## Writing files

- Never write into a target file directly: temp file beside it, then an atomic move with replace,
  retried a few times. On Windows the viewer may still hold the file.
- The shutdown hook that flushes settings fires after any test's temp root is restored — which is why
  the Gradle test task sets `woka.storageRoot` for the whole JVM. Keep that in mind before adding a
  second such hook.

## In-memory caches

- Bound by **bytes**, not entries, when the populations differ in size by orders of magnitude
  (`ImageBitmapCache`: a poster is 0.36 MB, a native still is 96 MB).
- Skia holds pixels natively — `-Xmx` does not bound them and the collector never feels the pressure.
  Say so in the KDoc of anything that caches decoded images.
- Expose the retained size as a readable property so a performance test can assert on it.
- Every cache states its eviction policy and what re-fills it after eviction.
