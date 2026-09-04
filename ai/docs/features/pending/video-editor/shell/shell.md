# Feature: The shared editor shell (Video Editor, §300)

The eighth folder under [`video-editor/`](../video-editor.md), and the one that is not a band of the
200s. Everything here changes the shell **both** editors compose — so a photograph is affected by
every one of it, and the tests that prove it are the image editor's as much as the video editor's.

It exists because the shared backlog's §300 needed a home for a status table. The umbrella
([`../video-editor.md`](../video-editor.md) §1) deliberately carries no ticket rows, and
`feature-docs.md` §4 puts the only table in the part's own file — so §300 became a part like the
other seven rather than an exception to them.

**No architecture of its own.** What the shell is, and what these tickets are allowed to do to it,
is [`../video-editor.md`](../video-editor.md) §4.1–§4.5 and
[`image-editor.md`](../../../implemented/image-editor/image-editor.md) §3–§4. This file holds the
status table, the two rules below, and nothing that is written down elsewhere.

---

## 1. What lands here rather than in a band

Two questions, asked in order:

1. **Does a still change?** If the answer is yes, it is a §300 ticket. A tool that only exists for a
   moving picture belongs in `timeline/`, `audio/` or `picture/` however much shell code it touches.
2. **Is it the same behaviour on both media?** A control that has to say `MediaKind` out loud is not
   shared-shell work; it is a branch, and the seam
   ([`../video-editor.md`](../video-editor.md) §3.4) exists so the shell never grows one.

Both are true of everything in §300 today: right-click reset (ME-300), the per-file hide (ME-302,
ME-303) and the HDR switch reaching a video frame (ME-301).

## 2. A §300 ticket ships against the image editor first

The image editor is shipped and confirmed on screen; the video editor is one folder in. So a change
to the shared shell is verifiable **today**, on a photograph, and every ticket here is written that
way round: the still is the acceptance test and the video is the case that must not regress.

That is also why these are not "video editor" tickets in any but the filing sense. ME-302 was
reported against an SDR **photograph**.

---

## 3. Status

| Ticket | Title | State |
|---|---|---|
| [ME-302](tickets/ME-302-the-hdr-tool-leaves-an-sdr-rail.md) | The HDR tool leaves an SDR picture's rail | **done 2026-09-04**, confirmed on screen by the author |
| [ME-303](tickets/ME-303-the-hdr-dropdown-leaves-an-sdr-pill.md) | The HDR dropdown leaves an SDR picture's pill | **done 2026-09-04** — the viewer's pill is one `&&` over a unit-tested predicate and wants a by-hand look |

Unpromoted blocks — ME-300 and ME-301 — are in the shared backlog
([`../tickets/backlog.md`](../tickets/backlog.md) §300), which is where their state is *not* recorded:
nothing there is a row, and a block gets one only when it is promoted into this table.

---

## 4. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-04 | Where does a §300 ticket live? | **Its own part folder, `shell/`, like the other seven** | The umbrella carries no ticket rows by decision, and `feature-docs.md` §4 allows exactly one status table per part. A §300 ticket with nowhere to be recorded is a ticket whose state lives in a session's memory |
| 2026-09-04 | Is a per-file hide a widened `EditorToolRegistry`? | **No — a filter over what it returns** | `EditorSkippedOps` and `RasterFold` both read `registry.tools(kind)` to tell "no such tool" from "tool switched off". A third meaning inside that answer would make the skipped-ops notice say a hidden tool's work was dropped, which is the notice lying (ME-302) |
| 2026-09-04 | Who answers "what colour is this file"? | **The engine, as `EditorEnginePicture.sourceColor`** | It is the one object in the editor that is allowed to know what a medium is. The alternative — the shell reading the file — makes the shell name `MediaKind` to avoid asking a video, which is the branch the seam exists to prevent (ME-302) |
