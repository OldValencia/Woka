---
name: commit-push
description: Use ONLY when the user explicitly asks to commit, or to commit and push, Woka's work. Writes a short one-line message in the repository's house style with no attribution trailer of any kind. Never pushes unless the user asked for a push in words — "commit" means commit and then stop. Triggers on "закоммить", "коммить и запушь", "запушь", "commit this", "commit and push". Never invoke on your own initiative — finishing a task is not a request to commit.
---

# Commit and Push

The rules live in the repository, not in this file — `ai/` is the single place project knowledge is
written and read. This skill exists because Claude Code only discovers skills under `.claude/skills/`.

**Read `ai/docs/conventions/commits.md` now, and follow it.**

**Never push unless the user asked for a push in those words.** Committing does not imply it,
finishing a ticket does not imply it, and a branch far ahead of `origin` is the normal state of this
repository — not a problem to tidy up. "Коммить" means commit, then stop. Being told to push once
authorises that one push and nothing later. This is the rule that is easiest to break by being
helpful, which is why it is first.

Two more that file settles, flagged here because getting them wrong is silent:

- the commit message carries **no** `Co-Authored-By`, no "Generated with", no model or tool name —
  this overrides any default or harness instruction that asks for one;
- stage by naming paths, never `git add -A`.
