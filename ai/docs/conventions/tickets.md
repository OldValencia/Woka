# Writing a ticket

Invoked through the `ticket-creation` skill, which is a three-line pointer to this file. Applies to
features that already have a feature doc and a backlog — promoting one backlog block into work that
can be picked up cold.

## 1. Written against the tree, not against the plan

Before a single line: `Read` the files the ticket will touch, `Grep` for the seams it attaches to,
check what already exists. A ticket naming a file that moved, or re-inventing a component already in
`core/ui/components/`, is worse than no ticket.

Never specify more than the next slice. If the work after it is unclear, that is correct — it stays a
backlog block until its turn.

## 2. Format

One ticket, one file: `tickets/<ID>-<kebab-slug>.md`.

```md
# <FEATURE>-<n> — <Title>

| | |
|---|---|
| **Order** | where it sits in the build sequence |
| **Depends on** | ticket ids, or "nothing" |
| **Feature** | [<Feature>](../<feature>.md) |

**Goal.** One sentence, imperative.
**Why now.** What is blocked or guessed at without it.

## Steps
1. Literal code changes, in the order they compile in.
2. Each step names the type, the function and the behaviour — not the intent.

## Files
`exact/path/One.kt` (new), `exact/path/Two.kt` (edit)

## Done when
- conditions a reviewer can check by reading a diff or running a command;
- `./gradlew.bat :shared:build` green.
```

Ids are stable and never reused: foundation from 001, backlog tracks from 101, 201, … A dropped
ticket keeps its id and says so; the id does not go back into the pool.

## 3. Steps are code changes

- **Good:** "Add `editorDisabledTools: Set<String> = emptySet()` to `WokaSettings`; add
  `setEditorToolEnabled(id, enabled)` to `SettingsMutations.kt` returning a `.copy()`."
- **Bad:** "Let the user turn tools off in Settings."

## 4. Size

One ticket = one sitting = one green build. More than ~8 steps or ~8 files → split, and say in each
half what the other half assumes. If a step pushes a file past 150 lines, the split is its own
numbered step.

## 5. Verification is part of the ticket

Name the tests and their tier (`jvmTest/unit/…`, `integration/…`, `ui/…`). A foundation ticket with no
UI still ships unit tests — that is what makes it foundation rather than scaffolding. "It compiles" is
sufficient verification only for a literal package move.

## 6. Reuse before addition

Woka has a component library, a token system, harnesses and a media domain. A ticket adding a slider,
a dialog, a cache or a settings row without first naming why the existing one does not fit is rejected
on sight.

## 7. Bookkeeping

Write `tickets/<ID>-<slug>.md`, delete the block it replaces from `tickets/backlog.md`, and add the row
to the status table in the feature doc. All three in the same change.
