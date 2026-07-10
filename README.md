# Iris

A minimalist, fast, and aesthetic desktop media viewer and editor built with Kotlin Multiplatform (Compose for Desktop).

## Features

- **Desktop First:** Built specifically for macOS, Windows, and Linux environments (JVM).
- **Apple HIG Inspired:** Clean, distraction-free interface focusing on the content.
- **Fast Media Preview:** Smooth zooming and panning for images, and responsive playback for videos.
- **Lightweight Editing (Planned):** Crop, rotate, simple markup (shapes, text), and lossless video trimming.
- **Echo Integration:** Designed to integrate seamlessly with the Echo clipboard manager.

## Architecture

- **UI:** Compose Multiplatform (Desktop)
- **Engine:** Skia (via Compose) for image manipulation, JavaCV (Bytedeco FFmpeg) for video rendering and processing.

---

### Running the app

- Hot reload: `./gradlew :desktopApp:hotRun --auto`
- Standard run: `./gradlew :desktopApp:run`