---
name: Feature Plan Creation
description: Guidelines on how to create structured, actionable implementation plans for new features.
---

# Feature Plan Creation Guidelines

When asked to create a plan for a new feature, follow these strict rules to ensure the plan is highly actionable and precise.

## 1. Output Location
- **DO NOT** create plans in temporary folders, scratch directories, or as chat-only artifacts if it's a persistent project feature.
- **DO** save the plan directly into the project repository. For this project, save plans in the `ai/docs/features/pending/` directory using an appropriate `.md` filename.

## 2. Structure & Tone
- Keep the plan structured, clear, and step-by-step.
- Group steps logically into **Phases** (e.g., Phase 1: State Management, Phase 2: UI Implementation, Phase 3: Business Logic).
- Be direct and technical.

## 3. Actionable Steps (No Fluff)
- **CRITICAL**: A step must NOT contain descriptive UI/UX fluff (e.g., "The app should look beautiful and the buttons should be blue").
- A step is a **literal stage of development**. It must describe exactly *what* code changes to make.
- **Good Example**: "Add `isSelectMode: Boolean` to `FolderState`. Implement `toggleSelectMode()` to flip the boolean and clear the `selectedPaths` set."
- **Bad Example**: "Add a selection mode so users can easily select multiple files at once."

## 4. File Operations Checklist
For each phase, quickly list the specific files that need to be touched. This acts as a roadmap during implementation.

## Example Output Format
```md
# Plan: [Feature Name]

## Phase 1: [Logical Grouping, e.g., State Updates]
- Add `stateVariable` to `MyViewModel.kt`.
- Implement `updateState(val)` method in `MyViewModel.kt`.

## Phase 2: [Logical Grouping, e.g., UI]
- In `MyScreen.kt`, wrap `MyComponent` with `pointerInput` to detect right clicks.
- Replace `ButtonX` with `ButtonY` conditionally based on `stateVariable`.
```
