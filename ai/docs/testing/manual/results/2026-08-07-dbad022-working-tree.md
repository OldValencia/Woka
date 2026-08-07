# 2026-08-07 — dbad022 + working tree

Phase 8 of `viewer-verification-pass.md`: the re-run over what the fix pass changed. Run by hand by
Valik on Windows 11, single monitor.

**The tree is not a commit.** The fixes below sit uncommitted on top of `dbad022`; that is what was
run, and saying so is the point of the `<build>` field. The next commit is the tree this file
describes.

Scope: every case touching a surface this pass changed. The rest of the 38 were untouched by it and
stand on `2026-08-07-dbad022.md`.

| Case | Result | Note |
|------|--------|------|
| TC-PIP-01 | ok | the window ⇄ main handover still carries zoom and pan |
| TC-PIP-02 | ok | the defect is gone in both halves — a plain drag moves the window and only the window, and letting the button up no longer toggles playback or drops PiP back into the app |
| TC-PIP-03 | ok | |
| TC-PIP-04 | ok | |
| TC-PIP-05 | ok | |
| TC-PIP-06 | ok | new. Always on Top holds PiP above other apps, the menu and Settings agree in both directions, and the flag correctly does nothing while the window fills the screen |
| TC-PIP-07 | ok | new. The toolbar button shrinks and fills in both directions, and mixes with the edge drag in any order — the two-layer defect behind that is written up in the case |
| TC-SYS-01 | ok | `Woka.exe <path>` opens the viewer on a file and Folder View on a directory; `X` lands on the parent with the pin offer, and pinning survives a restart |
| TC-SYS-06 | ok | grid Copy reaches the clipboard and pastes into Explorer as a file; Duplicate takes the folder picker; Move unchanged. The viewer's own Copy still works from the context menu and `Ctrl+C` |

**Nothing outstanding.** No defect, no "not run".

## What this closes

- The three defects the first run found — M1/M1b (PiP drag), M2 (no CLI routing), M3 (Copy meant the
  wrong thing) — are confirmed fixed in the app, not only in the suite.
- `:shared:build` is green at **383 checks, 0 failures, 0 skipped**, with no `@Ignore` anywhere.
- `viewer-verification-pass.md` is done and moves to `implemented/`.

TC-IMG-05 stays open as evidence rather than a defect: HEIF/HEIC/AVIF have no decoder, which
`pending/rare-image-formats-and-frame-sets.md` owns. It is not this plan's to close.
