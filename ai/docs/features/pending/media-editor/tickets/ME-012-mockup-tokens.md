# ME-012 — Editor mockup and tokens

| | |
|---|---|
| **Order** | 13 — independent, can run in parallel with ME-003…ME-011 |
| **Depends on** | nothing |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** The visual specification, in one HTML file, plus the dp/ms constants the shell will read.

**Why now.** §4.2 of the feature doc holds design intent in prose. Prose disagrees with code silently;
a mockup disagrees visibly.

## Steps

1. `ai/mockups/design/editor.html`: the layout from §4.2 — top bar, canvas, inspector, rail — in both
   themes with a toggle, per `ai/docs/conventions/feature-docs.md` §6. Self-contained, no CDN, opens
   by double-click. Every state that has to be built:
   - a tool selected, unselected, and disabled;
   - `Done` dirty and not dirty;
   - one inspector row per `ParamSpec` type, including the preview strip and the colour swatches;
   - the "operations from a disabled tool are not applied" notice;
   - the save menu — `Save a Copy` with the generated filename, `Overwrite` as the secondary entry;
   - the overwrite confirmation, stating that no copy is kept, with the "Don't ask again this session"
     option drawn in both its states;
   - the HDR view-mode control in the top bar (§6.1);
   - the save banner in its three states, and the conversion sheet.
2. Take colours from `core/ui/theme/Theme.kt` (`accentEditor` = `#BF5AF2`) and geometry from
   `ViewerChrome` — the editor must look like the viewer's controls, not like a new app.
3. `EditorTokens.kt` (jvmMain, `feature/editor/shell/`): the same values as Kotlin constants, each
   commented with the mockup element it corresponds to. Nothing else in the editor declares a dp
   literal for chrome (`code-style/composable.md`).
4. Replace the concrete values in §4.2 of the feature doc with a pointer to the mockup, so there is one
   source and not two.

## Files

`ai/mockups/design/editor.html` (new), `jvmMain feature/editor/shell/EditorTokens.kt` (new),
`ai/docs/features/pending/media-editor/media-editor.md` (edit)

## Done when

- the mockup opens by double-click with no network access, and both themes render;
- every value in `EditorTokens.kt` appears in the mockup, and every chrome value in the mockup appears
  in `EditorTokens.kt`.
