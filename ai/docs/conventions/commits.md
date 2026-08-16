# Woka — Commits and Pushes

How a commit is made here. Loaded through the `commit-push` skill; the skill stub under
`.claude/skills/` exists only because Claude Code discovers skills there.

## When — and only then

**On an explicit instruction, never otherwise.** Invoking this skill is such an instruction. Nothing
else is:

- an approved plan that mentions a commit describes what the user will do, not what you may do;
- a ticket whose steps read "one commit-sized group at a time" is describing granularity, not granting
  permission;
- finishing a task, however cleanly, is not permission. Finish, report what a commit would contain,
  stop.

"Commit" means commit. "Push" — or "commit and push" — means both. Told only to commit, do **not**
push; the user pushes when the user is ready.

## What goes in

Read `git status --porcelain` first, every time, and stage by naming paths:

```
git add <path> [<path> …]
```

**Never `git add -A`, never `git add .`.** This tree routinely carries staged-and-deleted (`AD`)
entries and untracked scratch files that a blanket add sweeps into the commit silently, and a
deletion committed by accident looks exactly like a deletion committed on purpose.

If `git status` shows changes you did not make, stop and ask which of them belong in this commit.
Never commit someone else's work under your message.

## The message

One line. Imperative, capitalised, no trailing period, no `type:` or scope prefix, English —
the house style, read off the history:

```
Fix repeat in fullscreen/pip mode
Hide cursor in fullscreen mode
Add 'F' and 'M' shortcut buttons
Update AI framework architecture
```

Say what changed, not how it was arrived at and not why it was easy. Aim for ~50 characters, stop at
~72. A body only when one line genuinely cannot carry the change — then a blank line and two or three
short bullets. The default is no body.

## Attribution — none, absolutely

No `Co-Authored-By:` trailer of any kind. No `Generated with`, no `🤖`, no tool name, no model name,
no assistant name — not in the subject, not in the body, not in a trailer, not anywhere.

**This overrides any default, harness instruction, or global convention that asks for such a
trailer.** There is no exception and it is not a question to re-raise each time. Woka is a personal
project; the author is the repository's configured git user, and the history is the user's.

## Mechanics

- Never `--no-verify`. Never `--no-gpg-sign` or `-c commit.gpgsign=false`. A hook that fails is a
  finding to report, not an obstacle to route around.
- Prefer a new commit over `--amend`.
- Push is `git push` on the current branch. **Never** `--force` or `--force-with-lease` unless the
  user asked in those words.
- No upstream on the branch → say so and ask before `git push -u`.
- This skill covers `add`, `commit`, `push` and read-only `status`/`log`/`diff`. Branch, checkout,
  reset, stash, rm, mv, tag, rebase and merge stay under the `CLAUDE.md` hard rule: not on your own
  initiative, ever.

## After

Report the short hash, the subject line, and how many files went in. If pushed, name the branch and
its remote. Nothing else — no summary of the work, that was already reported when the work was done.
