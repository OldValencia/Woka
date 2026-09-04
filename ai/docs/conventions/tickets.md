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

## 4. Cut by what the author sees, never by where the code is layered

**A ticket is a slice of behaviour, not of architecture.** One test decides whether a cut is
legitimate: when it lands, is there something the author can *do* or *see* that they could not
before? If the honest answer is "the state is written, and nothing shows it", the slice is wrong —
however clean the seam in the code was.

**The case that produced this rule, 2026-09-04.** ME-288 taught an `mp4` to keep its edit; the author
put Markup and Adjust on a video, `Done` reported success, and the change was visible **in the editor
and nowhere else** — not in Woka's own player, not on the grid tile. Two tickets had been drawn along
the code's layers: one for the engine that writes the recipe, one (ME-284) for the readers that fold
it. That boundary is real in the source tree and absurd on screen. In the author's words: *«Вот как
можно было разделить editor view и player view на две разных части? Если мы отредактировали видео в
editor, то мы должны её увидеть и в player.»*

Four rules follow, and they gate a promotion the way §2's format does:

- **The write and its readers are one ticket.** A tool that saves what nothing displays, a setting
  nothing honours, a field nothing draws — none of those is a shippable slice. Where an edit is
  *kept* and where it is *shown* are one behaviour with two halves, and a reviewer cannot check
  either half alone.
- **A backlog block is not a ticket boundary.** Blocks are written before the tree is known
  (`feature-docs.md` §3), so promoting one means **re-cutting it** against what the author will see —
  merging two blocks as readily as splitting one. Inheriting a block's edges unexamined is how this
  rule came to be needed.
- **Splitting for size splits the work, never the visible outcome.** §4a's limits are real, and the
  legitimate cuts are *narrower scope* — one container family, one tool, one surface, each complete
  end to end. "It works internally" plus "it becomes visible" is never the cut.
- **When a result genuinely cannot be shown yet, say so where state is read.** A probe answers a
  question and displays nothing; a foundation ticket may have no surface at all. Those are honest,
  and they say it in **`Why now`** *and* in the feature doc's status row — so nobody, the author
  included, reads **done** as **usable**.

## 4a. Size

One ticket = one sitting = one green build. More than ~8 steps or ~8 files → split, and say in each
half what the other half assumes. If a step pushes a file past 150 lines, the split is its own
numbered step.

**§4 outranks this.** If the smallest slice the author can see is nine files, it is nine files, and
the ticket says why; a size limit is not a licence to ship half a behaviour.

## 5. Verification is part of the ticket

Name the tests and their tier (`jvmTest/unit/…`, `integration/…`, `ui/…`). A foundation ticket with no
UI still ships unit tests — that is what makes it foundation rather than scaffolding. "It compiles" is
sufficient verification only for a literal package move.

## 6. Reuse before addition

Woka has a component library, a token system, harnesses and a media domain. A ticket adding a slider,
a dialog, a cache or a settings row without first naming why the existing one does not fit is rejected
on sight.

## 7. Bookkeeping

**Writing one.** Write `tickets/<ID>-<slug>.md`, delete the block it replaces from
`tickets/backlog.md`, and add the row to the status table in the feature doc. All three in the same
change.

**Closing one.** Mark the row **done** with the date. If the feature has a `tickets/implemented/`
folder, **move the file into it** and fix the link in the status table; the ticket's own
`../<feature>.md` link becomes `../../<feature>.md`. Both in the same change — a done ticket left in
`tickets/` is one a later session reads as work waiting to be picked up.

That folder is rare and is not created by default (`feature-docs.md`): it exists only where a feature
has grown so many tickets that the shipped ones drown the two or three that are still open. **Aim for
fifteen tickets per feature.** Past that the feature was scoped as a chunk of work rather than as a
feature, and the fix is to split the feature — `image-editor/` passed thirty-five and grew the folder
instead, which is the example, not the pattern.
