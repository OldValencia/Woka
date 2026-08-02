---
name: plan-creation
description: Use when creating an implementation plan for a new Woka feature. Produces a structured, step-by-step plan file saved into ai/docs/features/pending/. Triggers on "составь план", "create a plan", "plan this feature", or any request to design a feature before coding.
---

# Feature Plan Creation

Rules for turning a feature request into an actionable plan document.

## 1. Output location

- Persistent project features get a **file in the repo**, not a chat-only answer and not a scratchpad.
- Save to `ai/docs/features/pending/<kebab-case-feature-name>.md`.
- When the plan is fully implemented, **move** it to `ai/docs/features/implemented/`.
  Supporting images go in `ai/docs/features/implemented/assets/`.

## 2. Structure & tone

- Structured, step-by-step, direct, technical.
- Group steps into **Phases** by logical layer (e.g. Phase 1: State, Phase 2: UI, Phase 3: Logic).
- Read `ai/docs/general_idea.md` and `ai/docs/roadmap.md` first — the plan must not contradict them.

## 3. Steps are literal development stages

A step describes **what code changes to make**, not how the result should feel.

- **Good:** "Add `isSelectMode: Boolean` to `FolderState`. Implement `toggleSelectMode()` in
  `FolderViewModel.kt` to flip the boolean and clear the `selectedPaths` set."
- **Bad:** "Add a selection mode so users can easily select multiple files at once."

No UI/UX fluff ("should look beautiful", "buttons should be blue"). If visual specifics matter,
state them as concrete values (`12.dp` radius, `1.dp` border).

## 4. File checklist per phase

Each phase lists the exact files to be touched. This is the implementation roadmap.
Respect the 130–150 line file limit — if a phase would push a file past it, the plan must include
the split as its own step.

## 5. Output format

```md
# Plan: [Feature Name]

## Phase 1: [Logical grouping, e.g. State Updates]
- Add `stateVariable` to `MyViewModel.kt`.
- Implement `updateState(value)` in `MyViewModel.kt`.

**Files:** `MyViewModel.kt`, `MyState.kt`

## Phase 2: [Logical grouping, e.g. UI]
- In `MyScreen.kt`, wrap `MyComponent` with `pointerInput` to detect right clicks.
- Replace `ButtonX` with `ButtonY` conditionally on `stateVariable`.

**Files:** `MyScreen.kt`, `components/MyComponent.kt`
```
