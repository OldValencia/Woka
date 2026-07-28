# Video Player Enhancements

## 1. Auto Size Mode for Fullscreen Video
- Add an "Auto size" mode for fullscreen video that automatically stretches the video to fill the screen fully (either vertically or horizontally) without needing to manually adjust the zoom percentages.
- Allow fine-tuning of the zoom level (e.g., adding just a "few percent" of zoom when the video almost fits the screen, for more precise user control).
- Implement a more intuitive zoom configuration/automation.

## 2. Stream Selection (Video, Audio, Subtitles)
- Implement functionality to select the desired video source/track.
- Implement functionality to select the desired audio track.
- Implement functionality to select the desired subtitle track.
- In the "File Info" screen, parse and display all available video, audio, and subtitle streams for the media file.

## 3. Subtitle Customization & Advanced Settings
- Add settings to customize the appearance of subtitles (font, size, color, background, etc.).
- Support loading external subtitle files (e.g., matching `.srt` or `.ass` files from the same folder).
- Add synchronization controls (delay/advance) for audio and subtitles if they are out of sync.

## 4. Mega-Convenience Features (Simple & Elegant)
- **Playback Resume:** Automatically remember the last playback position for each file and resume from there.
  - *Must be highly customizable via settings:* Option to turn it off completely, or disable it for short videos (e.g., < 10 mins) while keeping it active for long movies/shows, to prevent it from becoming annoying.
- **Smart Chapters:** If the video file contains chapters (like in MKV), display them as segments on the timeline (Apple TV / YouTube style).
- **System Media Controls:** Integrate with OS media keys (Play/Pause/Next on the keyboard should control Woka globally).

## 5. Web Streaming & Network Sources (Experimental)
- **Direct Link Streaming:** Ability to paste a YouTube URL or direct video link (m3u8, mp4) and stream it natively through our player. 
- **Web Folders:** "Link" a website (like a YouTube channel or a supported movie site) as a Woka folder, parsing its content into standard media cards so it looks completely native.
