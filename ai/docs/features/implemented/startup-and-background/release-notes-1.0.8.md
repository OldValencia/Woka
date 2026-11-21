# Woka 1.0.8

## Faster and lighter
- Woka is now a native Windows application, built with GraalVM Native Image — no bundled Java
  runtime. A photo is on screen about 0.2 s after a double-click (about 0.8 s in 1.0.7); a video
  starts in about 0.35 s.
- About 113 MB of memory with the app open and nothing loaded.
- No more slow first launch after installing or updating.
- Playing 4K video no longer climbs into gigabytes: frames are freed as soon as they leave the
  screen (physical memory while playing 4K: up to 17 GB before, about 0.4 GB now).
- A folder of heavy videos builds its thumbnails with far less memory (peak 3.75 GB → 1.4 GB), and
  a closed video's player is released completely.
- Opening a photo no longer loads the video engine at all.

## Video
- Dolby Vision Profile 5 plays with correct colours, accelerated on the GPU; 4K P5 plays smoothly.
- 8K HEVC at 150 Mbit/s plays at 60 fps on NVIDIA GPUs (CUDA).
- HDR video thumbnails, Dolby Vision P8.1 included, now match the player — no longer too dark.
- Embedded subtitles appear instantly, even in very large films; they used to take 30–40 seconds.

## Editor
- New: Render photo… saves the edited photo as a new file beside the original, so the edits show
  up in any other app.
- Copy now includes your edits: an edited photo pastes as the edited picture, or as a file in
  Explorer. A GIF pastes as the animated file, with edits applied to every frame.
- Crop works correctly after rotating or flipping a photo.
- Markup stays exactly where it was drawn after a crop, on screen and in the saved file.
- Changing a clip's speed takes effect immediately, without seeking.
- Fragmented MP4 files (common from screen recorders and streams) open as render-only with a
  clear explanation, instead of losing the cut on Done.

## Everything else
- Up / Backspace now work in a folder Woka was opened on.
- The installer gives Woka its own icon and version, and includes the GraalVM license notices.

## Platforms
- Windows 10 and 11. macOS and Linux installers are no longer built: they were never tested.
