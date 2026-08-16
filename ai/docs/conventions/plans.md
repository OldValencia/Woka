# Writing a feature plan

Invoked through the `plan-creation` skill, which is a three-line pointer to this file. The rules live
here so that `ai/` stays the single place project knowledge is written and read.

## 1. Where it lands

- Persistent project work gets a **file in the repo** — not a chat answer, not the scratchpad.
- Small feature → `ai/docs/features/pending/<kebab-case-name>.md`.
- Feature large enough to outlive one sitting → a **folder**, per `feature-docs.md`:
  `<feature>/<feature>.md` — the same feature file, named after the folder — plus `tickets/` (one
  file per specified ticket and `backlog.md` for everything after).
- Fully shipped → **move** to `ai/docs/features/implemented/`; images to the sibling `assets/`.
- A feature introducing a new UI surface gets `ai/mockups/design/<feature>.html` before its UI
  tickets are written.

## 2. Read first

`ai/docs/general_idea.md` and `ai/docs/roadmap.md`. A plan that contradicts them is wrong by
construction. Then read the code it will touch — a plan naming a file that moved is worse than no
plan.

## 3. Structure and tone

Structured, step-by-step, direct, technical. Group by logical layer — state, contract, engine, UI,
save — not by feature area. Every design claim states what it rejected and why (`code-style/comments.md`
is the same rule for code).

## 4. Steps are development stages

A step describes **what code changes**, not how the result should feel.

- **Good:** "Add `isSelectMode: Boolean` to `FolderState`. Implement `toggleSelectMode()` in
  `FolderViewModel.kt` to flip the boolean and clear `selectedPaths`."
- **Bad:** "Add a selection mode so users can easily select multiple files."

No UI adjectives. Visual specifics are concrete values (`12.dp` radius, `180 ms`, `#BF5AF2`) or a
pointer to the mockup.

## 5. File lists

A single-file plan lists the files it touches per phase, and respects the 130–150 line mandate — if a
phase pushes a file past it, the split is its own step.

**Folder-shaped features are the exception:** only the phases being built next carry file lists, and
each of those is its own file under `tickets/` (`tickets.md`). Later phases stay in `backlog.md` as
three-to-six-line blocks with no paths, because those paths do not exist yet and naming them now
guarantees the document is wrong later.

## 6. Decisions

Every architecture doc ends with a decision log: date, question, choice, one line of reasoning. A
logged choice is not reopened without a new fact; when it is, the old entry stays and a new one is
appended.

## 7. Minimal format

```md
# Plan: [Feature Name]

## Phase 1: [State]
- Add `stateVariable` to `MyViewModel.kt`.
- Implement `updateState(value)` in `MyViewModel.kt`.

**Files:** `MyViewModel.kt`, `MyState.kt`
```
