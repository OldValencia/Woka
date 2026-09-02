# ME-178 — The editor's HDR mirror

| | |
|---|---|
| **Order** | any time; nothing waits on it |
| **Depends on** | ME-119 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Put the HDR view mode in the editor's top bar, so the author can change how a picture is
read without leaving the picture.

**Why it is separate.** The backlog block for ME-119 asked for the setting *and* this mirror. ME-119
shipped the setting and left this out for one reason and not the usual one: **the mirror is a
convenience, not a gap**. ME-177 put the tone map under the decode, so the mode already reaches the
editor's canvas — flipping it in Settings changes what the editor draws today. What is missing is
only the shorter path to the switch.

ME-119 was already at ten files against the ~8 line `tickets.md` §4 sets, so this was the slice to
cut.

## Steps
1. A control in `EditorTopBarControls.kt` reading `settings.hdrViewMode` and calling
   `setHdrViewMode`. The tri-state is three values on a surface with little room, so it is a popover
   or a cycling button rather than a `SegmentedControl` — decide against `EditorTopBarSetup`'s
   existing spacing rather than in the abstract.
2. It should be **absent for a picture the mode cannot affect**. An SDR file with no HDR tags reads
   identically under `Auto` and `Force SDR`, and a control that does nothing on most files is worse
   than no control. `readStillColor` already answers this and the editor now has a reason to ask it.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorTopBarControls.kt`
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorTopBarSetup.kt`

## Done when
- `jvmTest/ui/`: the control appears for `hdr_real_pq.avif` and not for `still.jpg`;
- `jvmTest/ui/`: choosing a value writes the setting, which is the only thing it owns — the redraw is
  ME-177's and is already tested;
- confirmed by hand: the canvas changes without leaving the editor.
