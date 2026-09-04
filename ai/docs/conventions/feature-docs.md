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
    implemented/   a very rare folder, when there are a lot of tickets in a feature,
                   normally it should not exist
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

### A feature that ships in parts moves in parts

A feature big enough to be several bodies of work that fail and ship independently gets a folder per
part, under one umbrella:

```
ai/docs/features/pending/<feature>/
  <feature>.md   the umbrella: the architecture every part rests on, and a roll-up of the
                 parts with no ticket rows in it
  tickets/
    backlog.md   ONE backlog for every part, a section each
  <part>/        <part>.md plus tickets/ for that part's promoted tickets
```

**A part that ships leaves this tree on its own**, to
`ai/docs/features/implemented/<feature>/<part>/`, tickets and all — in a plain `tickets/`, **never a
`tickets/implemented/`**: a shipped part has no open tickets for the done ones to drown, which is the
only thing that folder is for. The umbrella, the shared backlog and the parts that have not started
stay under `pending/`.

Two consequences, and both are work in the same change: **every link across the move is repointed**
— the umbrella's roll-up, the shared backlog, the sibling parts that name the shipped one, and the
shipped tickets' own links back out — and `roadmap.md` says which part shipped rather than that the
feature did.

**Why not wait and move the feature whole.** A part is finished, and a `pending/` tree that holds
finished work is a tree a later session has to read before it can tell what is left. Added
2026-09-03, after `video-editor/foundation/` shipped and this file said nothing about the case.

## 3. Implementation-driven tickets

Only the next slice is specified in full. Everything beyond it is a block of three to six lines that
states the goal and the shape, and **no file list** — because the files it would name do not exist
yet, and a list written today is a lie by the time it is read.

A ticket is promoted from `backlog.md` into its own file when it becomes the next thing to build, and
it is written **against the tree as it then is**.

**A block's edges are not the ticket's.** Promoting one means re-cutting it so that what lands is
something the author can see — merging two blocks as readily as splitting one, because a block was
drawn before the tree was known. `tickets.md` §4 is that rule and the case that forced it.

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

**There is no `ai/docs/testing/` tree.** There was one — manual pass definitions, dated run results,
dated probe results — and it was read, acted on and deleted deliberately. Do not recreate it, and
treat a document that still refers to it as out of date rather than as an instruction.

Evidence lives next to the question it answers:

- a **probe** prints a table (`code-style/test.md`), and its output is recorded with a date in the
  ticket or plan that asked for it;
- a **by-hand check** — the thing a behavioural dependency bump needs (`code-style/build.md`) — is
  recorded the same way, in the ticket, with a date;
- the **decision** either one settles goes into the feature's decision log, and is cited in the code.

The date is the part that matters, not the location: a probe result answers "what did this machine's
decoder do, **and when**". What the separate tree cost was distance — a checklist in a directory
nobody opens drifts away from the code silently, which is exactly what happened to the last one.

## 8. Skills

The rules live here, in `ai/docs/conventions/` — `plans.md` and `tickets.md`. `.claude/skills/`
holds three-line stubs that point at them, and exists only because Claude Code discovers skills
nowhere else. Edit the convention, never the stub.

- `plan-creation` → `plans.md` — turning a request into an architecture doc + a backlog.
- `ticket-creation` → `tickets.md` — promoting a backlog block into a specified ticket against the
  current tree.

Same rule for the mandates: `mandates.md` lives here and `CLAUDE.md` imports it by path. A convention
that an agent gets wrong twice belongs in one of these files, not in prose scattered across plans.
