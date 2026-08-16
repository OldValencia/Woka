---
name: plan-creation
description: Use when creating an implementation plan for a new Woka feature. Produces a structured, step-by-step plan file saved into ai/docs/features/pending/. Triggers on "составь план", "create a plan", "plan this feature", or any request to design a feature before coding.
---

# Feature Plan Creation

The rules live in the repository, not in this file — `ai/` is the single place project knowledge is
written and read. This skill exists because Claude Code only discovers skills under `.claude/skills/`.

**Read `ai/docs/conventions/plans.md` now, and follow it.**

Also read, when the feature is large enough to be a folder rather than a file:
`ai/docs/conventions/feature-docs.md` (how `ai/` is organised) and
`ai/docs/conventions/tickets.md` (how the first tickets are written).
