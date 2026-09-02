# ME-176 — Revert follows the scope, and the switch says Single Frame

| | |
|---|---|
| **Order** | after ME-175, which makes a frame's edit a layer rather than a replacement |
| **Depends on** | ME-175, ME-107, ME-156 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make Revert remove what the scope switch is pointing at — this frame's own edit,
or every frame's — and rename `This Frame` to **Single Frame**.

**Why now.** Revert is whole-file and always has been: it strips the recipe and the
picture goes back to as shot. Since ME-156 the editor can say "this edit is for one frame"
everywhere else, and Revert is the one control that cannot hear it — so on a burst the
only way to undo one frame's work is to undo it step by step. Asked for by hand
2026-09-02, together with the rename: `This Frame` reads as a location, `Single Frame`
reads as a scope, which is what it is.

## What each Revert does

| Scope | What is removed | What survives |
|---|---|---|
| **All Frames** | the whole recipe — `ops` and every key | nothing; the file is as shot |
| **Single Frame** | this frame's key only | the file's own `ops`, and every other frame's key |

Under ME-175 those are exactly the two layers, which is what makes the pair coherent:
All Frames takes the base and everything on it, Single Frame takes one delta off and
leaves the base drawing.

**Both warn**, and the two warnings must not say the same thing. Revert is the editor's
only destructive control (ME-107) and the scope decides how much it destroys; a dialog
that read "this cannot be undone" for both would be true and useless.

## Steps
1. Rename the enum constant `EditScope.ThisFrame` → `SingleFrame` and the label the
   segmented control shows. **The serialised name changes**, so check what `storedScope`
   and `EditPayload` actually persist: the scope is *derived* from whether a key exists
   (`EditPayload.storedScope`) rather than written, so this is a source rename with no
   format change — confirm that by grep before relying on it.
2. `EditorEngine.revert()` takes an `EditScope`. `ImageEditorEngine` passes it to
   `revertRecipe`, which today removes the whole recipe.
3. `revertRecipe(file, scope, frameIndex)`: `AllFrames` clears as it does now;
   `SingleFrame` reads the payload, drops `frames[frameIndex]`, and writes what is left —
   or clears the recipe entirely if what is left is empty, so a file never keeps a slot
   describing nothing (§5.2's rule, which `isEmpty` already states).
4. Two dialog bodies rather than one. All Frames: what it says today. Single Frame: that
   this frame goes back to what the rest of the file draws — **which is not "as shot"**
   when the file carries a base, and saying "as shot" there would be a lie.
5. `canRevert` follows the scope too: in Single Frame it is false when this frame carries
   no key of its own, because there is nothing of this frame's to remove. A control that
   is drawn always and disabled when it would do nothing is the rule ME-107 set for it.
6. The dialog goes through `DiscardChangesDialog`'s neighbour, not through a third dialog
   type — check what ME-160 and ME-158 left behind before adding one.

## Files
Named when the ticket is picked up, against the tree as it is then. The seams are
`EditScope`, `EditorEngine.revert`, `revertRecipe`, `EditorFrameScopeStrip`,
`EditorHostControls` and whatever draws the confirmation.

## Done when
- `jvmTest/unit/`: Single Frame revert on a burst leaves `ops` and the other keys intact;
- `jvmTest/unit/`: Single Frame revert that empties the payload removes the recipe rather
  than leaving an empty slot;
- `jvmTest/unit/`: All Frames revert removes everything, as it does today;
- `canRevert` is false in Single Frame on a frame with no key;
- `grep -rn "ThisFrame"` returns nothing;
- confirmed by hand on `burst.heic` — the two dialogs are the deliverable here, and only
  reading them decides whether they say the right thing;
- `./gradlew.bat :shared:build` green.
