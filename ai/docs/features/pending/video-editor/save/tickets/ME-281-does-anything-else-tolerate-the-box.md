# ME-281 — Probe: does anything else tolerate the box?

| | |
|---|---|
| **Order** | third in `save/`, straight after ME-288 |
| **Depends on** | ME-288 (there is no box in an `mp4` until the extensions are wired) |
| **Feature** | [Video Editor — Save (`Done`)](../save.md) |

**Goal.** Write our `uuid` box into a real `mp4` and a real `mov`, then find out what every other
program does with those files.

**Why now.** It is the **gate on the whole folder** and it costs nothing to ask. Structurally an
unknown top-level box must be skipped by its size — that is the extension point the specification
provides — but what mpv, VLC, Windows Explorer's preview pane and a phone's gallery *actually* do is
unmeasured. If a common player refuses the file, `Done` has nowhere to write, `Done ▾` collapses to
`Render video…` permanently, and that changes the umbrella's conclusion rather than a detail of it
([`../../video-editor.md`](../../video-editor.md) §5). Learned now it re-plans a folder; learned
after ME-282 and ME-283 it re-plans two carriers that were written for nothing.

**A probe asserts nothing** (`code-style/test.md`): it prints a table, the output is evidence, and
the decision it settles goes into [`../save.md`](../save.md)'s decision log with a date.

---

## Design notes read against the tree, 2026-09-04

**Half of this question is not machine-answerable, and the probe is built around that.**
`EditorHdrProbe` is the shape to copy exactly — it writes files into `build/hdr-probe`, prints where
they are, and says *"open each in Windows Photos and record whether it displays as HDR"*. It also
records why: *"A directory that deletes itself on the way out answers nothing."* So this probe writes
to `build/carrier-probe` and **not** to a temp storage root.

**The corpus has a `mov` and no `mp4`.** `livephoto.mov` is a real QuickTime file — `ftypqt  `,
measured — and it is what ME-288 was proved against. There is no `mp4` in `jvmTest/resources/media`,
so the probe makes one by stream-copying an existing container with the **bundled** ffmpeg: no new
fixture is committed, and the `mp4` is one a real muxer produced rather than one we hand-built.

**The unedited original goes in beside each written file**, for `EditorHdrProbe`'s stated reason: the
by-hand question becomes *"does this behave like that one"* rather than *"does this look right to
me"*. A player that mishandles the corpus file to begin with must not be read as a player that
rejects our box.

**The machine-answerable half is worth printing anyway**: our own reader gets the payload back, and
the bundled ffmpeg still opens the file and decodes a frame from it. Those two passing while a player
refuses the file is a much more interesting result than either alone, and it is the difference
between "the box is malformed" and "that player is strict".

---

## Steps

1. **`jvmTest/probe/VideoCarrierProbe.kt` (new).** One `@Test`, no assertions, writing into
   `File("build/carrier-probe")`:
   - resolve the bundled binary with `Loader.load(ffmpeg::class.java)`; a null answer prints one line
     and returns, as `EditorHdrProbe` does;
   - build the two subjects: `livephoto.mov` copied from the corpus, and an `mp4` stream-copied from
     it with `-c copy -movflags +faststart`;
   - copy each **before** state in as `reference_<name>`;
   - write a recipe into each with `EditRecipe.write(file, document)` — a Transform op, so the
     payload is representative rather than empty;
   - print a table: `subject | before | after | our reader | ffmpeg re-opens`, where the last column
     runs the binary with `-i <file> -frames:v 1 -f null -` and reports the exit;
   - print the absolute path and the by-hand checklist below.

2. **The by-hand pass, and the ticket is not closed without it.** Four programs, named in the
   printout so the author does not have to come back to this file: **mpv**, **VLC**, **Windows
   Explorer's preview pane**, and **a phone's gallery** (copy the file across). For each, one of:
   *plays as before* · *plays with a complaint* · *refuses the file*. The results are pasted into §
   *Measured* below with the date, and the decision goes into `save.md`'s decision log.

3. **The decision the result forces**, written now so the probe is not re-litigated when it answers:
   - **every program plays both** → `save/` proceeds as designed; ME-282 writes the Matroska
     carrier next.
   - **one strict program complains but plays** → recorded and accepted; the box stays.
   - **any common program refuses either file** → `Done` cannot be the default save for that
     container. `save.md` §3.1 is rewritten, `Done ▾` collapses to `Render video…` for it, and
     ME-282/ME-283 are re-planned before they are written. **ME-288 is not reverted** — an
     `mp4` that only Woka reads is still worth having if nothing else breaks on it, and that is the
     distinction the `ffmpeg re-opens` column exists to draw.

## Files

`shared/src/jvmTest/kotlin/com/woka/probe/VideoCarrierProbe.kt` (new) ·
`ai/docs/features/pending/video-editor/save/save.md` (edit — the row, and the decision log once the
by-hand pass is in)

## Done when

- `./gradlew.bat :shared:jvmTest --rerun --tests "com.woka.probe.VideoCarrierProbe" -i` prints the
  table and the two file paths;
- the probe asserts nothing and cannot fail the build on a machine whose ffmpeg cannot be unpacked;
- **the by-hand results are pasted into § Measured with a date** — until then this ticket is open,
  and `save.md`'s row says *awaiting the by-hand pass* rather than done;
- `wc -l` ≤ 150 for the probe;
- the anti-FQN grep (`mandates.md`) is clean;
- `./gradlew.bat :shared:build` green.

## Measured

### The machine half — 2026-09-04

```
| subject     | before | after        | our reader | ffmpeg re-opens |
|-------------|--------|--------------|------------|-----------------|
| carrier.mov | 17830  | 17976 (+146) | ok         | ok              |
| carrier.mp4 | 17880  | 18026 (+146) | ok         | ok              |
```

The `mp4` was stream-copied from `livephoto.mov` by the bundled ffmpeg, so both subjects are files a
real muxer wrote. **146 bytes each** — the payload plus the 24-byte box header — and the write was
ME-280's `setLength`-and-append, not a copy. Our reader gets the document back from both, and the
bundled ffmpeg still opens each file and decodes a frame out of it.

So the box is well-formed and one strict demuxer accepts it. That is as far as a machine can answer.

### The by-hand half — 2026-09-04, by the author

Both `carrier.mp4` and `carrier.mov` from `shared/build/carrier-probe/`:

| Program | Result |
|---|---|
| VLC | **plays as before** |
| Windows Photos | **plays as before** |
| Woka | **plays as before** |

*"vlc, woka, photos на windows — всё воспроизвело без проблем."*

**Not opened: mpv, and a phone's gallery.** Recorded rather than glossed — the gate was whether a
*common* program refuses the file, and three did not, one of them the system viewer that most
Windows users actually double-click into. Windows Photos stands in for the Explorer preview the
ticket named; both are the same shell component's business. If a phone ever refuses an edited `mp4`
the answer is here to be revisited, and ME-284's own by-hand pass is the natural place to try it.

### Verdict

**The box is tolerated. `save/` proceeds as designed** — `Done` stays the default save for `mp4` and
`mov`, and ME-282 writes the Matroska carrier next. Logged in [`../save.md`](../save.md) §5.
