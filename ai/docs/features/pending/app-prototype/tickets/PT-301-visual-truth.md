# PT-301 — Visual truth switches to `woka.html`

| | |
|---|---|
| **Order** | ninth |
| **Depends on** | PT-201, PT-202 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Make `woka.html` the visual truth the conventions name, and keep the mockups before it as
history.
**Why now.** With the library in, the prototype covers every screen; the conventions still sent a
new surface to a mockup file of its own and named `FINAL_design_mockup.html` as the truth.

## Steps

1. Move `FINAL_design_mockup.html` and the per-feature mockups (`audio-player`, `editor`,
   `folder-card-stack`, `home-proposal`, `now-playing`, `phosphor-icons`, `rethink-home`,
   `sidebar-redesign`, `video-editor`) into `ai/mockups/design/legacy/` — the author's call,
   2026-11-25: keep them as history. `woka.html` and `woka/` stay where they are.
2. Repoint every path to a moved file: shipped and pending feature docs, five Kotlin comments that
   cite the mockup a component was taken from, `already_done.md`.
3. `CLAUDE.md`, `mandates.md`, `feature-docs.md` §6, `plans.md` §1, `code-style/composable.md`:
   the truth is `woka.html`; a new surface adds scenes to it; the prototype is checked with
   `shot.mjs`.

## Done when

- `Grep` for the moved names under `design/` without `legacy/` finds nothing.
- `./gradlew.bat :shared:compileKotlinJvm` green (comments only).
