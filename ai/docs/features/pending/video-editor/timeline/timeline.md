# Feature: Video Editor — Timeline

The strip, and the document behind it. This is the half of the feature the image editor could not
have anticipated, and it is the half the author asked for by name: *вырезать из центра часть и
удалить её либо вставить её в другое место*.

Umbrella, and the data-model argument this rests on: [`../video-editor.md`](../video-editor.md) §3.2,
§3.3 and §4.2–4.3. Ticket ids: **ME-210…ME-229**.

---

## 1. Status

No tickets written. Every block is in [the shared backlog](../tickets/backlog.md) §210.

Blocked on [`foundation/`](../foundation/foundation.md) — there is no band to draw in until ME-201
lands, and no engine to fold a timeline stage.

---

## 2. Scope

**In.** Trim the ends. Split at the play head. Select a clip. Delete it, duplicate it, drag it
somewhere else. Per-clip speed and freeze-frame. The clip-scope switch that lets any tool apply to
one clip. The filmstrip itself.

**Out.** Playing the result without stalling ([`playback/`](../playback/playback.md)) — this folder
produces the document and the strip, and a document with three clips is meaningful before anything
can play it smoothly. Audio's own row ([`audio/`](../audio/audio.md)). Both saves.

---

## 3. Architecture

### 3.1 One op, one ordered list

The whole cut is one `ToolOp` owned by one tool, on the ordinary contract:

```
clips : List<Clip>     ordered; each is a stable id, fromMs..toMs into the ONE source,
                       and the two settings that change how long it lasts
```

**The id is stable, minted once and never reused**, exactly as `MarkupShape.id` is and for the same
reason: selection is keyed on it, and it must survive a move, which replaces the instance. It is also
what a per-clip edit is keyed on (§3.3 of the umbrella) — a *position* cannot be, because re-ordering
the clips would hand every clip its neighbour's edit.

`ToolCardinality.Single`, so a new list replaces the old one and undo is one entry per gesture —
the same shape `MarkupTool` settled on for the same reason (a `Repeating` op cannot be edited once
committed, and `EditorSession` offers no way to edit history). Carried as an `ObjectParam`, which is
the spec written for exactly this: a value no generic row can draw says so in its type.

`stage(params)` returns a **`ToolStage` that is not a `RasterStage`**. That costs nothing and needs
no branch: `orderedStages` ends in `as? RasterStage` and drops it, and a second fold — the timeline
fold — reads it. This is the decision of 2026-08-16 finally being used.

### 3.2 The clip entry holds only what the strip's geometry needs

One rule, and it decides every "where does this setting live" question in the feature:

> **A setting is a field of the clip entry if the strip cannot be laid out without it. Everything
> else is an ordinary op in that clip's layer.**

So **speed** and a **freeze's hold** are fields: they change how long the clip lasts, therefore where
every clip after it sits, therefore the strip itself — and the strip must be drawable from the clip
list alone, without resolving a layer per clip. **Volume, mute, fades and all five picture tools are
not**: they change how a clip looks or sounds and not how long it is, so they are ops under
`EditScope.SingleFrame` keyed on the clip's id, which is `EditLayers` doing exactly what ME-175 built
it for ([`../video-editor.md`](../video-editor.md) §3.3).

**A clip's layer travels with the clip.** Delete takes it away, duplicate copies it under the new
id, and a reorder does nothing to it because the key is an id and not a position. That is one rule
covering audio and picture identically, which is the payoff for not having invented a second
per-clip mechanism.

### 3.3 A split is arithmetic, not a decode

Splitting at the play head divides one entry into two at the play head's millisecond. **No keyframe
is consulted**, and that is a decision rather than an omission:

- the preview plays from any millisecond, because the decoder seeks and decodes forward to it — that
  is what the seek path already does;
- a keyframe only matters when a *copy* is wanted, and that is `render/`'s problem to state, one
  segment at a time, at the point where the trade-off is real;
- snapping the user's cut to a keyframe 400 ms away, in a tool whose whole promise is that the strip
  shows what you get, is a lie in the UI to save a cost that only one of the two saves pays.

This reverses the retired ME-202, which specified keyframe-snapped cuts as the mechanism. It was the
right mechanism for a stream-copy-only design and is the wrong one for a design whose default save
copies nothing.

### 3.4 The filmstrip is the seek preview's cells

`SeekPreviewSource` decodes 240 px stills keyframes-only on one dedicated thread; `SeekPreviewCache`
holds up to 512 of them in an LRU; `SeekPreviewGrid` already maps a position to a cell index. A
filmstrip is a row of those cells laid out along the clip's own range. **Nothing new decodes.**

What is new is that a cell has to be placed in *timeline* coordinates rather than source ones: with
three clips out of order, the strip is a walk over the clip list, and each clip asks for the cells
covering its own range.

### 3.5 Freeze-frame is a clip whose range is a point

A freeze is `fromMs == toMs` plus a `holdMs`. It reuses everything: it is an entry in the list, it
drags and deletes like any other, its thumbnail is one cell repeated, and the render draws one frame
for `holdMs`. No second concept, and no branch anywhere except where duration is computed.

---

## 4. UI

The two states of the band, and the four gestures, are specified in the umbrella (§4.2, §4.3) and
drawn in [`ai/mockups/design/video-editor.html`](../../../../../mockups/design/video-editor.html). What
belongs here is what the *tool* contributes:

- **Rail entry:** `✂ Trim`, in a new `ToolDomain.Time` group, first in the rail. One entry, not
  three: split, delete and reorder are things you do *to the strip*, not separate tools, and a rail
  of three time tools would be the submenu problem in a different costume.
- **Inspector, schema-driven and nothing custom:** `Split` (`ActionParam`, at the play head),
  `Delete` and `Duplicate` (`ActionParam`, enabled only with a selection), `Speed`
  (`ChoiceParam` — ¼×, ½×, 1×, 2×, 4× — or a `FloatParam` with a detent at 1×), `Freeze` and its
  hold. The footnote line says the clip's own length and the result's total.
- **Selection is drawn by the strip**, and read by the inspector through the same `selectedId()`
  channel `MarkupTool`'s rows already use.

**Delete with no selection deletes nothing** and says so by being disabled. `Delete` while a clip is
selected must not also be the shortcut that deletes a markup shape — the shell's key table is one
table, and this is the first tool that competes with another for a key.

---

## 5. Open questions

- **What the strip does when a clip is one frame long.** A 40 ms clip is 3 px wide at any sane zoom.
  A minimum drawn width means the strip no longer maps linearly to time, which is a real cost in a
  control whose whole value is that it is honest.
- **Whether the strip zooms.** Thirty minutes in 600 px is 3 s per pixel, and a two-second cut is
  unmakeable. Zoom is the obvious answer and it is also the first thing that makes the control feel
  professional; a magnifier over the play head is the other.
- **Does `Speed` change the clip's length on the strip?** It must, or the strip stops describing the
  output — and then a speed change moves every clip after it, which is correct and surprising.
- **Undo granularity for a drag.** One entry per drop is right. One entry per split is right. Whether
  a drag that ends where it started should push an entry at all is the kind of thing only using it
  answers.

---

## 6. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | One tool or several for the strip? | **One, `Trim`, in a new `Time` domain** | Split, delete and reorder are gestures on one object; three rail entries for one object is the overload the whole feature is defined against |
| 2026-09-03 | Do cuts snap to keyframes? | **No** | The default save copies nothing, so a keyframe buys nothing; snapping would make the strip lie about where the cut lands (§3.3). Reverses retired ME-202 |
| 2026-09-03 | Where does a per-clip setting live? | **A clip field if the strip's layout needs it, otherwise an op in the clip's layer** | The strip must be drawable from the clip list alone. One rule then covers audio and picture identically instead of a second per-clip mechanism (§3.2) |
| 2026-09-03 | What keys a per-clip edit? | **A stable clip id, never a position** | A reorder would otherwise hand every clip its neighbour's edit (§3.1) |
| 2026-09-03 | How is a freeze-frame modelled? | **A clip whose range is a point, plus a hold** | It reuses the list, the strip, the drag and the delete — one concept instead of two |
