# Media Pan Bounds (Photos & Videos)

> **✅ Shipped as Phase 4 of `zoom-size-control-redesign.md` (Step 8b)** — the clamp needs the same
> `sizeMode` / container / native geometry the redesign reworked, so it landed there rather than on
> its own. Both items below are implemented in `PanBounds.kt` (`panLimits` / `clampOffset` /
> `rubberBandOffset`) plus the offset animation in `MediaViewerContent.kt`, and verified live.
> §2 was taken in its stricter form: the frame clamps to the panel edges, so no black void is ever
> shown, and the optional spring is in. Beyond the original scope: left-button drag now pans too
> (the doc assumed `Ctrl + Drag` only), and an axis where the media is smaller than the panel is
> pinned to centre, which auto-recentres the frame as the zoom comes back down.

## 1. Dragging Constraints
When a user zooms into a photo or video and pans it around (e.g., using `Ctrl + Drag`), the media should not be allowed to be dragged entirely off-screen or too far past its edges.

## 2. Expected UX (Rubber-banding / Clamping)
- **Bounds Checking:** Ensure that the edges of the media frame cannot cross past the center of the screen (or ideally, strictly clamp to the screen edges so the user doesn't see a black void if they try to drag past the media's natural border).
- **Spring Effect (Optional but recommended):** If the user forcefully drags past the boundary, add visual resistance (rubber-banding). Upon releasing the mouse, the media should smoothly spring back to align with the edge of the viewport.
