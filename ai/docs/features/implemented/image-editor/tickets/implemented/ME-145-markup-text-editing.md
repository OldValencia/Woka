# ME-145 — Markup text: editing on the canvas

| | |
|---|---|
| **Order** | 46b — immediately after ME-122, which it makes reachable |
| **Depends on** | ME-122 (the shape, its measurement and its size row), ME-114 (the overlay and the keyboard slot) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Place a caret with a click, type into it on the canvas, and commit the caption as one undo
step — discarding it when nothing was typed.

**Why now.** ME-122 shipped a shape nothing could create, and the `Text` chip in the Shape row
advertised it: choosing the text pen and clicking the picture did nothing at all, and *dragging* with
it minted an empty shape and an undo step for a picture that had not changed. Reported by hand
2026-08-26.

**Written after the work, not before it** — the one ticket in this feature that is. It was promoted
straight out of the backlog as a fix, so what follows is what was built rather than what was planned.

## The three things that were not obvious

**1. The editor's key handler is previewed at the window root, before focus.** `EditorKeyEffect`
installs into `GlobalShortcutState.onPreviewKey`, which `MainShell` calls from `onPreviewKeyEvent` on
its outermost box — so with a field open, `Backspace` reached `CanvasTool.onDelete` and deleted the
caption being typed into, and `Esc` closed the editor out from under the caret. The gate is a flag on
`CanvasKeys`, the slot ME-114 built for exactly this class of problem, and the handler declines
**every** key while it is set. The field consumes `Enter` and `Esc` itself.

**2. `onFocusChanged` reports *not focused* on the frame the field first composes**, before the
`LaunchedEffect` requesting focus has run. A blur handler that treats any unfocused report as "clicked
away" therefore commits the edit on the frame it opened, and the caret vanishes as fast as it appears
— which is precisely what the first build did. The commit needs *two* latches: one for "focus has
arrived at least once", one for "this edit is already over", the second because `Enter` removes the
field and the removal reports a blur.

**3. Clicking away cannot come from the field.** The canvas under it is not focusable, so a press on
the picture never moves focus. The press handler ends any open edit itself, before it decides what
else the press means.

## What was built

1. `core/EditorToolScope.kt` (edit): `fun cancel()` — `session.cancelPending()`, which `ParamWriter`
   has had since ME-102 and an overlay could not reach; without it `Esc` could only be undone by
   *committing* what it was abandoning. And `fun captureKeys(captured: Boolean)`, the media-agnostic
   spelling of "this tool is taking typed input".
2. `shell/EditorToolScopeImpl.kt` (edit): both, with `CanvasKeys` passed in — nullable, because the UI
   tests compose an overlay with no host to own the keyboard.
3. `shell/ActiveToolOverlay.kt` (edit): threads the slot into `rememberToolScope`, and clears
   `typing` on dispose beside `onDelete` — a tool torn down mid-edit must not leave the editor deaf.
4. `shell/CanvasKeys.kt` (edit): `var typing: Boolean`. `shell/EditorKeys.kt` (edit): the gate, first
   thing in the handler.
5. `tools/markup/MarkupTextEdits.kt` (new): `textEditAt` (what a press means — a new caret on empty
   picture with the text pen, or a **second** click on a selected caption), `hideWhileEditing`
   (previews the shape with an empty string so it keeps its place in the list while the field draws
   it), `commitTextEdit` (blank removes it, unchanged cancels, otherwise one undo step) and
   `abandonTextEdit`.
6. `tools/markup/MarkupTextField.kt` (new): `MarkupTextEdit`, and the `BasicTextField` positioned and
   sized through `sourceToCanvas` so a caption typed at 300 % zoom lands where one typed at Fit does.
7. `tools/markup/MarkupPointer.kt` (new): the whole `awaitEachGesture` body, moved out of
   `MarkupOverlay` as a `Modifier` extension when it went past the size mandate — the shape
   `editorCanvasGestures` already takes. Type is **placed, not dragged**: a drag with the text pen
   writes nothing.
8. `tools/markup/MarkupOverlay.kt` (edit): down to the three things a recogniser cannot own — the
   string being typed, the field, and the keyboard.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/markup/MarkupTextEdits.kt` (new) ·
`.../tools/markup/MarkupTextField.kt` (new) · `.../tools/markup/MarkupPointer.kt` (new) ·
`.../tools/markup/MarkupOverlay.kt` (edit) · `.../shell/EditorToolScopeImpl.kt` (edit) ·
`.../shell/ActiveToolOverlay.kt` (edit) · `.../shell/CanvasKeys.kt` (edit) ·
`.../shell/EditorKeys.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorToolScope.kt` (edit)

## Done when

- `jvmTest/ui/MarkupTextUiTest.kt` (new): a click with the text pen opens a caret and puts **no**
  shape in the document; typing then `Enter` commits one caption and one undo step, and the string
  does not reach the document per keystroke; a caret with nothing typed into it leaves the document
  byte for byte as it was; `Esc` abandons what was typed. ✔
- `jvmTest/ui/EditorKeysUiTest.kt` (edit): with `CanvasKeys.typing` set, `Backspace` driven through a
  real `onPreviewKeyEvent` on a focused box does **not** reach the tool's `onDelete`, and does reach
  it when the flag is clear. Driven through the plumbing rather than by calling the handler, or the
  test proves the `if` and not the wiring. ✔
- `./gradlew.bat :shared:build` green. ✔
- **By hand — done 2026-08-27**, and it took four fixes to get there. Each is worth reading, because
  three of the four were found by a log from the running app after a UI test had said the code was
  fine:
  1. **The caption had to move into the document.** It shipped living in the field until the edit was
     finished, and the style rows could not reach it — clicking a row is what blurs the field, so the
     write landed *before* the commit with nothing selected, and the row silently moved the pen. It is
     now previewed into the list the moment the caret is placed, which also puts it on the canvas as
     it is typed and makes the field transparent (`MarkupTextEdits`).
  2. **The field committed itself on the frame it opened.** `onFocusChanged` reports *not focused*
     before the request for focus lands, so the blur handler needs a latch for "focus has arrived at
     least once" and a second for "this edit is over".
  3. **A row labelled `Text size` wrote `width`.** `EditorTool.rows` swaps one spec for another in the
     same position, and without an identity Compose reused the node while `DetentSlider` kept a
     gesture closed over the old spec. Fixed with `key(spec.key)` per row and `gestureKey = spec.key`
     on the slider. **Three UI tests failed to reproduce this** — the app's own log is what found it.
  4. **The caret kept the size it opened at.** `MarkupTextEdit.shape` is frozen so the commit can tell
     an emptied caption from an abandoned caret; the field now draws from `liveEdit`, which is that
     edit carrying the caption as the document has it now.
