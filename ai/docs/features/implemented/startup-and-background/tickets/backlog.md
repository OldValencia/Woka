# Startup and background — backlog

**Empty since 2026-11-21.** The native image (ST-001, ST-004, ST-005) made what was left either
unnecessary or another feature's: the ids below are kept and never reused.

### ~~ST-002 — Compact idle~~ — dropped 2026-11-21
A window-less background process, kept alive so a reopen would skip the JVM's start-up. The native
image reaches `main` in ~30 ms and draws a photo in ~190 ms; there is no start-up left to hide, and
a resident process would only cost the memory the image saved. The author: "the app opens fast
enough".

### ~~ST-003 — Launcher and packaging~~ — dropped 2026-11-21
A native forwarder in front of the JVM, handing a second launch to the running instance before a
JVM loads. The executable *is* native now, and packaging moved to ST-004/ST-005 (the native MSI)
and the CI workflow. Same reason as ST-002.

### ~~ST-007 — An intermittent native crash in video render~~ — moved 2026-11-21
Not a start-up concern: it is **F5** of the video editor's 1.0.7 review, open since 2026-11-10, and
continues there (`video-editor/review-1.0.7.md`, F5; `video-editor/backlog.md`).
