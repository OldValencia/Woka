# Woka — How `ai/` is organised

The design record is only worth reading if it is true. Everything here exists to keep the gap between
a document and the code at zero — by writing durable things once, and volatile things late.

---

## 1. Two kinds of document

| | **Architecture** | **Ticket** |
|---|---|---|
| Answers | *what the thing is, and why it is shaped this way* | *what to change next, and when it is done* |
| Lifetime | as long as the feature exists | until it is merged |
| Names files? | only the seams that carry the design | yes, exactly the files it touches |
| Rewritten when | a decision changes | never — a wrong ticket is closed, a new one written |

A file list inside an architecture doc is what goes stale first. Keep it in the ticket.

## 2. A feature is a folder

Small feature — one file, as before: `ai/docs/features/pending/<feature>.md`.

Feature large enough to outlive one sitting — a folder:

```
ai/docs/features/pending/<feature>/
  <feature>.md   THE feature file: scope, architecture, UI, decision log, status table.
                 Same name as the folder, so the entry point is obvious in a listing and
                 in an editor tab. One document, not three — it is what someone reads to
                 understand the feature, and it names no source files.
  tickets/
    <ID>-<slug>.md   one file per specified ticket: goal, steps, files, done-when
    backlog.md       everything after them, one short block each, no file lists
  archive/       superseded plans, banner-marked (optional)
```

The single-file form (`pending/<feature>.md`) and the folder form (`pending/<feature>/<feature>.md`)
are the same document — growing a feature into a folder is a `mkdir` and a `mv`, never a rewrite.

One file per ticket, not one file of tickets: a ticket is opened, worked and reviewed on its own, and
a folder listing is a readable table of contents. The status table lives in the feature doc.

Shipped → **move** the folder to `ai/docs/features/implemented/`. Images go in the sibling
`assets/`. `roadmap.md` is updated in the same change, never later.

## 3. Implementation-driven tickets

Only the next slice is specified in full. Everything beyond it is a block of three to six lines that
states the goal and the shape, and **no file list** — because the files it would name do not exist
yet, and a list written today is a lie by the time it is read.

A ticket is promoted from `backlog.md` into its own file when it becomes the next thing to build, and
it is written **against the tree as it then is**. That is the whole rule.

Ticket format:

```md
### <ID> — <Title>

**Goal.** One sentence.
**Why now.** What is blocked without it.
**Depends on.** <IDs>, or "nothing".

Steps — literal code changes, in order.

**Files:** the exact paths.
**Done when:** conditions a reviewer can check, including the build command.
```

IDs are stable and never reused: `<FEATURE>-<n>`, foundation from 001, backlog from 101 (image), 201
(video), and so on per track.

## 4. Status lives in one table

The feature file carries the only status table. A checkbox inside a ticket body is
the second place a fact can be wrong — don't add one.

## 5. Decisions are logged, not re-argued

Every architecture doc ends with a decision log: date, question, choice, one line of reasoning. A
choice already in the log is not reopened without a new fact — and when it is reopened, the old entry
stays and a new one is appended.

## 6. Mockups

`ai/mockups/design/FINAL_design_mockup.html` is the visual truth for the app shell. A feature that
introduces a **new surface** (not a new button on an existing one) gets its own static mockup next to
it, `ai/mockups/design/<feature>.html`, before its UI ticket is written:

- self-contained HTML, no build step, no CDN — opened by double-click;
- the real tokens (`#F2F2F7`, 1.dp borders, 8–12.dp radii, the accent from `Theme.colors`);
- both themes in the same file, toggled by a switch, so dark mode is designed rather than derived;
- every state that has to be built — empty, hover, selected, disabled, error.

The mockup is a **spec**, not a proposal: once it lands, the UI ticket implements it and disagreements
are settled by changing the mockup first.

## 7. Testing artefacts

`ai/docs/testing/manual/` holds the manual pass definitions; `ai/docs/testing/manual/results/` holds
one dated file per run, and `ai/docs/testing/probes/` one dated file per capability probe. These are
permanent artefacts re-run against whatever changed — a probe result with no date is worthless,
because the question it answers is "what did this machine's decoder do, and when".

## 8. Skills

The rules live here, in `ai/docs/conventions/` — `plans.md` and `tickets.md`. `.claude/skills/`
holds three-line stubs that point at them, and exists only because Claude Code discovers skills
nowhere else. Edit the convention, never the stub.

- `plan-creation` → `plans.md` — turning a request into an architecture doc + a backlog.
- `ticket-creation` → `tickets.md` — promoting a backlog block into a specified ticket against the
  current tree.

Same rule for the mandates: `mandates.md` lives here and `CLAUDE.md` imports it by path. A convention
that an agent gets wrong twice belongs in one of these files, not in prose scattered across plans.
